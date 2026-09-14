# FoodHub

FoodHub is a local services platform built as a Spring Boot and Spring Cloud
multi-module project. The first milestone establishes service boundaries,
shared infrastructure, service discovery, and a repeatable local environment.

## Prerequisites

- JDK 21
- Docker Desktop with Linux containers enabled
- Git

Maven does not need to be installed globally; use the checked-in Maven Wrapper.

## Start the development environment

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f infrastructure/compose.yaml up -d
.\mvnw.cmd clean verify
```

Infrastructure endpoints:

| Component | Address |
| --- | --- |
| Nacos | http://localhost:8848/nacos |
| RabbitMQ management | http://localhost:15672 |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

Application ports and module responsibilities are documented in
[`docs/architecture.md`](docs/architecture.md).

The initial RabbitMQ exchange, routing keys, queues, payloads, and idempotency
rules are documented in
[`docs/messaging-contracts.md`](docs/messaging-contracts.md).

Parallel implementation boundaries for the merchant and social teams are in
[`docs/parallel-development-guide.md`](docs/parallel-development-guide.md).

## Run a service

```powershell
.\mvnw.cmd -pl foodhub-auth -am spring-boot:run
```

The default `local` profile reads connection settings from environment
variables and uses development-only defaults. Production secrets must never be
committed to this repository.
