# nano-job

`nano-job` is a Java 21 Spring Boot backend for delayed job scheduling, async execution, retry handling, and execution tracking.

## Current scope

- Job creation, lookup, and cancellation APIs
- Scheduling-ready domain model
- Execution log model
- Service and handler extension points
- SQLite for low-friction local persistence

## Planned next steps

1. Implement persistence-backed job creation and query.
2. Add scheduler polling and worker execution.
3. Add retry flow and execution log writes.
4. Add tests around SQLite-backed repositories and service flow.
