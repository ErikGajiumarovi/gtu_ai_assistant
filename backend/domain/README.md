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
- prefer Arrow DSL primitives: `either {}`, `ensure`, `bind`, `raise`, `Either.catch`
- `DomainError` is only for domain validation and invariants
- all value objects use `private constructor`, `create(...)`, and `fromTrusted(...)`
- all domain entities use `private constructor`, `create(...)`, and `fromTrusted(...)`
- aggregate identity is `id + version`, with `version` used for optimistic locking flow
- each `in port` keeps its own local contract in one file: port, command/query, result, error
- do not use repositories; model `out ports` explicitly as `Find*`, `Save*`, `Exists*`, `Update*`
- use `fun interface` with `suspend operator fun invoke(...)` where possible
- every `Find*Port` must use strategy pattern

## Dependencies

- `arrow-core`

## Current scope

The module already contains the first aggregate:

- `User`
- `UserId`
- `UserName`
- `UserLastName`
- `UserEmail`
- `CreateUserUseCase`
- `FindUserPort`
- `SaveUserPort`
- `ExistsUserPort`
- `UpdateUserPort`
- `Chat`
- `ChatId`
- `Message`
- `MessageSenderType`
- `CreateChatWithAgentUseCase`
- `ContinueChatWithAgentUseCase`
- `ListChatsUseCase`
- `DeleteChatUseCase`
- `FindChatPort`
- `SaveChatPort`
- `DeleteChatPort`
- `GenerateMessagePort`
