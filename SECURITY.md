# Security policy

Do not report vulnerabilities, leaked credentials, or personal data in a
public issue. Use the repository's private GitHub Security Advisory channel or
contact an authorised Job Seeker Copilot maintainer directly.

Revoke and rotate a suspected credential before relying on source cleanup.
Include the affected commit or endpoint, impact, reproduction steps, and any
known containment action. Never include live tokens, provider payloads, user
profiles, CVs, application data, or session recordings in a report.

Job Finder forwards the original validated Bearer access token to User Profile,
Job Service, Application Tracker and generated-document cleanup only through
request-scoped or immutable HTTP state. It never forwards `X-User-Id` as an
identity assertion. Downstream services must independently validate the token
and bind their resource access to its subject.
