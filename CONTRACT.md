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
- `page`
- `pageSize`
- `sort`

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
Authorization: Bearer <the validated access token>
Content-Type: application/json
```

Job Finder creates a generated Job Service client per request before assigning
the Bearer token, so mutable authentication state is not shared between users.
It never forwards the browser's `X-User-Id`. Job Service independently verifies
the token signature, issuer, audience, expiry, nonblank subject and access-token
type, then derives the search identity only from `sub`.

The request is converted into the model generated from the pinned Job Service
contract. The generated response is returned directly, preserving:

- canonical and provider job identity
- per-target-role results
- provider statuses
- normalised location and salary fields
- application and generated-document enrichment
- bounded aggregate `page`, `pageSize`, `totalResults`, `totalPages` and
  effective `sort`

Job Finder does not invent paging defaults or reorder results. Omitted paging
fields remain unset so Job Service applies its documented defaults. Explicit
values pass through unchanged; Job Service remains the authority for bounds,
stable ordering, provider fetch budgets and aggregate metadata.

### Saved jobs

The same verified access token is required for:

```http
POST /api/jobs/saved
GET /api/jobs/saved?page=0&size=20
GET /api/jobs/saved/{savedJobId}
DELETE /api/jobs/saved/{savedJobId}
Authorization: Bearer <the validated access token>
```

Job Finder creates a new generated Job Service client before assigning each
request's Bearer token. It never accepts or forwards an owner header. Job
Service independently verifies the token and owns save validation, stable IDs,
immutable snapshot versions/content digests, list/get isolation and idempotent
unsave behavior.

Save preserves `201 CREATED` or `200 REPLAYED|UPDATED|REACTIVATED` and the
`X-Saved-Job-Outcome` header. Missing and other-owner identifiers map to the
same stable `404`. Malformed canonical input maps to a stable `400`; unavailable
or invalid downstream responses do not expose Job Service details. Request
logs replace saved-job UUIDs with `{savedJobId}`.

The returned `savedJobId`, snapshot version, content digest, timestamps and
source state are server-owned. Document Generation and Application Tracking
must retrieve the snapshot by that ID; they must not treat later browser job
content as authoritative.

## Job Finder to Application Tracker

All application proxy routes require the same validated access token as Job
Search. The list route retains its current compatibility path:

```http
GET /api/jobs/applications/user/{userId}
Authorization: Bearer <access-token>
```

`{userId}` must exactly match the token subject. Job Finder constructs the
downstream list path from the subject and never forwards `X-User-Id`.

Status routes first load the application with the validated Bearer token and
verify that its `userId` equals the token subject. Generated withdrawal is one
owner-scoped Application Tracker command: Tracker durably records the operation,
coordinates atomic Document Store cleanup and returns either completed `200` or
recovery-pending `202`. Job Finder preserves that response and does not issue
document deletes. Unknown and foreign record IDs return the same redacted `404`;
downstream response resource identity is checked before returning data.

Request logs replace
application IDs and list-owner path segments with placeholders. Framework web
logging stays at INFO and the first-request handler lookup warning is suppressed
so it cannot emit the unredacted path before the application filter.

These gateway checks are defence in depth, not an atomic authorization
boundary. Application Tracker must authenticate the token and enforce
subject/resource ownership within each query and mutation. That blocking
dependency is tracked by
[`APP-03`](https://github.com/jobseekercopilot/application-tracker-service/issues/4).

## Contract provenance

Exact producer contracts and their source revisions are recorded under
`src/main/openapi`. `SHA256SUMS` protects the reviewed bytes. Maven uses OpenAPI
Generator 7.5.0 during `generate-sources`; generated code and JARs are never
committed.

The Job Service pin currently consumes contract `2.1.0` at producer revision
`2cce83ddc057967b7a20d43c970499bffe0dd31f`. It includes bounded
aggregate paging/sorting, `totalPages`, aggregate
`searchStatus`/`matchingStatus`, the stable provider-result taxonomy, healthy
empty-result semantics, canonical Job schema `2.0` and the owner-scoped
saved-job resource. Compatibility checks protect search and saved-job response,
request and identity boundaries.

Contract policy checks reject:

- missing, symbolic, or checksum-drifted inputs
- unexpected producer revision metadata
- removal of required search, saved-job or profile operations
- removal of either downstream Bearer authentication boundary
- removal of key Job Search request or response boundary fields
- removal of paging/sort fields or the reviewed sort modes
- removal of saved-job identity, version, digest, source-state or canonical
  snapshot fields

## Errors and ownership constraints

- Missing or invalid authentication returns a stable, redacted `401` response
  with a correlation ID.
- An incomplete profile or invalid search request returns `400`.
- An invalid saved-job request returns a stable `400`; missing and foreign
  saved-job IDs return the same stable `404`.
- An unavailable User Profile or Job Service returns `503` without exposing
  credentials.
- Job Finder enforces its application-proxy ownership checks, but JFG-01 remains
  open until Application Tracker APP-03 supplies atomic downstream enforcement.
