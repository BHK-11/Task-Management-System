# AI Work Manager setup and current limits

TASK OS Java remains the identity, approval, persistence and execution authority. The private FastAPI service proposes a typed plan and generates bounded documents; it has no TASK OS database credentials and cannot create tasks. Java validates the plan and stores it in owner-scoped AI plan/step tables. Approval queues work; the worker commits one action at a time and creates tasks only through the existing `TaskService`.

The work manager accepts up to 20,000 instruction characters and 12 jobs. Its registered actions are `CREATE_TASK`, `GENERATE_DOCX`, `GENERATE_PDF`, `GENERATE_XLSX`, `GENERATE_PPTX`, `DRAFT_EMAIL` and `DRAFT_MESSAGE`. Draft actions produce downloadable `.txt` files. They do not send messages. Unsupported external actions remain visibly unsupported. The older `/v1/plan` task-only route is retained for compatibility.

Generated documents are produced by deterministic Python libraries and reopened for format validation before Java checks their signature/structure and saves them. Files are stored outside public web folders in `${user.home}/.taskos/artifacts` by default; set `TASKOS_ARTIFACT_DIRECTORY` to choose a private persistent volume. Each download requires an authenticated owner match and a SHA-256 integrity check. Files are capped at 10 MiB. The generator uses an installed Devanagari font for Hindi PDFs when available; on other systems set `TASKOS_PDF_FONT_PATH` to a licensed, readable TTF font path or the Hindi PDF job will fail clearly.

The built-in job lifecycle is `PENDING_APPROVAL → QUEUED ↔ RUNNING → COMPLETED | PARTIALLY_COMPLETED | FAILED`, or `REJECTED` before execution. A two-minute lease recovers a worker-lost active step, and unfinished plans return to the queue after each committed step. There is no progress stream, user cancellation, retry button, multi-worker job parallelism, or plan deletion/retention setting yet. The UI polls queued/running plans every 2.5 seconds. The daily planning limit is 10 per account in a rolling 24-hour period; planner concurrency is capped at four, generator concurrency at two, and the work-plan provider timeout is 45 seconds with no automatic retry.

## Voice input

The composer can use the browser's `SpeechRecognition` API when supported. The user explicitly starts and stops it, selects English/Hinglish (`en-IN`) or Hindi (`hi-IN`), and can edit the transcript before submitting. The app does not upload or retain microphone audio. A browser's speech service may process audio outside TASK OS; typed input remains available when speech recognition is unsupported or denied.

## Configure locally

Use a random private service token of at least 32 characters. Set the same token in the Java and Python processes. Keep provider credentials and selected model IDs only in the private AI service environment. OpenAI is the default target when `OPENAI_MODEL` is set; Anthropic, Gemini, xAI and localhost Ollama can be configured as alternate targets. The provider receives the submitted text, timezone and local date; task title/description is included only when the user keeps the AI Assist prefill in the instruction. See [the Phase 16 gateway status](model-gateway.md) for routing and security limits.

```powershell
cd services/ai-api
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:TASKOS_AI_SERVICE_TOKEN = "<private random token shared with TASK OS>"
$env:OPENAI_API_KEY = "<provider key>"
$env:OPENAI_MODEL = "<model available to your account>"
uvicorn main:app --host 127.0.0.1 --port 8090
```

Additional providers use `ANTHROPIC_API_KEY` / `ANTHROPIC_MODEL`, `GEMINI_API_KEY` / `GEMINI_MODEL`, `XAI_API_KEY` / `XAI_MODEL`, or `OLLAMA_MODEL`. User selection and cloud/fallback permission are saved from `/settings/ai`. Provider API keys are not stored in TASK OS tables. Private mode uses only a local Ollama endpoint; TASK OS does not download models.

In another PowerShell window, set the same service token and loopback URL, then start TASK OS from the repository root:

```powershell
$env:TASKOS_AI_SERVICE_TOKEN = "<same private random token>"
$env:TASKOS_AI_SERVICE_URL = "http://127.0.0.1:8090"
.\mvnw.cmd spring-boot:run
```

The Java client accepts HTTPS, or HTTP only for loopback. Use HTTPS when the private planner is remote. `OPENAI_BASE_URL` is optional for an OpenAI-compatible provider. `TASKOS_AI_EXECUTION_ENABLED=false` disables the background worker. In production, mount `TASKOS_ARTIFACT_DIRECTORY` on a persistent private volume and back it up with the database metadata.

## Data and retention

The original instruction, typed plan, provider model, token counts when returned, approval decision, step statuses, task IDs and artifact metadata are stored in `task_os.ai_plans`, `task_os.ai_plan_steps` and `task_os.ai_artifacts`. Artifact bytes live on the configured private filesystem volume. There is no per-plan deletion or separate AI retention policy. Do not include secrets or sensitive data in an instruction.

Run Python checks from `services/ai-api`:

```powershell
python -m unittest discover -s . -p 'test_*.py'
```

Run the Java suite from the repository root with `.\mvnw.cmd clean verify`. Neither suite makes a live provider call. Java integration tests mock the private generator while Python tests create and reopen actual DOCX, PDF, XLSX and PPTX outputs.

## Hermes and agent integration readiness

Hermes is not enabled in TASK OS. The current built-in plan queue is not a general external-agent runtime: it lacks cancellation, user-triggered retries, streaming events, per-run sandboxing and a scoped external tool proxy. The host has no Hermes CLI, Docker or Podman. Do not connect an unrestricted Hermes API server directly to TASK OS; its full agent toolset needs a disposable restricted runtime and a Java-owned per-run authorization proxy first.

TASK OS now has a separate, optional Playwright worker for one-shot, read-only observation of operator-approved public HTTPS origins. Browser Use remains installed only in the ignored repository-root `.venv` for evaluation; it is not connected to TASK OS or the AI API service. Do not expose the worker publicly: it has no container/VM or network-level egress sandbox. AgentMemory development-session data must stay separate from TASK OS user memory. OpenViking remains deferred pending AGPL and tenant/ACL review. Scientific Agent Skills are development-time skill files, not production services, and are outside this product scope. Diagram Design is used only for development documentation. See [the agent integration audit](../architecture/ai-agent-integration.md) for the project and trust-boundary review.
