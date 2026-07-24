# Job Finder Gateway beta-readiness audit

## Current role

The client BFF posts to `POST /api/jobs/search`. This gateway derives a user
identity, optionally obtains a profile, forwards a Job Search request to Job
Service, and maps its response. It does not call providers directly.

## Remaining blocking findings

- **P0 dependency:** Application Tracker does not yet enforce the forwarded
  subject/resource relationship atomically. Job Finder now checks ownership at
  its boundary, but pre-authorization cannot secure direct tracker access or
  remove a check/use race. This is tracked by
  [APP-03](https://github.com/jobseekercopilot/application-tracker-service/issues/4).
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
- Application list requests must match the token subject; the downstream list
  path is built from that subject rather than the caller's header.
- Status and generated-withdraw operations load the application first and
  return the same stable `404` for foreign and unknown IDs before mutation.
- The validated Bearer token is forwarded to Application Tracker and Document
  Store cleanup calls; downstream application responses are ownership-checked.
- Application/user UUID path segments and controller audit events are redacted
  from Job Finder's application-operation logs; framework request logging is
  bounded so the pre-filter handler warning cannot emit a raw resource path.
- The validated Bearer token is forwarded by a per-request generated client to
  User Profile; mutable authentication state is not shared between requests.
- The validated Bearer token is forwarded by a separate per-request generated
  client to Job Service; mutable token state is not shared and the
  browser-supplied identity header is never relayed.
- Job Service independently verifies the signed access token and derives the
  search identity only from its subject.
- Job Service and User Profile clients are generated at build time from exact,
  checksum-protected producer contracts. Local JAR and `systemPath`
  dependencies have been removed.
- The complete generated Job Service response is returned without a duplicate
  hand-maintained response DTO.
- Integration tests cover valid, missing, malformed, expired, forged,
  unknown-key, wrong-algorithm, wrong-issuer, wrong-audience, and refresh-token
  cases, plus header spoofing, downstream identity propagation, cross-user
  application denial, non-enumerating unknown IDs, Bearer forwarding, and log
  path redaction.

## Target boundary

Only a verified JWT subject or a narrowly scoped, authenticated service
identity may establish the user. Job Finder must pass a trusted identity
downstream, reject caller-supplied ownership overrides, validate bounded
requests, apply an end-to-end deadline, and return a stable contract with
correlation metadata. Saving and application actions must call their owning
services using subject-aware APIs.

## Evidence required to close

- Clean-clone `mvn -B clean verify` and container build.
- Application Tracker APP-03 evidence for atomic subject-aware application
  access and direct-call rejection.
- Integration evidence for downstream timeout and structured downstream
  failures.
- Secret scan of every migrated ref and documented rotation/history decision.
- Load evidence that gateway timeouts fit inside the end-user latency budget.

This audit is a work queue input. It is not a beta-readiness approval.
