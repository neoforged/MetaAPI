# NeoForge Meta API

A JSON-based HTTP API for querying metadata about NeoForge and Minecraft versions.

## API Endpoints

See [meta.yaml](./meta.yaml).

## Database

The service uses SQLite to store its data.

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
