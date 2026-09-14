# Social Post Minimum Loop Design

## Scope

Implement the first `foodhub-social` delivery as a post-only minimum loop:

- publish a post;
- list visible posts with pagination;
- view one visible post;
- soft-delete a post owned by the current user.

This delivery does not implement follows, likes, comments, favorites, or the
feed. It does not modify or import code from `foodhub-merchant`. It also does
not change shared modules, the gateway, authentication, infrastructure, or the
root Maven build.

## Architecture

The implementation follows the repository's required layering:

```text
PostController -> PostService -> PostMapper -> foodhub_social.post
```

Request DTOs, persistence entities, and response VOs remain separate.

- `PostController` defines the four `/api/posts` endpoints and resolves the
  current user from `X-User-Id` for protected operations.
- `CreatePostRequest` validates the request body.
- `PostService` owns creation, pagination, lookup, ownership checks, JSON
  conversion, and soft deletion.
- `PostMapper` owns SQL access to the Social `post` table.
- `PostEntity` represents persistence state only.
- `PostView` and `PostPageView` define the public response model.
- `SocialApplication` imports the existing common exception handler and scans
  the Social mapper package.

All files remain under `foodhub-social`.

## Data Model

Create `foodhub-social/src/main/resources/db/migration/V1__create_social_tables.sql`
with a `post` table containing:

| Column | Type | Rules |
| --- | --- | --- |
| `id` | `BIGINT` | Auto-increment primary key |
| `author_id` | `BIGINT` | Required; obtained from `X-User-Id` |
| `content` | `VARCHAR(2000)` | Required, non-blank post content |
| `image_urls` | `JSON` | Optional array of image URLs |
| `merchant_id` | `BIGINT` | Optional positive identifier reference only |
| `status` | `VARCHAR(16)` | `VISIBLE` or `DELETED` |
| `published_at` | `DATETIME` | Required publication time |
| `updated_at` | `DATETIME` | Required last-update time |
| `deleted_at` | `DATETIME` | Set during soft deletion |

Add an index on `(status, published_at, id)` for visible-post pagination.
Social stores `merchant_id` but does not query the Merchant database, call the
Merchant service, or validate that the referenced merchant exists.

## Input Contract

Post creation accepts:

```json
{
  "content": "A post about local food",
  "imageUrls": ["https://example.com/image.jpg"],
  "merchantId": 123
}
```

Validation rules:

- trimmed content length is between 1 and 2000 characters;
- at most 9 image URLs are accepted;
- every image URL is at most 2048 characters and is an absolute `http` or
  `https` URL;
- `merchantId`, when supplied, is positive;
- clients never supply the acting author's ID in the body or path.

Image URLs are represented as `List<String>` at the API boundary and encoded
as JSON for persistence.

## HTTP API

### Publish

`POST /api/posts`

Requires `X-User-Id`. Creates a visible post and returns its `PostView` inside
`ApiResponse`.

### List

`GET /api/posts?page=1&pageSize=20`

Public endpoint. `page` starts at 1, `pageSize` defaults to 20 and may not
exceed 100. Only `VISIBLE` rows are returned, ordered by
`published_at DESC, id DESC`. The response contains `page`, `pageSize`,
`total`, and `items`.

### Detail

`GET /api/posts/{postId}`

Public endpoint. Returns only a visible post. A missing or deleted post produces
`POST_NOT_FOUND`.

### Delete

`DELETE /api/posts/{postId}`

Requires `X-User-Id`. Only the author may delete the post. Deletion changes the
status to `DELETED` and records `deleted_at`. Repeated deletion produces
`POST_NOT_FOUND`; an attempt by another user produces `POST_FORBIDDEN`.

## Response Model

`PostView` exposes:

- `id`;
- `authorId`;
- `content`;
- `imageUrls`;
- `merchantId`;
- `publishedAt`.

Responses use the existing `ApiResponse`. `publishedAt` is an ISO-8601
timestamp with the explicit `+08:00` offset matching the configured
`Asia/Shanghai` database timezone.

## Error Handling

Business failures use the existing `BusinessException` and
`GlobalExceptionHandler`:

| Code | Meaning |
| --- | --- |
| `INVALID_USER_ID` | `X-User-Id` is absent, malformed, or not positive |
| `INVALID_IMAGE_URL` | An image is not an absolute HTTP(S) URL |
| `POST_NOT_FOUND` | The post is absent or no longer visible |
| `POST_FORBIDDEN` | The current user is not the post author |
| `POST_DATA_INVALID` | Persisted image JSON cannot be decoded |

Bean-validation errors continue to use the shared `VALIDATION_ERROR` response.

## Persistence Flow

Creation trims content, validates URLs, serializes the image list, assigns the
gateway user ID, and inserts a `VISIBLE` record in one transaction. Reads query
visible records only. Pagination uses explicit mapper count and page queries,
so no shared MyBatis pagination configuration is required.

Deletion first reads the visible post, verifies its author, and then performs a
conditional soft update. A concurrent deletion that wins first is reported as
`POST_NOT_FOUND`.

## Testing

Development follows test-driven steps.

Controller tests cover successful creation, invalid request bodies, missing or
invalid identity headers, valid pagination, invalid pagination, detail lookup,
and deletion delegation.

Service tests cover author assignment, content trimming, image JSON conversion,
invalid image URLs, visible-post pagination, missing detail, successful owner
deletion, forbidden deletion, and concurrent/already-deleted behavior.

The focused verification command is:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl foodhub-social -am test
```

Before handoff, `git diff` must confirm that no `foodhub-merchant` or shared
module file changed.
