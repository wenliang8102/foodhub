# Single-server deployment

The production delivery pipeline is [`.github/workflows/release.yml`](../.github/workflows/release.yml).
It builds each Spring Boot service into a GHCR image, then deploys the version to one
Docker host. A deployment happens when a `v*` Git tag is pushed, or when the workflow
is run manually with an existing image tag. The `production` GitHub Environment can be
configured with required reviewers before the remote deployment job runs.

## Server preparation

Install Docker Engine and Docker Compose v2.20 or newer. Create the directory that will
contain the deployment, for example `/opt/foodhub`, and copy
[`infrastructure/.env.production.example`](../infrastructure/.env.production.example)
to `/opt/foodhub/.env`. Replace all placeholder secrets. Do not commit this file.

The workflow copies `production.compose.yml` and the MySQL initialization script into
that directory. Only the gateway's `GATEWAY_PORT` is published to the host; MySQL,
Redis, RabbitMQ and Nacos remain on the private Docker network.

## GitHub configuration

Create a repository environment named `production` and add these environment secrets:

| Secret | Purpose |
| --- | --- |
| `DEPLOY_HOST` | Server hostname or IP address |
| `DEPLOY_USER` | SSH deployment user |
| `DEPLOY_SSH_KEY` | Private SSH key for that user |
| `DEPLOY_PATH` | Absolute server directory, such as `/opt/foodhub` |
| `REGISTRY_USERNAME` | GHCR account allowed to pull the published packages |
| `REGISTRY_TOKEN` | GHCR token with `read:packages` permission |

Make the published GHCR packages public, or use a dedicated pull-only token for
`REGISTRY_TOKEN`. The workflow itself pushes with its scoped `GITHUB_TOKEN`.

## Release

After CI succeeds, create and push a version tag:

```powershell
git tag v0.1.0
git push origin v0.1.0
```

The workflow waits for Compose health checks. Spring Boot applies Flyway migrations as
each database-owning service starts, so database migrations must remain backward
compatible with the currently deployed version.
