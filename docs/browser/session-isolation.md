# Session isolation

The worker creates a new non-persistent Playwright `BrowserContext` for each one-shot observation, then closes it. It does not reuse cookies, profiles, downloads, cache, or local storage. A browser context is not an operating-system boundary: the worker currently runs as the service process account, so container/VM isolation and resource limits are still required before production exposure.
