# NeoForge Meta API

A JSON-based HTTP API for querying metadata about NeoForge and Minecraft versions.

## API Endpoints

See [meta.yaml](./meta.yaml).

## Authentication

The application implements dual authentication strategies depending on the access pattern. API endpoints under `/v1/**`
require API key authentication via the `X-API-Key` header. This requires no session cookies or CSRF.
API keys are configured in the application configuration file.

UI endpoints use our GitHub OAuth2 authentication system with cookie-based sessions and CSRF protection.
Static resources like CSS, JavaScript, and images are publicly accessible without authentication.
The session cookie also allows use of the API endpoints, which makes testing the API in the browser
much easier.

This dual-authentication setup allows us to inspect and administrate using the UI while API consumers authenticate
using an API key.
