# Domain Module

## Purpose

`domain` contains the core business language of the project:

- `AggregateRoot<ID : Any>`
- domain entities
- value objects
- domain errors
- domain events
- `in ports`
- `out ports`
- pure domain models and services

## Rules

- no Ktor, Koin, Exposed, JDBC, `ktor-client`, or `koog`
- all `in/out ports` return `Either`
- `DomainError` is only for domain validation and invariants
- all value objects use `private constructor`, `create(...)`, and `fromTrusted(...)`
- all domain entities use `private constructor`, `create(...)`, and `fromTrusted(...)`
- aggregate identity is `id + version`, with `version` used for optimistic locking flow

## Dependencies

- `arrow-core`
