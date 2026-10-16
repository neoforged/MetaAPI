# NeoForge Meta API

A JSON-based HTTP API for querying metadata about NeoForge and Minecraft versions.

## API Endpoints

See [meta.yaml](./meta.yaml).

## Database

In development the service uses an embedded SQLite database. For production (Kubernetes)
it runs against PostgreSQL — see [Deployment](#deployment-kubernetes) below.

## Indexing

The service indexes Minecraft and NeoForge versions, but is aimed at indexing all of our released software components.

Each Maven component that the service should index must be listed in the application configuration file. It allows
specifying groupId/artifactId, which repository the artifacts are published to, and special rules which artifacts
are expected for each version range of the component, to accommodate the set of published artifacts changing over time.

## Webhooks

The API can invoke webhooks when versions change.

See `net.neoforged.meta.config.trigger.TriggersProperties` for details.

## Authentication

The API allows for three types of authentication. Two are intended for external consumers, while the third is used
only internally by NeoForge team members.

### API-Key Authentication

External consumers of the API can authenticate using an API key header. To get an API key, contact us
on [Discord](https://discord.neoforged.net/).

The name of the HTTP header is `X-API-Key`.

### GitHub Actions

If your code is running inside a GitHub Actions Workflow, you can also use the built-in OpenID Connect Token to
authenticate
with the Meta API. Our API requires the token audience `neoforged-meta-api`.

Example Workflow to access the API:

```yaml
name: API Test

on:
  workflow_dispatch:

permissions:
  id-token: write

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Get OIDC Token
        id: oidc
        uses: actions/github-script@v7
        with:
          script: core.setOutput('token', await core.getIDToken('neoforge-meta-api'))
      - name: Query API
        run: |
          curl --fail -H "Authorization: Bearer ${META_API_TOKEN}" https://meta-api.prod.k8s.neoforged.net/v1/neoforge-versions/ -v -o response
          cat response | jq
        env:
          META_API_TOKEN: "${{ steps.oidc.outputs.token }}"
```

### User Authentication

If a browser is used to access the API, it will redirect to our IDP (Dex), which authenticates the user using
GitHub and establishes a Session-Cookie. This is used to inspect the data stored in the API and debug issues
by the NeoForge team and is not available to external users.

## Deployment (Kubernetes)

The service is designed to run as multiple replicas against a shared PostgreSQL database.
Scheduled discovery jobs are coordinated between replicas via distributed database locks,
so exactly one replica runs each job per scheduled slot. See `.agent/plans/ha-setup.md`
for the full design.

The container image `ghcr.io/neoforged/metaapi` is published as a multi-platform
(amd64 + arm64) OCI index on every push to `main`. The Helm chart lives in
[`helm/meta-api`](./helm/meta-api) and is also published to GHCR as an OCI artifact
(`oci://ghcr.io/neoforged/meta-api`), so you can either clone the repo or pull it
directly:

```bash
# Pull the chart from GHCR (requires ghcr credentials for a private package)
helm registry login ghcr.io
helm pull oci://ghcr.io/neoforged/meta-api --version 1.0.0 -d .
```
**Releases are semantic-version git tags.** Pushing a tag like `v1.2.3` triggers a
release build that publishes:

- container image `ghcr.io/neoforged/metaapi:1.2.3` (and re-points `latest`),
- the Helm chart as OCI artifact at version `1.2.3`
  (`helm pull oci://ghcr.io/neoforged/meta-api --version 1.2.3 -d .`),
- a GitHub Release with the chart `.tgz` attached.

Pushes to **any branch** (or manual dispatch) produce ephemeral dev/test builds on GHCR —
no GitHub Release is created. The image gets both a short-SHA tag and a per-branch pointer
(slashes → dashes, case preserved, e.g. `feature-HA-Capable`), and a matching Helm chart is
published at `oci://ghcr.io/neoforged/meta-api:0.0.0-feature-HA-Capable`. Both always point at
that branch's latest push; the chart version is a semver prerelease, so address it by explicit
`--version` (it won't show up in default version listings). Pin an exact image build with
`--set image.tag=<short-sha>`. To cut a release:
tag and push (`git tag v1.2.3 && git push origin v1.2.3`).
The chart supports both clusters that use **Ingress** (legacy) and clusters that use the
**Gateway API** (newer); enable exactly one of them:

```bash
# Legacy cluster — Ingress (chart-managed DB credentials)
helm install meta-api ./helm/meta-api \
  -n meta --create-namespace \
  --set image.tag=1.0.0 \
  --set database.url='jdbc:postgresql://db-host:5432/metaapi' \
  --set database.password='<password>' \
  --set ingress.enabled=true \
  --set ingress.host=meta-api.prod.k8s.neoforged.net

# Newer cluster — Gateway API (requires the gateway.networking.k8s.io CRDs)
helm install meta-api ./helm/meta-api \
  -n meta --create-namespace \
  --set image.tag=1.0.0 \
  --set gateway.enabled=true \
  --set gateway.parentRef.name=<gateway-name> \
  --set 'gateway.hostnames[0]=meta-api.prod.k8s.neoforged.net'
```
Database credentials are provided in exactly one of two ways:

- **Inline (default):** set `database.url`, `database.username` and `database.password`
in the values; the chart then creates and manages a `<fullname>-db` Secret itself.
  This is the recommended option for GitOps, since everything lives in the release.
- **Pre-existing Secret:** leave the inline values empty and set
  `database.existingSecret=<name>` pointing at a Secret with keys `url`, `username`
  and `password`. The chart then does not create any database Secret itself:

```bash
kubectl create secret generic meta-api-db -n meta \
  --from-literal=url='jdbc:postgresql://db-host:5432/metaapi' \
  --from-literal=username=metaapi \
  --from-literal=password='<password>'
```

To run API-only replicas,
set `scheduledTasks.mavenVersionDiscoveryEnabled=false` and
`scheduledTasks.minecraftVersionDiscoveryEnabled=false` on them, keeping at least one
replica with both enabled.

### Environment variables

| Variable | Required | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes (K8s) | Must be `postgres` for HA deployments. |
| `DB_URL` | yes (K8s) | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://db:5432/metaapi`. |
| `DB_USERNAME` / `DB_PASSWORD` | yes (K8s) | Database credentials. |
| `DB_POOL_SIZE` | no | HikariCP max pool size per replica (default 15). |
| `INSTANCE_ID` | no | Stable identifier for this instance in job locks and logs. Generated from the pod hostname if unset. |

### Health & probes

- `/actuator/health/liveness` — process liveness (K8s liveness + startup probe).
- `/actuator/health/readiness` — traffic readiness; includes database connectivity, connection pool stats,
  Flyway migration status, and which jobs this instance is currently running.
- `/actuator/prometheus` — metrics for the monitoring stack.

Both health paths are publicly reachable (K8S probes do not authenticate); all other actuator endpoints
require the admin role.
