import asyncio
import os
import socket
import unittest
from unittest.mock import patch

from fastapi import HTTPException
from playwright.async_api import async_playwright

from main import (BrowserPolicyError, ObserveRequest, capture_observation, configured_origins, observe,
                  validate_read_request, validate_target)


PUBLIC_DNS = lambda host, port, type: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", port))]


class BrowserPolicyTest(unittest.TestCase):
    def test_allows_only_configured_public_https_origin(self):
        allowed = configured_origins("https://example.test")
        self.assertEqual(validate_target("https://example.test/guide", allowed, PUBLIC_DNS), "https://example.test")

    def test_rejects_private_addresses_and_nonstandard_ports(self):
        allowed = configured_origins("https://example.test")
        private_dns = lambda host, port, type: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("10.0.0.4", port))]
        for url, resolver in (("https://127.0.0.1/", PUBLIC_DNS),
                              ("https://example.test:8443/", PUBLIC_DNS),
                              ("https://example.test/", private_dns)):
            with self.subTest(url=url), self.assertRaises(BrowserPolicyError):
                validate_target(url, allowed, resolver)

    def test_rejects_unapproved_origins_credentials_and_url_tokens(self):
        allowed = configured_origins("https://example.test")
        for url in ("https://other.test/", "https://user:password@example.test/",
                    "https://example.test/?token=secret", "http://example.test/"):
            with self.subTest(url=url), self.assertRaises(BrowserPolicyError):
                validate_target(url, allowed, PUBLIC_DNS)

    def test_allowed_origin_configuration_must_be_origin_only(self):
        with self.assertRaises(BrowserPolicyError):
            configured_origins("https://example.test/path")

    def test_page_requests_cannot_mutate_websites(self):
        with self.assertRaisesRegex(BrowserPolicyError, "Only read-only GET"):
            validate_read_request("POST", "https://example.test/form", configured_origins("https://example.test"),
                                  PUBLIC_DNS)


class BrowserEndpointTest(unittest.TestCase):
    def test_requires_service_authentication_before_browsing(self):
        with patch.dict(os.environ, {"TASKOS_BROWSER_SERVICE_TOKEN": "t" * 32,
                                    "TASKOS_BROWSER_ALLOWED_ORIGINS": "https://example.test"}):
            with self.assertRaises(HTTPException) as error:
                asyncio.run(observe(ObserveRequest(url="https://example.test/"), None))
        self.assertEqual(error.exception.status_code, 401)

    def test_unapproved_origin_is_rejected_before_playwright_launch(self):
        with patch.dict(os.environ, {"TASKOS_BROWSER_SERVICE_TOKEN": "t" * 32,
                                    "TASKOS_BROWSER_ALLOWED_ORIGINS": "https://example.test"}):
            with self.assertRaises(HTTPException) as error:
                asyncio.run(observe(ObserveRequest(url="https://other.test/"), "t" * 32))
        self.assertEqual(error.exception.status_code, 403)


class PlaywrightObservationIntegrationTest(unittest.TestCase):
    def test_real_chromium_observation_reads_visible_text_without_form_values(self):
        async def run():
            async with async_playwright() as playwright:
                browser = await playwright.chromium.launch(headless=True)
                try:
                    page = await browser.new_page()
                    await page.set_content("""<!doctype html><title>Course Portal</title>
                        <h1>Assignment details</h1><h2>Due date</h2>
                        <p>Upload your Java notes by Friday.</p>
                        <input aria-label="Password" type="password" value="hidden-secret">
                        <script>document.body.dataset.test='not visible';</script>""")
                    observed = await capture_observation(page, "https://example.test",
                                                         "https://example.test/assignment?secret=omit")
                    self.assertEqual(observed.title, "Course Portal")
                    self.assertEqual(observed.headings, ["Assignment details", "Due date"])
                    self.assertIn("Upload your Java notes by Friday.", observed.visible_text)
                    self.assertNotIn("hidden-secret", observed.visible_text)
                    self.assertEqual(observed.source_url, "https://example.test/assignment")
                finally:
                    await browser.close()
        asyncio.run(run())


if __name__ == "__main__":
    unittest.main()
