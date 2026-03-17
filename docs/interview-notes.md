# nano-job Interview Notes

## One-Sentence Version

`nano-job` is a small backend job platform built with Java 21 and Spring Boot that supports delayed execution, async workers, retry policies, deduplication, lease recovery, typed payload contracts, and execution metrics.

## 3-Minute Version

I wanted a project that was small in scope but still showed real backend design, so I built `nano-job` as a generic task execution service instead of another CRUD system.

The core flow is:

1. a client submits a job
2. the service validates the payload contract and applies deduplication
3. the scheduler finds due jobs
4. the dispatcher claims jobs with a conditional update
5. a dedicated executor runs the job handler asynchronously
6. the lifecycle service decides whether the result is success, retry, or final failure
7. each execution attempt is logged and metrics are emitted

The important part is that I separated platform concerns:

- payload validation is annotation-driven
- retry behavior is strategy-based by job type
- deduplication is also policy-based
- state changes are centralized in the lifecycle service
- execution is isolated from request threads

That lets the system stay small while still looking like a real task platform.

## 5 Key Technical Points

### 1. Claim Idempotency

Problem:

The scheduler can see the same due job multiple times, or multiple threads can try to pick it up.

Solution:

I use a conditional database update to claim execution rights. A job can only transition from `PENDING` or `RETRY_WAIT` to `RUNNING` once.

Why it matters:

This prevents duplicate consumption without introducing distributed locking complexity.

### 2. Lease Recovery

Problem:

If a worker crashes after claiming a job, the job could stay stuck in `RUNNING`.

Solution:

`RUNNING` includes lease ownership and an expiry time. The dispatcher scans expired leases and routes them back through the normal failure path.

Why it matters:

I do not maintain a second recovery model. Timeout recovery reuses the same retry and final-failure semantics as any other execution error.

### 3. Retry Policy by Job Type

Problem:

Different kinds of jobs fail for different reasons, so retry rules should not live inside the generic lifecycle engine.

Solution:

I moved retry decisions into per-type strategies.

Examples:

- `HTTP` retries server-side and network failures
- `HTTP` does not retry invalid URL or bad request cases
- `NOOP` uses simpler linear retry

Why it matters:

The state machine stays generic while recovery logic remains business-aware.

### 4. Deduplication Semantics

Problem:

Submission idempotency is more than “same key means same task”.

Solution:

I turned dedup into a policy with three decisions:

- create new
- return existing
- reject

I also added:

- drift detection: same `dedupKey` but different payload or type gets rejected
- windowed deduplication: recently finished jobs can still be reused for a configurable duration

Why it matters:

This handles both duplicate clicks and semantic conflicts instead of just returning old jobs blindly.

### 5. Payload Contracts and Observability

Problem:

If payload parsing, field validation, and type registration are all hand-written, the system drifts and becomes harder to extend.

Solution:

I use Java records plus validation annotations to define payload contracts, and the platform auto-discovers job types from those payload classes.

I also added Micrometer counters and gauges for:

- claimed jobs
- started jobs
- succeeded jobs
- failed jobs
- retries
- final failures
- lease recoveries
- current job counts by status

Why it matters:

The platform is both type-safe and observable.

## What I Would Emphasize in an Interview

- I deliberately chose a small backend infrastructure project instead of a CRUD business app.
- The complexity is in lifecycle control, not in UI or business tables.
- I focused on correctness under retries, duplicate submissions, worker failure, and external dependency errors.
- I kept the design extensible without introducing premature distributed complexity.

## What I Would Improve Next

- expose actuator metrics more explicitly
- add dashboard-ready tags such as retry reason or HTTP outcome class
- add request tracing or correlation IDs
- introduce dead-letter handling for repeated failures
- add persistence-backed scheduler pagination for larger queues
