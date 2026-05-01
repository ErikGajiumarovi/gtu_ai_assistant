# App Module

## Purpose

`app` is the application entry point. It wires the modules together, configures DI, and starts the web server.

## Rules

- keep only composition and bootstrap logic here
- no domain rules or use case orchestration
- assemble `presentation`, `application`, `infrastructure`, and `domain`
- configure Koin and server startup in this module
- temporary non-persistent or non-AI bootstrap adapters may live here when needed to make the app runnable

## Dependencies

- `project(:backend:domain)`
- `project(:backend:application)`
- `project(:backend:presentation)`
- `project(:backend:infrastructure)`
- `koin-core`
- `ktor-server-core`
- `ktor-server-netty`
