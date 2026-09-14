# ADR 0001: Modular monorepo with layered services

- Status: Accepted
- Date: 2026-09-12

## Decision

Use a Maven multi-module monorepo. Split deployable modules by business
capability and use Controller-Service-Mapper layering inside business services.
Keep authentication and user accounts together initially, and keep coupons and
seckill activities together initially.

## Consequences

- One build validates cross-module compatibility.
- Services remain independently runnable and deployable.
- Shared modules require discipline and cannot contain business persistence
  models.
- Complex order and seckill rules may gain a dedicated `domain` package when
  their behavior warrants it.

