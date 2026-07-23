# Contributing

The default integration branch is `develop`. Create a short-lived branch from
`develop`, use the `feat/`, `fix/`, `docs/`, `test/`, or `chore/` prefix, and
open a pull request back to `develop`.

Before requesting review:

1. Run `mvn -B clean verify`.
2. Build the container from a clean checkout.
3. Confirm that no credentials, environment files, generated clients, JARs,
   classes, `target` trees, logs, recordings, or provider data are staged.
4. Update the versioned API contract and consumer evidence for interface
   changes.
5. Link the Job Search Beta Readiness issue and record rollout or rollback
   considerations.

At least one authorised maintainer must review a pull request. Protected
branches must not be force-pushed. Releases are promoted from tested commits;
`main` is not the active development branch during beta hardening.
