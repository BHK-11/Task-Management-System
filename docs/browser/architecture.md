# Phase 18 browser foundation

The authenticated `/browser` page calls the Java `BrowserWorkerClient`; Java owns the signed-in user, validates the URL, and records only owner-scoped observation metadata in Flyway V29. A separate FastAPI worker launches one ephemeral Playwright Chromium context per request and returns the title, headings, visible text excerpt, final origin, timestamp, and blocked-request count. Page text is rendered as untrusted text and is not sent to an AI model.

The implemented strategy is one-shot, deterministic, read-only observation. There is no persistent browser session, task/job queue, login state, computer-use adapter, or external agent controller.

Run the private worker from this directory after installing [requirements.txt](../../services/browser-worker/requirements.txt):

```powershell
$env:TASKOS_BROWSER_SERVICE_TOKEN = "<private random token of at least 32 characters>"
$env:TASKOS_BROWSER_ALLOWED_ORIGINS = "https://docs.example.org"
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8091
```

Set the same token and `TASKOS_BROWSER_SERVICE_URL=http://127.0.0.1:8091` in the Java process. Configure only exact public HTTPS origins in the worker. Do not bind this service to a public interface.
