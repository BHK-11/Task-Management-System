# Phase 16 model gateway status

## Implemented path

Java remains the identity, preference, approval, plan-storage and task-execution authority. The authenticated private FastAPI service now owns provider discovery and model invocation. Both `/v1/plan` and `/v1/work-plan` use the same policy router. Model IDs are discovered from the provider account at request time and checked again before generation; a successful response is schema-validated before Java stores it. Plan history records the actual `provider/model` and provider-reported input/output token counts when supplied.

Provider adapters currently cover:

| Provider | Discovery | Planning request | Important limit |
| --- | --- | --- | --- |
| OpenAI | Authenticated `/v1/models` | Responses API structured parse | Capability is only verified after TASK OS receives and validates a plan. |
| Anthropic | Authenticated `/v1/models` | Messages API; Pydantic validates returned JSON | Uses prompted JSON plus application validation, not a claimed native schema guarantee. |
| Gemini | Authenticated `models.list`, filtered to `generateContent` models | `generateContent` JSON mode; Pydantic validates the response | Audio, vision, tools and other modality support are not inferred. |
| xAI | Authenticated `/v1/language-models` | Responses API structured parse | Only official global and US API hosts are accepted. |
| Ollama | Local `/api/tags` | Local `/api/chat` with JSON Schema | Only `localhost`, `127.0.0.1`, or `::1` over HTTP is accepted; no model is downloaded by TASK OS. |

The provider model list is not treated as proof of every capability. The gateway only marks `TEXT_GENERATION` and `STRUCTURED_OUTPUT` after a successful, validated TASK OS plan. That verification currently lives in the AI service process and is cleared on restart.

## User preferences and routing

`/settings/ai` stores per-user mode, selected manual model, cloud-processing permission, and fallback consent in PostgreSQL. Credentials remain in the AI service environment and are never accepted through the web UI. A manual provider/model is checked against the provider's current account model list for each planning request.

`PRIVATE` always selects configured local Ollama and cannot fall back to cloud. Turning off cloud processing also restricts every mode to local Ollama. Fallback is off by default; when explicitly enabled, the gateway may try one server-configured alternate only for transient provider/network failures. It does not fall back after permission/privacy errors, an unlisted model, or schema-validation failure.

The AI settings page shows this UTC month's saved plan count and reported input/output tokens, scoped to the signed-in owner. Cost is explicitly shown as unknown because provider pricing rates and a budget ledger are not configured. Provider and policy errors use explicit HTTP status codes rather than parsing error-message text.

Automatic modes use `TASKOS_AI_<MODE>_TARGET=provider/model-id`, then `TASKOS_AI_DEFAULT_TARGET`, then `TASKOS_AI_DEFAULT_PROVIDER` plus that provider's configured model variable. With no mode-specific targets, modes use the same default. TASK OS has not run comparative evaluations, so it makes no measured speed, cost, or quality ranking claim. `TASKOS_AI_FALLBACK_TARGETS` is a comma-separated ordered list of explicit alternates; a user must still enable fallback.

Configure provider keys only in the private service process: `OPENAI_API_KEY` / `OPENAI_MODEL`, `ANTHROPIC_API_KEY` / `ANTHROPIC_MODEL`, `GEMINI_API_KEY` / `GEMINI_MODEL`, `XAI_API_KEY` / `XAI_MODEL`, and/or `OLLAMA_MODEL` with `OLLAMA_BASE_URL` left on localhost. See `.env.example` for mode target names. Existing `OPENAI_BASE_URL` remains an operator-only OpenAI-compatible endpoint; it must use HTTPS, or HTTP on loopback.

## Not implemented in this slice

There is no organization-level AI policy, encrypted user API-key vault, usage ledger separate from AI plan history, cost registry, atomic spend budget, per-provider rate-limit accounting, streaming event API, provider health circuit breaker, evaluation suite, or Hermes Agent runtime adapter. Existing Java plan limits and private-service concurrency caps remain the only request-volume controls. No live provider credentials were available in the implementation environment, so provider protocols were exercised with mocks and have not been verified against paid accounts.

Do not treat this release as production spend governance. Add database-backed reservations and operator/workspace policy before enabling high-volume or team-wide model access.
