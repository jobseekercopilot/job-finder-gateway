# Job Finder Gateway beta-readiness audit

## Current role

The client BFF posts to `POST /api/jobs/search`. This gateway derives a user
identity, optionally obtains a profile, forwards a Job Search request to Job
Service, and maps its response. It does not call providers directly.

## Blocking findings

- **P0 security:** application list, status, and withdrawal routes do not
  consistently prove that the authenticated subject owns the requested user or
  application.
- **P1 resilience:** the downstream `RestTemplate` has no connect or response
  timeout and there is no request deadline or cancellation policy.
- **P1 API safety:** request fields lack bounds, length constraints, provider
  allowlisting, and stable error semantics.
- **P1 secret safety:** the inspected source and legacy history contained
  non-empty JWT-secret defaults. Current source no longer accepts a shared
  signing secret, but credential rotation/history decisions remain required.

## Evidence completed in the current hardening slice

- Browser identity is accepted only from an RS256 access token with the
  configured issuer, audience, non-empty subject, and `token_type=access`.
- Caller-supplied `X-User-Id` cannot authenticate Job Finder or override the
  JWT subject used for Job Search.
- The validated Bearer token is forwarded by a per-request generated client to
  User Profile; mutable authentication state is not shared between requests.
- Job Service receives the validated subject, and the browser-supplied identity
  header is never relayed.
- Job Service and User Profile clients are generated at build time from exact,
  checksum-protected producer contracts. Local JAR and `systemPath`
  dependencies have been removed.
- The complete generated Job Service response is returned without a duplicate
  hand-maintained response DTO.
- Integration tests cover valid, missing, malformed, expired, forged,
  unknown-key, wrong-algorithm, wrong-issuer, wrong-audience, and refresh-token
  cases, plus header spoofing and downstream identity propagation.

## Target boundary

Only a verified JWT subject or a narrowly scoped, authenticated service
identity may establish the user. Job Finder must pass a trusted identity
downstream, reject caller-supplied ownership overrides, validate bounded
requests, apply an end-to-end deadline, and return a stable contract with
correlation metadata. Saving and application actions must call their owning
services using subject-aware APIs.

## Evidence required to close

- Clean-clone `mvn -B clean verify` and container build.
- Integration evidence for application-resource cross-user access, downstream
  timeout, and structured downstream failures.
- Secret scan of every migrated ref and documented rotation/history decision.
- Load evidence that gateway timeouts fit inside the end-user latency budget.

This audit is a work queue input. It is not a beta-readiness approval.
