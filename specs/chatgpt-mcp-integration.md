# ChatGPT MCP OAuth integration

Status: Draft for human review.

## Scope

Extend MCP-S01, MCP-S02, MCP-S05 and MCP-S06 in [MCP analytics](mcp-analytics.md).
Preserve RES-BPL-002 (synthetic data), RES-BPL-004 (review), RNF-BPL-003
(JWT and operation scopes), and UC-BPL-002 S20 (REST authorization).
No boleto, audit, Outbox, Flyway or public REST contract changes.

## Local evidence

- The Compose Keycloak advertises the loopback issuer. The MCP validates
  that exact issuer and uses the internal Keycloak JWKS URL. Postman also
  uses the loopback issuer. Changing the shared Keycloak hostname would
  affect previously issued tokens and existing REST test clients.
- The running tunnel-client-runtime process was identified. Its profile,
  target mapping, resource identifier and credentials were not inspected.
  The control-plane status and v0.0.21 version were supplied by the operator.
- The running default MCP returned HTTP 200 at
  `/.well-known/oauth-protected-resource` with
  `resource: http://127.0.0.1:8090` and no `authorization_servers`.
  This local identifier is distinct from the transport URL ending in
  `/mcp` and from the fixed JWT audience `boleto-analytics-mcp`.

## Proposed architecture

For Secure MCP Tunnel discovery, the ChatGPT overlay can advertise the exact
local origin `http://127.0.0.1:8090` while publishing the HTTPS Keycloak issuer.
Only the explicit tunnel discovery mode permits that HTTP identifier. Both
protected-resource metadata paths must agree, and the anonymous MCP challenge
must point to the root metadata document. JWT signature, issuer, expiry,
`boleto-analytics-mcp` audience and `analytics:read` checks remain mandatory.
The connector-facing resource and any additional audience remain PENDENTE
until verified through the tunnel.

The optional ngrok transport reuses the isolated Keycloak and the OAuth
route allowlist in Nginx. `compose.chatgpt.ngrok.yaml` binds only that proxy
to loopback on port 18082 by default. Set `KEYCLOAK_PUBLIC_HOSTNAME` to the
assigned development hostname, for example `auth-boleto.example.test`.
The standard Keycloak and REST issuer stay on the local configuration.
The operator activates ngrok separately after validating the discovery
document and blocked routes locally. `CHATGPT_MCP_RESOURCE` remains PENDENTE
until confirmed from the protected-resource metadata seen by the client.

Add an opt-in Compose overlay with a separate Keycloak 26 instance and
separate volume, initialized with the existing realm definition. Provision
only the ChatGPT client in that instance and use a non-admin test account
with synthetic data. Reuse the existing client provisioning script.
Keep the standard REST and Keycloak services unchanged.

A manually activated named Cloudflare Tunnel routes the approved HTTPS
hostname to a private path-allowlisting reverse proxy. Forward only required
realm-scoped OAuth/OIDC discovery, authorization, token, login actions, JWKS
and login assets. Deny admin, master realm, health, metrics and unmatched
paths. The proxy sets trusted forwarding headers. Cloudflared has no host
ports. The OpenAI Secure MCP Tunnel continues to route to the private MCP
endpoint and does not carry browser traffic to Keycloak.

The isolated Keycloak advertises the exact public realm issuer. The opt-in
MCP instance validates that issuer and RS256 signatures through the isolated
Keycloak's internal JWKS endpoint. Preserve expiration, scope and audience
checks. Publish local protected-resource metadata and advertise its URL on
the 401 challenge. Confirm the rewritten resource and
audience binding against actual ChatGPT configuration before end-to-end use.

Authorization uses a public client with Authorization Code and S256 PKCE.
The authorization request uses the exact ChatGPT callback. Exchange the
code with the verifier at the HTTPS token endpoint. Run MCP initialize,
list and a read-only call using the resulting access token.

## Acceptance and verification

| ID | Expected result |
| --- | --- |
| CHAT-MCP-01 | Default Compose and Postman continue using the original REST issuer. The overlay is optional. |
| CHAT-MCP-02 | The HTTPS issuer matches discovery metadata, JWT issuer, and MCP validation. JWKS remains reachable internally. |
| CHAT-MCP-03 | Anonymous, wrong issuer, wrong audience, expired and invalid-signature requests return 401. A valid token missing the scope returns 403. |
| CHAT-MCP-04 | A valid signed token initializes the MCP, discovers exactly six tools and invokes an authorized aggregate query. PostgreSQL rejects writes by the reader. |
| CHAT-MCP-05 | Only allowlisted OAuth paths are reachable over the HTTPS proxy. The UI callback completes a real ChatGPT flow or the test is marked pending. |

Use synthetic signed JWTs and a local JWKS fixture in contract tests.
Run the Gradle MCP and REST suites, Compose configuration validation and
Docusaurus typecheck/build. Validate Keycloak PKCE and token claims in an
isolated runtime when feasible. A tunnel status alone is not acceptance.

## Operation and open decisions

Discovery requires an approved HTTPS hostname and a running tunnel.
PENDENTE: confirm the external resource, the accepted audience/resource mapping and
that the connection accepts pre-registered public client authentication.
Use `KEYCLOAK_PUBLIC_HOSTNAME` only in the optional overlay. Obtain the
external resource from the metadata observed through the actual
connection and compare it to the OAuth request parameter. OpenAI's
tunnel documentation does not establish a deterministic rewrite rule.
If the flow requires the HTTPS resource in `aud`, review a fixed
additional audience mapper and an overlay validator that requires it
before enabling the integration.
Use ignored local settings and secret injection. Never version or log tokens.

Deactivation stops the optional services, disconnects the ChatGPT app and
revokes test sessions or clients and tunnel credentials where appropriate.
Restore the default MCP Compose service if reusing its host port. Preserve
the standard stack and database volumes. An external login surface still
has brute-force, availability and patching risks even with admin routes
blocked. Use synthetic data, limited users, rate limits and short sessions.
