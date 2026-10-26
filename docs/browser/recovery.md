# Recovery

Observation runs synchronously with a 15-second browser navigation timeout and one-worker concurrency. There is no durable queue, checkpoint, browser-session lease, cancellation endpoint, automatic retry, or worker restart recovery. A connection failure is recorded as a failed observation; no external write is retried.
