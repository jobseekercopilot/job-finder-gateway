# Job Finder Gateway

Job Finder Gateway is the authenticated browser-facing boundary for Job Search.
It accepts search requests from the client BFF and delegates canonical search
work to Job Service. It also contains application-tracker proxy endpoints;
those dependencies are integration boundaries, not owned implementations.

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
`APPLICATION_TRACKER_SERVICE_URL`, and `DOCUMENT_STORE_SERVICE_URL`. No signing
credential is accepted or stored by this service.

Application list, status, and generated-withdraw routes derive ownership from
the validated token subject. Job Finder forwards that Bearer token to
Application Tracker, pre-authorizes resource mutations, denies foreign and
unknown IDs identically, and forwards the token to generated-document cleanup.
Application Tracker must enforce the same subject/resource relationship
atomically; that dependency is tracked by
[`APP-03`](https://github.com/jobseekercopilot/application-tracker-service/issues/4).

Swagger UI is exposed at `/swagger-ui/index.html` and generated OpenAPI at
`/v3/api-docs`; both require a valid access token. `CONTRACT.md` documents the
identity and generated-client boundaries.

## Branches, ownership, and licence

`develop` is the integration/default branch for beta hardening. See
`CONTRIBUTING.md` and `SECURITY.md`. This repository is proprietary,
source-available software; see `LICENSE`.
