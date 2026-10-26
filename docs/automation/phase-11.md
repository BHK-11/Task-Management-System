# Phase 11: Automation

TASK OS runs typed, versioned automation rules in Java. Rules are scoped to a user or workspace, validated before publishing, evaluated by a durable outbox worker, and recorded as executions with ordered step history. Rule definitions cannot contain executable Java, SQL, shell commands, or arbitrary network calls.

## Event and execution flow

Task and workspace task services write supported events in the same transaction as the business change. The worker claims ready events with a lease, matches enabled rules and their current versions, evaluates typed AND/OR conditions, and creates one execution per rule version and event. A unique database key prevents duplicate execution records. Actions run through the existing task, workspace-task, and reminder services. Each execution and step stores status, timestamps, correlation data, and safe result text.

Retries use bounded exponential backoff. Expired worker leases can be reclaimed; permanent failures and exhausted retries become dead letters. A successful step is not repeated during a retry. Rules that originated the triggering event are skipped, and chain depth is capped at five.

Workspace rules are permission-checked at configuration and action time. Approval gates are available for workspace rules and require a workspace owner or administrator; approvals expire after 24 hours. Scheduled rules are persisted and evaluated in the configured timezone, with a stored missed-run policy.

## Supported registry

Triggers: task created, status changed, completed, reopened, priority changed, due date changed, tag added, assigned, and scheduled.

Actions: create task, update the source task's status, set the source task's priority, create a personal in-app reminder, and assign a workspace task to the member who triggered the event. Assignment reuses the workspace authorization, membership, history, and notification path. Scheduled rules can create tasks.

The Automation Center provides a no-code rule form, enable/disable/archive controls, dry-run previews, execution history, step details, retries, and an approval inbox.

## Current limits

Project and Kanban events/actions, Scrum events/actions, and a workflow template library are not registered yet. Assignment must target the triggering workspace member; there is no arbitrary member picker in the builder. Reminder actions are personal only; there is no external email or webhook delivery. Scheduled rules currently support task creation. Metrics are limited to persisted dashboard counts; no throughput or latency benchmark is available.

The test profile uses H2. PostgreSQL-specific integration, browser automation, load testing, and accessibility audits have not been run as part of this implementation.
