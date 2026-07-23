# Job Finder Gateway

Job Finder Gateway is the authenticated browser-facing boundary for Job Search.
It accepts search requests from the client BFF and delegates canonical search
work to Job Service. It also contains application-tracker proxy endpoints;
those dependencies are integration boundaries, not owned implementations.

Status: **migration candidate; not beta-ready**. The current POM uses local
generated-client JARs and cannot build from a clean clone. Authentication,
validation, timeout, and contract issues are recorded in
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Local verification

Requires Java 17 and Maven 3.9 or later:

```bash
mvn -B clean verify
docker build -t local/job-finder-gateway .
```

These commands are the intended clean-clone contract. They remain expected to
fail until versioned OpenAPI inputs replace the excluded `libs/*.jar`
dependencies.

Runtime configuration includes `JWT_SECRET`, `JOB_SERVICE_BASE_URL`,
`USER_PROFILE_SERVICE_BASE_URL`, `APPLICATION_TRACKER_SERVICE_BASE_URL`, and
`DOCUMENT_STORE_SERVICE_BASE_URL`. No credential has a repository default.

Swagger UI is exposed at `/swagger-ui/index.html` and generated OpenAPI at
`/v3/api-docs` while enabled. `CONTRACT.md` is informative; a versioned,
machine-readable source contract is still required.

## Branches, ownership, and licence

`develop` is the integration/default branch for beta hardening. See
`CONTRIBUTING.md` and `SECURITY.md`. This repository is proprietary,
source-available software; see `LICENSE`.
