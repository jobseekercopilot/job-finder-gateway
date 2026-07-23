# Job Finder Gateway beta-readiness audit

## Current role

The client BFF posts to `POST /api/jobs/search`. This gateway derives a user
identity, optionally obtains a profile, forwards a Job Search request to Job
Service, and maps its response. It does not call providers directly.

## Blocking findings

- **P0 security:** `X-User-Id` is accepted as authentication when no JWT is
  present. A browser-controlled header can therefore establish ownership.
- **P0 security:** application list, status, and withdrawal routes do not
  consistently prove that the authenticated subject owns the requested user or
  application.
- **P0 reproducibility:** four generated clients are `systemPath` dependencies
  under the excluded `libs` directory.
- **P1 resilience:** the downstream `RestTemplate` has no connect or response
  timeout and there is no request deadline or cancellation policy.
- **P1 API safety:** request fields lack bounds, length constraints, provider
  allowlisting, and stable error semantics.
- **P1 contract ownership:** response DTOs duplicate Job Service manually and
  no versioned OpenAPI source contract is owned here.
- **P1 secret safety:** the inspected source and legacy history contained
  non-empty JWT-secret defaults. The migration candidate removes the current
  default, but startup validation and credential rotation/history decisions
  remain required.

## Target boundary

Only a verified JWT subject or a narrowly scoped, authenticated service
identity may establish the user. Job Finder must pass a trusted identity
downstream, reject caller-supplied ownership overrides, validate bounded
requests, apply an end-to-end deadline, and return a stable contract with
correlation metadata. Saving and application actions must call their owning
services using subject-aware APIs.

## Evidence required to close

- Clean-clone `mvn -B clean verify` and container build.
- Unit and integration tests for JWT subject extraction, missing/invalid JWT,
  header spoofing, cross-user access, downstream timeout, structured failures,
  and contract compatibility.
- Versioned OpenAPI source plus generated-client drift check.
- Secret scan of every migrated ref and documented rotation/history decision.
- Load evidence that gateway timeouts fit inside the end-user latency budget.

This audit is a work queue input. It is not a beta-readiness approval.
