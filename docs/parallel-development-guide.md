# Parallel development guide

This guide freezes the first implementation boundary for `foodhub-merchant`
and `foodhub-social`. The two services may be developed and merged in
parallel. They share technical conventions, but they do not share business
tables, entities, mappers, or service classes.

## Shared rules

### Service ownership

| Service | Port | Database | Owns |
| --- | ---: | --- | --- |
| `foodhub-merchant` | 8102 | `foodhub_merchant` | merchants, categories, food items |
| `foodhub-social` | 8103 | `foodhub_social` | posts, follows, likes, comments, favorites, feeds |

Each service follows:

```text
Controller -> Service -> Mapper -> Database
```

Use separate DTOs, entities, and response VOs. A service may store another
service's identifier, such as `merchantId`, but must not import that service's
entity or mapper.

### Shared request and identity contract

- Return business responses through `ApiResponse` from `foodhub-common-core`.
- Use `Long` for database identifiers.
- Serialize timestamps as ISO-8601 values with an explicit offset or `Z`.
- The gateway validates the Bearer JWT and supplies trusted identity headers:
  `X-User-Id`, `X-Username`, and `X-User-Role`.
- Protected operations use `X-User-Id` as the current user. Do not accept a
  user ID from a request body or path when the operation is about the current
  user.
- Role values are the uppercase values already used by authentication, such as
  `USER`, `MERCHANT`, and `ADMIN`.
- Do not connect to `foodhub_auth` or read its `user` table directly.

The identity headers are a gateway-to-service contract. They must be removed
and re-created by the gateway, so a public client cannot choose another user's
identity. Direct service access is for local development and tests; production
traffic goes through the gateway.

### Files and modules

An owner may change files under its own service and add tests there. Changes to
`foodhub-common-*`, `foodhub-auth`, `foodhub-gateway`, `infrastructure`, or the
root `pom.xml` require coordination with the repository owner. Do not rename
shared packages or alter common response/security types as part of these two
features.

Neither service uses RabbitMQ in this milestone. RabbitMQ is reserved for the
`foodhub-coupon` and `foodhub-order` flow described in
[`messaging-contracts.md`](messaging-contracts.md).

## `foodhub-merchant`

### Scope

Implement the catalog and merchant information needed by users and later
coupon/order features:

- category management;
- merchant creation, editing, listing, detail, and on/off-business status;
- food item creation, editing, listing, detail, and publish/unpublish status;
- keyword and category filtering;
- pagination and basic sorting by rating or sales;
- Redis caching for merchant and food detail reads.

The first version may use MySQL for keyword searches. Do not add Elasticsearch
or a new search service in this branch.

### Suggested packages

```text
com.foodhub.merchant
  controller
  dto
  entity
  mapper
  service
  vo
```

Keep business rules in services. Controllers should validate input, obtain the
gateway identity when needed, and return VOs. Mapper interfaces and persistence
entities stay inside this module.

### Data ownership

Use a migration under
`foodhub-merchant/src/main/resources/db/migration/` for the merchant database.
The minimum tables are:

```text
category
merchant
food_item
```

Recommended constraints and fields:

- category names are unique among active categories;
- merchant has name, category ID, address, longitude, latitude, rating,
  sales count, business status, created time, and updated time;
- food item has merchant ID, name, price, image URL, sales count, publish
  status, created time, and updated time;
- foreign-key-like identifiers and status values are validated by the service;
- soft deletion or an explicit `status` field must prevent unavailable data
  from public listings.

Use `foodhub:merchant:detail:{merchantId}` and
`foodhub:food:detail:{foodId}` through the existing Redis key helper. Cache
only public, active detail data. Invalidate the corresponding key after an
admin or merchant update.

### API direction

Keep the existing gateway route families:

```text
GET    /api/merchants
GET    /api/merchants/{merchantId}
GET    /api/categories
GET    /api/foods
GET    /api/foods/{foodId}
POST   /api/merchants
PATCH  /api/merchants/{merchantId}
POST   /api/foods
PATCH  /api/foods/{foodId}
```

The exact request and response field names should be documented with the
implementation. Public reads may be anonymous. Mutations require `MERCHANT`
or `ADMIN`; a merchant may modify only its own merchant and food records.
Do not implement coupon, seckill, order, or social endpoints here.

### Acceptance checklist

- A public client can list and view active merchants, categories, and foods.
- Admin/merchant mutations reject invalid input and unauthorized ownership.
- Detail reads use Redis on a cache hit and repopulate it on a miss.
- Unpublished merchants or food items do not appear in public results.
- The module builds without importing classes from `foodhub-social`,
  `foodhub-coupon`, or `foodhub-order`.

## `foodhub-social`

### Scope

Implement user-generated food content and social interactions:

- publish, list, detail, and delete a food post;
- optional image URLs and an optional `merchantId` reference;
- follow and unfollow users;
- follower and following lists;
- like and unlike a post;
- create and delete one's own comments;
- favorite and unfavorite a post;
- pull-mode feed for posts by followed users.

The first feed implementation is a database query ordered by publish time. A
Redis feed or push model can be added later without changing the ownership
boundary.

### Suggested packages

```text
com.foodhub.social
  controller
  dto
  entity
  mapper
  service
  vo
```

If post rules become complex, add a local `domain` package. Do not move those
rules into a common module merely to share code with another service.

### Data ownership

Use a migration under
`foodhub-social/src/main/resources/db/migration/` for the social database. The
minimum tables are:

```text
post
user_follow
post_like
post_comment
post_favorite
```

Required uniqueness and visibility rules:

- `(follower_id, following_id)` is unique and a user cannot follow itself;
- `(user_id, post_id)` is unique for likes and favorites;
- comments must contain non-blank content;
- only the comment author may delete a comment;
- only the post author may delete a post;
- deleted or hidden posts never appear in public lists or feeds;
- a post may store `merchantId`, but social does not own or join the merchant
  table.

Use `foodhub:post:like:{postId}` for a high-frequency like counter only after
the database record rules are in place. The database uniqueness constraint is
the final duplicate-prevention mechanism.

### API direction

Keep the existing gateway route families:

```text
POST   /api/posts
GET    /api/posts
GET    /api/posts/{postId}
DELETE /api/posts/{postId}
POST   /api/follows/{userId}
DELETE /api/follows/{userId}
GET    /api/follows/following
GET    /api/follows/followers
GET    /api/feed
POST   /api/posts/{postId}/likes
DELETE /api/posts/{postId}/likes
POST   /api/posts/{postId}/comments
DELETE /api/posts/{postId}/comments/{commentId}
POST   /api/posts/{postId}/favorite
DELETE /api/posts/{postId}/favorite
```

Post creation, follow, like, comment, favorite, and deletion require a logged-
in user. Public post detail/list behavior must filter out hidden content.
Every mutation derives the acting user from `X-User-Id`; never trust a
`userId` field supplied by the client.

Social does not synchronously call the merchant service in the first version.
It may accept and return a `merchantId` reference. If merchant display data is
needed later, add a small HTTP read contract or a local snapshot deliberately;
do not import merchant persistence classes.

### Acceptance checklist

- A logged-in user can create and remove their own post.
- A user cannot follow themselves or create duplicate follow/like/favorite
  records.
- Users can comment and delete only their own comments.
- Feed results contain only visible posts from followed users.
- The module builds without importing classes from `foodhub-merchant`,
  `foodhub-coupon`, or `foodhub-order`.

## Merge and handoff process

1. Work on separate branches, for example `feature/merchant-v0.2` and
   `feature/social-v0.2`.
2. Keep commits focused on one service. Do not include generated `target/`
   files or local configuration.
3. Before handoff, run the focused build from the repository root:

   ```powershell
   .\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-merchant -am test
   .\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
   ```

4. Report changed migrations, endpoint paths, required roles, Redis keys, and
   any assumptions about the other service. The repository owner will perform
   the root build and gateway smoke check before merging.

The first integration point between these services and later work is the
identifier contract: coupon and order may reference `merchantId`, `foodItemId`,
or an activity target, but they must not reach into either service's database.
