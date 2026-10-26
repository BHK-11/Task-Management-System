"""Private planner API. It proposes typed steps; TASK OS Java executes approved actions."""

from __future__ import annotations

import asyncio
import os
import secrets
from threading import BoundedSemaphore
from datetime import date
from typing import Annotated, Literal

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict, Field, model_validator

from artifact_generator import generate_artifact
from document_extractor import MAX_UPLOAD_BYTES, extract_document
from model_gateway import GatewayError, catalog, plan_work as route_plan


def to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class Contract(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class PlanRequest(Contract):
    instruction: str = Field(min_length=1, max_length=4000)
    timezone: str = Field(min_length=1, max_length=64)
    today: date
    mode: Literal["AUTO", "FAST", "BALANCED", "QUALITY", "ECONOMICAL", "PRIVATE", "MANUAL"] = "AUTO"
    provider: str = Field(default="", max_length=32)
    model: str = Field(default="", max_length=128)
    allow_cloud_processing: bool = True
    allow_fallback: bool = False
    memory_context: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(default_factory=list, max_length=4)


class PlanStep(Contract):
    action_type: Literal["CREATE_TASK", "UNSUPPORTED"]
    title: str = Field(min_length=1, max_length=160)
    description: str = Field(default="", max_length=6000)
    priority: Literal["LOW", "MEDIUM", "HIGH", "URGENT"] = "MEDIUM"
    due_date: date | None = None
    dependencies: list[int] = Field(default_factory=list, max_length=12)


class PlanDraft(Contract):
    summary: str = Field(min_length=1, max_length=1200)
    detected_language: Literal["en", "hi", "hinglish", "mixed", "unknown"] = "unknown"
    clarifications: list[str] = Field(default_factory=list, max_length=8)
    steps: list[PlanStep] = Field(default_factory=list, max_length=12)

    @model_validator(mode="after")
    def dependencies_are_acyclic(self) -> PlanDraft:
        topological_order(self.steps)
        return self


class PlanResponse(Contract):
    plan: PlanDraft
    model: str
    input_tokens: int | None = None
    output_tokens: int | None = None


class WorkPlanStep(Contract):
    action_type: Literal["CREATE_TASK", "GENERATE_DOCX", "GENERATE_PDF", "GENERATE_XLSX", "GENERATE_PPTX", "DRAFT_EMAIL", "DRAFT_MESSAGE", "UNSUPPORTED"]
    title: str = Field(min_length=1, max_length=160)
    description: str = Field(default="", max_length=6000)
    priority: Literal["LOW", "MEDIUM", "HIGH", "URGENT"] = "MEDIUM"
    due_date: date | None = None
    dependencies: list[int] = Field(default_factory=list, max_length=12)
    content: str = Field(default="", max_length=12000)


class WorkPlanDraft(Contract):
    summary: str = Field(min_length=1, max_length=1200)
    detected_language: Literal["en", "hi", "hinglish", "mixed", "unknown"] = "unknown"
    clarifications: list[str] = Field(default_factory=list, max_length=8)
    steps: list[WorkPlanStep] = Field(default_factory=list, max_length=12)

    @model_validator(mode="after")
    def dependencies_are_acyclic(self) -> WorkPlanDraft:
        topological_order(self.steps)
        return self


class WorkPlanRequest(Contract):
    instruction: str = Field(min_length=1, max_length=20000)
    timezone: str = Field(min_length=1, max_length=64)
    today: date
    mode: Literal["AUTO", "FAST", "BALANCED", "QUALITY", "ECONOMICAL", "PRIVATE", "MANUAL"] = "AUTO"
    provider: str = Field(default="", max_length=32)
    model: str = Field(default="", max_length=128)
    allow_cloud_processing: bool = True
    allow_fallback: bool = False
    memory_context: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(default_factory=list, max_length=4)


class WorkPlanResponse(Contract):
    plan: WorkPlanDraft
    model: str
    input_tokens: int | None = None
    output_tokens: int | None = None


class ArtifactRequest(Contract):
    title: str = Field(min_length=1, max_length=160)
    content: str = Field(min_length=1, max_length=12000)


def topological_order(steps: list[PlanStep | WorkPlanStep]) -> list[int]:
    indegree = [0] * len(steps)
    children: list[list[int]] = [[] for _ in steps]
    for index, step in enumerate(steps):
        seen: set[int] = set()
        for dependency in step.dependencies:
            if dependency < 0 or dependency >= len(steps) or dependency == index or dependency in seen:
                raise ValueError("Invalid plan dependency")
            seen.add(dependency)
            indegree[index] += 1
            children[dependency].append(index)
    ready = [index for index, count in enumerate(indegree) if count == 0]
    order: list[int] = []
    while ready:
        current = ready.pop(0)
        order.append(current)
        for child in children[current]:
            indegree[child] -= 1
            if indegree[child] == 0:
                ready.append(child)
    if len(order) != len(steps):
        raise ValueError("Cyclic plan dependencies")
    return order


app = FastAPI(title="TASK OS AI Planner", docs_url=None, redoc_url=None, openapi_url=None)
_PLAN_SLOTS = BoundedSemaphore(4)
_ARTIFACT_SLOTS = BoundedSemaphore(2)
_EXTRACTION_SLOTS = BoundedSemaphore(2)


def authenticate(token: str | None) -> None:
    expected = os.getenv("TASKOS_AI_SERVICE_TOKEN", "")
    if len(expected) < 32:
        raise HTTPException(status_code=503, detail="AI service authentication is not configured")
    if token is None or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")


def reserve_plan_slot():
    if not _PLAN_SLOTS.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="Planner is busy; retry later")
    try:
        yield
    finally:
        _PLAN_SLOTS.release()


@app.post("/v1/plan", response_model=PlanResponse, response_model_by_alias=True)
def plan(request: PlanRequest, x_taskos_service_token: str | None = Header(default=None),
        _slot: None = Depends(reserve_plan_slot)) -> PlanResponse:
    authenticate(x_taskos_service_token)
    try:
        parsed, model, input_tokens, output_tokens = route_plan(request, PlanDraft)
    except GatewayError as exc:
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc
    try:
        topological_order(parsed.steps)
    except ValueError as exc:
        raise HTTPException(status_code=502, detail="AI provider returned an invalid plan") from exc
    return PlanResponse(
        plan=parsed,
        model=model,
        input_tokens=input_tokens,
        output_tokens=output_tokens,
    )


@app.post("/v1/work-plan", response_model=WorkPlanResponse, response_model_by_alias=True)
def work_plan(request: WorkPlanRequest, x_taskos_service_token: str | None = Header(default=None),
              _slot: None = Depends(reserve_plan_slot)) -> WorkPlanResponse:
    authenticate(x_taskos_service_token)
    try:
        parsed, model, input_tokens, output_tokens = route_plan(request, WorkPlanDraft)
    except GatewayError as exc:
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc
    try:
        topological_order(parsed.steps)
    except ValueError as exc:
        raise HTTPException(status_code=502, detail="AI provider returned an invalid plan") from exc
    return WorkPlanResponse(plan=parsed, model=model,
                            input_tokens=input_tokens, output_tokens=output_tokens)


@app.get("/v1/models")
def available_models(x_taskos_service_token: str | None = Header(default=None)) -> dict[str, object]:
    authenticate(x_taskos_service_token)
    return catalog()


def reserve_artifact_slot():
    if not _ARTIFACT_SLOTS.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="Artifact generation is busy; retry later")
    try:
        yield
    finally:
        _ARTIFACT_SLOTS.release()


@app.post("/v1/artifacts/{kind}")
def create_artifact(kind: Literal["docx", "pdf", "xlsx", "pptx", "txt"], request: ArtifactRequest,
                    x_taskos_service_token: str | None = Header(default=None),
                    _slot: None = Depends(reserve_artifact_slot)):
    authenticate(x_taskos_service_token)
    try:
        artifact = generate_artifact(kind, request.title, request.content)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return Response(content=artifact.content, media_type=artifact.media_type,
                    headers={"X-TaskOS-Artifact-Format": artifact.extension})


def reserve_extraction_slot():
    if not _EXTRACTION_SLOTS.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="Document extraction is busy; retry later")
    try:
        yield
    finally:
        _EXTRACTION_SLOTS.release()


@app.post("/v1/documents/extract/{kind}")
async def extract_uploaded_document(kind: Literal["pdf", "docx", "xlsx", "pptx", "txt", "md"], request: Request,
                                    x_taskos_service_token: str | None = Header(default=None),
                                    _slot: None = Depends(reserve_extraction_slot)) -> dict[str, object]:
    authenticate(x_taskos_service_token)
    length = request.headers.get("content-length")
    if length and (not length.isdigit() or int(length) > MAX_UPLOAD_BYTES):
        raise HTTPException(status_code=413, detail="The document is empty or exceeds the 10 MB limit.")
    body = bytearray()
    async for part in request.stream():
        body.extend(part)
        if len(body) > MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail="The document is empty or exceeds the 10 MB limit.")
    try:
        return await asyncio.to_thread(extract_document, kind, bytes(body))
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
