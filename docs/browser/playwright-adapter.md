# Playwright adapter

The standalone `services/browser-worker` pins Playwright 1.64.1 and Chromium. It opens a fresh headless context, blocks service workers, routes and validates every HTTP request, allows only GET requests to the requested origin, closes WebSockets, disables downloads, and extracts the page title, headings, and bounded visible text. No arbitrary selectors, clicks, typing, form interaction, CDP endpoint, persistent profile, or screenshot is exposed.

Official API references: [Playwright Python release](https://github.com/microsoft/playwright-python/releases), [browser-context request routing](https://playwright.dev/python/docs/api/class-browsercontext), and [WebSocketRoute close](https://playwright.dev/python/docs/api/class-websocketroute).
