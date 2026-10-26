"""Private, one-shot, read-only Playwright observation worker."""

from __future__ import annotations

import asyncio
import ipaddress
import os
import secrets
import socket
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from threading import BoundedSemaphore
from typing import Annotated
from urllib.parse import urlsplit, urlunsplit

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from playwright.async_api import async_playwright


def to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class Contract(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class ObserveRequest(Contract):
    url: str = Field(min_length=1, max_length=2048)


class Observation(Contract):
    origin: str
    source_url: str
    title: str
    headings: list[str]
    visible_text: str
    observed_at: datetime
    blocked_request_count: int


class BrowserPolicyError(ValueError):
    pass


def _origin(value: str, allow_query: bool = False) -> str:
    try:
        parsed = urlsplit(value.strip())
        host = parsed.hostname.rstrip(".").encode("idna").decode("ascii").lower() if parsed.hostname else ""
    except UnicodeError as exc:
        raise BrowserPolicyError("The browser hostname is invalid.") from exc
    except ValueError as exc:
        raise BrowserPolicyError("The browser URL is invalid.") from exc
    if parsed.scheme.lower() != "https" or not host or parsed.username or parsed.password:
        raise BrowserPolicyError("Only approved HTTPS origins are supported.")
    try:
        port = parsed.port
    except ValueError as exc:
        raise BrowserPolicyError("The browser URL is invalid.") from exc
    if port not in (None, 443):
        raise BrowserPolicyError("Non-standard browser ports are blocked.")
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        address = None
    if address is not None:
        if not address.is_global:
            raise BrowserPolicyError("Private and non-public network addresses are blocked.")
        host = f"[{address.compressed}]" if address.version == 6 else address.compressed
    if not allow_query and (parsed.query or parsed.fragment):
        # URL query strings and fragments can contain credentials and are unnecessary for this first read-only milestone.
        raise BrowserPolicyError("URLs with query parameters or fragments are not supported.")
    return f"https://{host}"


def configured_origins(raw: str | None = None) -> frozenset[str]:
    values = (raw if raw is not None else os.getenv("TASKOS_BROWSER_ALLOWED_ORIGINS", "")).split(",")
    result: set[str] = set()
    for value in values:
        candidate = value.strip()
        if not candidate:
            continue
        parsed = urlsplit(candidate)
        if parsed.path not in ("", "/") or parsed.query or parsed.fragment:
            raise BrowserPolicyError("Allowed origins must contain only an HTTPS origin.")
        result.add(_origin(candidate))
    return frozenset(result)


def validate_target(url: str, allowed: frozenset[str], resolver=socket.getaddrinfo,
                    *, allow_query: bool = False) -> str:
    if len(url) > 2048:
        raise BrowserPolicyError("The browser URL is too long.")
    origin = _origin(url, allow_query=allow_query)
    if origin not in allowed:
        raise BrowserPolicyError("This origin is not approved by the browser worker.")
    host = urlsplit(url).hostname.rstrip(".")
    try:
        addresses = resolver(host, 443, type=socket.SOCK_STREAM)
    except OSError as exc:
        raise BrowserPolicyError("The approved website could not be resolved.") from exc
    if not addresses:
        raise BrowserPolicyError("The approved website could not be resolved.")
    for address in addresses:
        ip = ipaddress.ip_address(address[4][0].split("%", 1)[0])
        if not ip.is_global:
            raise BrowserPolicyError("The website resolves to a private or non-public address.")
    return origin


def validate_read_request(method: str, url: str, allowed: frozenset[str], resolver=socket.getaddrinfo) -> str:
    if method.upper() != "GET":
        raise BrowserPolicyError("Only read-only GET requests are permitted.")
    return validate_target(url, allowed, resolver, allow_query=True)


def _safe_source_url(value: str) -> str:
    parsed = urlsplit(value)
    return urlunsplit((parsed.scheme, parsed.netloc, parsed.path, "", ""))


async def capture_observation(page, origin: str, source_url: str, blocked_count: int = 0) -> Observation:
    title = (await page.title())[:240]
    visible_text = (await page.locator("body").inner_text(timeout=4000))[:12000]
    headings = [item.strip()[:240] for item in await page.locator("h1, h2, h3").all_inner_texts()
                if item.strip()][:12]
    return Observation(origin=origin, source_url=_safe_source_url(source_url), title=title,
                       headings=headings, visible_text=visible_text,
                       observed_at=datetime.now(timezone.utc), blocked_request_count=blocked_count)


def authenticate(token: str | None) -> None:
    expected = os.getenv("TASKOS_BROWSER_SERVICE_TOKEN", "")
    if len(expected) < 32:
        raise HTTPException(status_code=503, detail="Browser worker authentication is not configured")
    if token is None or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")


_SLOTS = BoundedSemaphore(1)


def reserve_slot():
    if not _SLOTS.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="The browser worker is busy; retry later.")
    try:
        yield
    finally:
        _SLOTS.release()


app = FastAPI(title="TASK OS Browser Worker", docs_url=None, redoc_url=None, openapi_url=None)


@app.post("/v1/observe", response_model=Observation, response_model_by_alias=True)
async def observe(request: ObserveRequest, x_taskos_service_token: str | None = Header(default=None),
                  _slot: None = Depends(reserve_slot)) -> Observation:
    authenticate(x_taskos_service_token)
    try:
        allowed = configured_origins()
        if not allowed:
            raise HTTPException(status_code=503, detail="No public website origins are approved.")
        origin = await asyncio.to_thread(validate_target, request.url, allowed)
    except BrowserPolicyError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    blocked = 0
    request_count = 0
    session_origins = frozenset({origin})
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        try:
            context = await browser.new_context(service_workers="block", accept_downloads=False)

            async def guard(route):
                nonlocal blocked, request_count
                request_count += 1
                try:
                    if request_count > 75:
                        raise BrowserPolicyError("The page exceeded its request budget.")
                    await asyncio.to_thread(validate_read_request, route.request.method,
                                            route.request.url, session_origins)
                    await route.continue_()
                except (BrowserPolicyError, OSError, ValueError):
                    blocked += 1
                    await route.abort("blockedbyclient")

            async def deny_websocket(websocket):
                await websocket.close(code=1008, reason="WebSockets are disabled in read-only mode")

            await context.route("**/*", guard)
            await context.route_web_socket("**/*", deny_websocket)
            page = await context.new_page()
            page.set_default_navigation_timeout(15000)
            page.set_default_timeout(5000)
            try:
                await page.goto(request.url, wait_until="domcontentloaded")
                final_origin = await asyncio.to_thread(validate_target, page.url, session_origins, allow_query=True)
                return await capture_observation(page, final_origin, page.url, blocked)
            except HTTPException:
                raise
            except Exception as exc:
                raise HTTPException(status_code=502,
                                    detail="The page could not be safely observed. No browser action was performed.") from exc
            finally:
                await context.close()
        finally:
            await browser.close()
