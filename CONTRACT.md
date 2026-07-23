# Job Finder identity and downstream contract

Job Finder is the authenticated browser-facing boundary for Job Search. This
document describes how identity and the generated service contracts cross that
boundary.

## Browser to Job Finder

`POST /api/jobs/search` requires:

```http
Authorization: Bearer <access-token>
Content-Type: application/json
```

Job Finder accepts only RS256 tokens verified against the configured JWKS. The
token must have the configured issuer and audience, a non-empty `sub`, and
`token_type=access`.

`X-User-Id` is not an authentication mechanism. If a browser supplies it, the
header cannot override the verified token subject.

The request body may contain:

- `aspirations`
- `workPreferences`
- `homeLocation`
- `selectedProviders`

If the body is omitted, Job Finder obtains the authenticated user's profile and
maps its structured aspirations, work preferences, and location into the Job
Service request.

## Job Finder to User Profile

Job Finder calls:

```http
GET /api/profiles/me
Authorization: Bearer <the validated access token>
```

The User Profile producer contract owns this operation and derives profile
ownership from the token subject. Job Finder creates a generated client per
request before assigning the Bearer token, so token state is not shared between
users.

## Job Finder to Job Service

Job Finder calls:

```http
POST /api/jobs/search
X-User-Id: <verified token subject>
Content-Type: application/json
```

The current Job Service contract requires `X-User-Id`. Job Finder always
creates that header from the verified token subject; it never relays the
browser's value. Replacing this transitional header with authenticated service
identity is tracked separately by `JOBSVC-01`.

The request is converted into the model generated from the pinned Job Service
contract. The generated response is returned directly, preserving:

- canonical and provider job identity
- per-target-role results
- provider statuses
- normalised location and salary fields
- application and generated-document enrichment

## Contract provenance

Exact producer contracts and their source revisions are recorded under
`src/main/openapi`. `SHA256SUMS` protects the reviewed bytes. Maven uses OpenAPI
Generator 7.5.0 during `generate-sources`; generated code and JARs are never
committed.

Contract policy checks reject:

- missing, symbolic, or checksum-drifted inputs
- unexpected producer revision metadata
- removal of the required search or profile operations
- removal of key Job Search request or response boundary fields

## Errors and ownership constraints

- Missing or invalid authentication returns a stable, redacted `401` response
  with a correlation ID.
- An incomplete profile or invalid search request returns `400`.
- An unavailable User Profile or Job Service returns `503` without exposing
  credentials.
- Application-tracker proxy resource ownership is a separate boundary tracked
  by `JFG-01`; this contract does not claim that work is complete.
