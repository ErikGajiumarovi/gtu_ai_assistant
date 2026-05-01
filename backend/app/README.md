# App Module

## Purpose

`app` is the application entry point. It wires the modules together, configures DI, and starts the web server.

## Rules

- keep only composition and bootstrap logic here
- no domain rules or use case orchestration
- assemble `presentation`, `application`, `infrastructure`, and `domain`
- configure Koin and server startup in this module

## Dependencies

- `project(:backend:domain)`
- `project(:backend:application)`
- `project(:backend:presentation)`
- `project(:backend:infrastructure)`
- `koin-core`
- `ktor-server-core`
- `ktor-server-netty`
