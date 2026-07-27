# Job Finder Gateway

Job Finder Gateway is the authenticated browser-facing boundary for Job Search.
It accepts search requests from the client BFF and delegates canonical search
work to Job Service. It exposes Job Service's owner-scoped saved-job operations
so the browser can exchange a selected canonical result for a stable
`savedJobId`. It also contains application-tracker proxy endpoints; those
dependencies are integration boundaries, not owned implementations.

Status: **beta hardening in progress; not yet beta-ready**. Reproducible
generated clients, the Job Search authentication boundary, and Job Finder's
application-proxy ownership checks are implemented. Atomic Application Tracker
ownership, validation, and timeout issues are recorded in
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Local verification

Requires Java 17 and Maven 3.9 or later:

```bash
mvn -B --no-transfer-progress clean verify
docker build -t local/job-finder-gateway .
```

These commands are the clean-clone verification contract. Maven generates
Job Service and User Profile clients from the checksum-protected producer
contracts under `src/main/openapi`; generated sources and binaries are not
committed.

Authentication uses RS256 access tokens verified from the platform JWKS.
Runtime configuration includes `AUTH_JWKS_URI`, `JOB_FINDER_JWT_ISSUER`,
`JOB_FINDER_JWT_AUDIENCE`, `JOB_SERVICE_URL`, `USER_PROFILE_SERVICE_URL`,
`APPLICATION_TRACKER_SERVICE_URL`, and
`JOB_FINDER_MAX_SEARCH_REQUEST_BYTES` (default 65,536). No signing
credential is accepted or stored by this service.

Application list, status, and generated-withdraw routes derive ownership from
the validated token subject. Job Finder forwards that Bearer token to
Application Tracker and denies foreign and unknown IDs identically. Application
Tracker owns the durable generated-withdrawal operation and its atomic Document
Store cleanup; Job Finder does not perform a second best-effort delete.
Job Finder also creates a request-scoped generated Job Service client and
forwards the original Bearer token; it never relays a caller-supplied identity
header. Job Service independently verifies the token and derives search
identity from its subject.
The browser-facing API 1.4 validates bounded search criteria and caps request
bodies before any downstream call. It passes valid Job Service API 2.1 `page`,
`pageSize` and `sort` through unchanged and returns its bounded aggregate
`totalPages` and effective sort metadata. Invalid, malformed and oversized
requests use a stable versioned error with safe correlation metadata.

The same boundary provides `POST/GET /api/jobs/saved` and
`GET/DELETE /api/jobs/saved/{savedJobId}`. Job Service remains the authority
for owner identity, stable IDs, immutable snapshots, replay/version outcomes
and non-enumerating lookups. After save, document and application workflows
must use the returned server-owned `savedJobId`; browser job fields are not
authoritative input to those workflows.

Application Tracker must enforce the same subject/resource relationship
atomically; that dependency is tracked by
[`APP-03`](https://github.com/jobseekercopilot/application-tracker-service/issues/4).

The approved end-to-end request path and responsibility owners are defined in
the Infrastructure
[Job Search architecture ADR](https://github.com/jobseekercopilot/infrastructure/blob/develop/docs/adr/0001-job-search-architecture-and-ownership.md).

Swagger UI is exposed at `/swagger-ui/index.html` and generated OpenAPI at
`/v3/api-docs`; both require a valid access token. `CONTRACT.md` documents the
identity and generated-client boundaries.

## Branches, ownership, and licence

`develop` is the integration/default branch for beta hardening. See
`CONTRIBUTING.md` and `SECURITY.md`. This repository is proprietary,
source-available software; see `LICENSE`.
