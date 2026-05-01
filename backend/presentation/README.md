# Presentation Module

## Purpose

`presentation` contains Ktor HTTP handling and maps transport requests into domain-shaped input for use cases.

## Rules

- use only `in ports` plus models from `domain`
- never use `out ports` directly
- extract raw request data and convert it to domain types through `create(...)`
- if `create(...)` returns `Either.Left(DomainError)`, stop before calling the use case
- never use `fromTrusted(...)` in this module
- pass only valid value objects, domain data classes, and other domain types into `application`

## Dependencies

- `project(:backend:domain)`
- `project(:backend:application)`
- `arrow-core`
- `ktor-server-core`
