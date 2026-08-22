# Pinned downstream contracts

The Job Service and User Profile files are exact vendored copies of their
producer-owned contracts at the revisions recorded in the adjacent `SOURCE`
files. `SHA256SUMS` protects the reviewed bytes.

To update either contract, merge and verify the producer change first, copy the
exact source document, update its `SOURCE` record and checksum, then run
`scripts/test-contract-policy.sh`, `scripts/verify-contracts.sh` and
`mvn -B clean verify`. Never edit a vendored copy independently.

OpenAPI Generator 7.5.0 creates separate RestTemplate clients during Maven
`generate-sources`; generated sources and binaries are never committed.
Application Tracker and Document Store use repository-owned DTO/HTTP adapters,
so their previously declared generated JARs were unused and have been removed.
