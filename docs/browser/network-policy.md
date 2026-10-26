# Network policy

The worker accepts only exact origins from `TASKOS_BROWSER_ALLOWED_ORIGINS`. User URLs must be HTTPS, use the default port, omit credentials, query parameters, and fragments, and resolve only to globally routable addresses. During a request, every routed GET stays on that one origin; non-GET requests, cross-origin requests, private/non-public addresses, WebSockets, service workers, and over-budget requests are blocked.

This in-process policy cannot eliminate DNS rebinding or guarantee that Chromium uses only the address checked by Python. Production must add network egress controls; no localhost/private-network penetration test was run.
