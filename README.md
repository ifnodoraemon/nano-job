# nano-job

`nano-job` is a Java 21 Spring Boot backend for delayed jobs, async execution, retry control, deduplication, and execution tracking.

Chinese documentation:

- [README.zh-CN.md](./README.zh-CN.md)
- [docs/interview-notes.zh-CN.md](./docs/interview-notes.zh-CN.md)

The project is intentionally backend-only and small in scope, but it focuses on the parts that actually demonstrate Java backend depth:

- state transitions
- async execution boundaries
- retry policy design
- deduplication semantics
- lease recovery
- payload contracts
- observability

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- SQLite
- Micrometer
- JUnit 5

## What It Does

`nano-job` accepts a job, stores it, schedules it, executes it asynchronously, retries when appropriate, and records execution history.

Current job types:

- `NOOP`
- `HTTP`

Current capabilities:

- create, query, cancel, and list jobs
- delayed execution
- async worker execution with a dedicated thread pool
- execution lease and timeout recovery
- retry policies by job type
- submission deduplication with drift detection
- windowed deduplication for recently finished jobs
- per-attempt execution logs
- payload contracts discovered from annotations
- Micrometer counters and status gauges

## Architecture

Main flow:

1. `JobController` receives a request
2. `JobService` validates payload and applies deduplication
3. `JobScheduler` triggers `JobDispatchService`
4. `JobDispatchService` finds due jobs and tries to claim them
5. `JobExecutionService` hands claimed jobs to a dedicated executor
6. `JobHandler` runs job-specific logic
7. `JobLifecycleService` transitions the job to `SUCCESS`, `RETRY_WAIT`, or `FAILED`
8. `JobExecutionLogService` records each attempt
9. `JobMetricsService` emits execution and status metrics

Core packages:

- `controller`: API endpoints
- `service`: orchestration and state flow
- `handler`: job execution logic
- `retry`: retry policies by job type
- `dedup`: deduplication policies and decisions
- `jobtype`: payload contract discovery and job type metadata
- `metrics`: Micrometer integration
- `support/payload`: shared payload binding and validation

## Job Lifecycle

Job status:

- `PENDING`
- `RUNNING`
- `RETRY_WAIT`
- `SUCCESS`
- `FAILED`
- `CANCELED`

Lifecycle rules:

- only `PENDING` and `RETRY_WAIT` can be claimed
- only `RUNNING` can become `SUCCESS` or `FAILED`
- `RUNNING` uses a lease to avoid hanging forever
- expired leases are recovered into the normal failure path

This means timeout recovery reuses the same retry logic as ordinary execution failure instead of introducing a second recovery model.

## Payload Contracts

Payload contracts are record-based and annotation-driven.

Examples:

- [`HttpJobPayload`](./src/main/java/com/ifnodoraemon/nanojob/domain/payload/HttpJobPayload.java)
- [`NoopJobPayload`](./src/main/java/com/ifnodoraemon/nanojob/domain/payload/NoopJobPayload.java)

Payload classes declare:

- the `JobType`
- a human-readable description
- validation constraints such as `@NotBlank` and `@PositiveOrZero`

`JobTypeDefinitionRegistry` discovers these contracts automatically and builds the job-type directory used by the API.

## Deduplication

Submission deduplication is policy-based, not hardcoded in the service layer.

Current dedup behavior:

- if an active job with the same `dedupKey` exists, return it or reject it depending on policy mode
- if the existing job has the same key but different `type` or `payload`, reject it as drift
- if no active job exists, a recent finished job can still be reused inside a configurable time window

Config:

```yaml
nano-job:
  dedup:
    mode: RETURN_EXISTING
    detect-drift: true
    window: 0s
```

## Retry Behavior

Retries are policy-driven by job type.

- `HTTP`
  - invalid arguments are not retried
  - `5xx`, `408`, `429`, I/O failures, and timeouts are retried
  - retry backoff is more aggressive
- `NOOP`
  - simple linear retry

This keeps the lifecycle engine generic while pushing recovery rules into dedicated strategy objects.

## HTTP Job Execution

`HTTP` jobs use JDK `HttpClient`.

Supported payload fields:

- `url`
- `method`
- `headers`
- `body`
- `timeoutMillis`

Current error classification:

- `2xx`: success
- `4xx`: non-retryable
- `408`, `429`, `5xx`: retryable
- network or timeout failures: retryable
- invalid URL or unsupported method: non-retryable

## Metrics

Micrometer metrics are emitted for the main execution events.

Counters:

- `nano.job.dispatch.claimed`
- `nano.job.execution.started`
- `nano.job.execution.succeeded`
- `nano.job.execution.failed`
- `nano.job.execution.retry.scheduled`
- `nano.job.execution.final_failed`
- `nano.job.execution.lease_recovered`

Gauge:

- `nano.job.status.count{status=...}`

These metrics are recorded centrally instead of being scattered through handlers.

## API

Main endpoints:

- `POST /api/jobs`
- `GET /api/jobs/{jobKey}`
- `POST /api/jobs/{jobKey}/cancel`
- `GET /api/jobs/{jobKey}/logs`
- `GET /api/jobs`
- `GET /api/job-types`

Example request:

```json
{
  "type": "HTTP",
  "payload": {
    "url": "http://127.0.0.1:8080/demo",
    "method": "POST",
    "body": "{\"event\":\"demo\"}",
    "timeoutMillis": 1000
  },
  "executeAt": "2026-03-17T12:00:00",
  "maxRetry": 2,
  "dedupKey": "demo-http-1"
}
```

## Run

```bash
./mvnw spring-boot:run
```

## Test

```bash
./mvnw -Dmaven.repo.local=.m2 test
```

Current test coverage includes:

- duplicate claim protection
- timeout recovery
- retry policy behavior
- dedup drift rejection
- dedup window behavior
- live HTTP execution
- metrics emission

## Why This Project Matters

This project is deliberately small, but it is not CRUD-focused.

It demonstrates:

- state-machine thinking
- conditional updates for idempotent claim
- decoupled execution and scheduling
- typed payload contracts
- strategy-based retry and deduplication
- failure classification for external dependencies
- operational visibility through metrics
