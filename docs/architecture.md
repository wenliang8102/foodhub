# FoodHub architecture

## Repository boundaries

FoodHub uses one repository with independently runnable services. Each business
service owns its data and follows the conventional Java flow:

```text
Controller -> Service -> Mapper -> Database
```

Request DTOs, persistence entities, and response VOs remain separate. Services
must not import another service's entities or mappers. Cross-service calls use
HTTP contracts and, where asynchronous behavior is required, RabbitMQ events.

## Modules

| Module | Port | Responsibility |
| --- | ---: | --- |
| `foodhub-gateway` | 8080 | External routing and edge concerns |
| `foodhub-auth` | 8101 | Accounts, authentication, and roles |
| `foodhub-merchant` | 8102 | Merchants, categories, and food items |
| `foodhub-social` | 8103 | Posts, follows, likes, comments, and feeds |
| `foodhub-coupon` | 8104 | Coupons and seckill activities |
| `foodhub-order` | 8105 | Orders, payment state, and cancellation |
| `foodhub-common-*` | n/a | Small, reusable technical building blocks, including messaging contracts |

The common modules may contain technical conventions, but never shared business
entities or a shared persistence model.

## Runtime model

MySQL, Redis, RabbitMQ, and Nacos run in Docker. Applications run from the IDE
or Maven Wrapper for fast debugging. Each service has its own logical MySQL
database even though local development uses one MySQL instance.

Configuration uses environment variables for local overrides. Nacos provides
service discovery from the first milestone; centralized configuration can be
introduced when the first shared runtime settings exist.
