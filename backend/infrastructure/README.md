# Infrastructure Module

## Purpose

`infrastructure` contains implementations of `out ports` for database, HTTP, AI, and other external systems.

## Rules

- implement only `out port` adapters here
- catch technical exceptions and convert them to `Either.Left(InfrastructureError(cause))`
- use `fromTrusted(...)` only when reconstructing domain models from trusted persisted data
- perform blocking IO inside `withContext(Dispatchers.IO)`
- do not expose SQL, HTTP, or SDK-specific models outside the adapter boundary

## Dependencies

- `project(:backend:domain)`
- `arrow-core`
- `kotlinx-coroutines-core`
- `ktor-client-core`
- `ktor-client-cio`
- `exposed-core`
- `exposed-jdbc`
- `postgresql`
- `koog-agents`
