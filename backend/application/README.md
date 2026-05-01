# Application Module

## Purpose

`application` contains implementations of `in ports` and use case orchestration.

## Rules

- each `UseCaseImpl` uses only constructor-injected `out ports`
- do not depend on `Repository` abstractions; use explicit ports like `FindUserPort`, `SaveUserPort`, `ExistsUserPort`, `UpdateUserPort`
- `UseCaseImpl` may also use only `domain` models and helpers from this module
- `DomainError` must be mapped to `UseCaseError`
- `InfrastructureError` from `out ports` must be mapped to `UseCaseError` via `mapLeft`
- prefer Arrow DSL primitives: `either {}`, `bind`, `ensure`, `raise`, `Either.catch`
- no direct access to transport, database, HTTP clients, AI SDKs, or framework wiring
- no `withContext(Dispatchers.IO)` here; IO dispatcher switching belongs to `infrastructure`
- all functions are `suspend`

## Dependencies

- `project(:backend:domain)`
- `arrow-core`
- `kotlinx-coroutines-core`
