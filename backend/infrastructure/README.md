# Infrastructure Module

## Purpose

`infrastructure` contains implementations of `out ports` for database, HTTP, AI, and other external systems.

## Rules

- implement only `out port` adapters here
- implement explicit ports like `Find*Port`, `Save*Port`, `Exists*Port`, `Update*Port`; do not reintroduce repositories
- catch technical exceptions and convert them to `Either.Left(InfrastructureError(cause))`
- use `fromTrusted(...)` only when reconstructing domain models from trusted persisted data
- perform blocking IO inside `withContext(Dispatchers.IO)`
- do not expose SQL, HTTP, or SDK-specific models outside the adapter boundary
- persist `Chat` through relational records, with `chat_messages` stored separately from `chats`
- AI adapters such as `GenerateMessagePort` also live here, but remain separate from persistence code

## Dependencies

- `project(:backend:domain)`
- `arrow-core`
- `kotlinx-coroutines-core`
- `ktor-client-core`
- `ktor-client-cio`
- `exposed-core`
- `exposed-jdbc`
- `exposed-java-time`
- `postgresql`
- `koog-agents`
