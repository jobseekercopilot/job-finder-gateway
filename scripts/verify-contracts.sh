#!/usr/bin/env bash
set -euo pipefail

contract_dir="${1:-src/main/openapi}"
job_contract="$contract_dir/job-service.yaml"
profile_contract="$contract_dir/user-profile-service.json"
job_source="$contract_dir/job-service.SOURCE"
profile_source="$contract_dir/user-profile-service.SOURCE"
manifest="$contract_dir/SHA256SUMS"

for required_file in "$job_contract" "$profile_contract" "$job_source" "$profile_source" "$manifest"; do
    if [[ ! -f "$required_file" || -L "$required_file" ]]; then
        echo "contract policy: required regular file is missing or is a symlink: $required_file" >&2
        exit 1
    fi
done

(
    cd "$contract_dir"
    sha256sum --check --strict SHA256SUMS
)

test "$(wc -l < "$job_source" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/job-service' "$job_source" >/dev/null
grep -Fx 'revision=2cce83ddc057967b7a20d43c970499bffe0dd31f' "$job_source" >/dev/null
grep -Fx 'path=api/openapi.yaml' "$job_source" >/dev/null
grep -Fx 'sha256=3d3af14c64393df44b605226bb5950bc442ed901f43f12418f65a7b5d80408ce' "$job_source" >/dev/null

test "$(wc -l < "$profile_source" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/user-profile-service' "$profile_source" >/dev/null
grep -Fx 'revision=37b98fbf3cdc5ded8ee645b79eec068fa5684a0e' "$profile_source" >/dev/null
grep -Fx 'path=api/openapi.json' "$profile_source" >/dev/null
grep -Fx 'sha256=ffaaa16a169ab11d864f82440be9fcc7d5df2d4f2d63a3525d40bda497ea6598' "$profile_source" >/dev/null

grep -Fx '  /api/jobs/search:' "$job_contract" >/dev/null
grep -Fx '  /api/jobs/saved:' "$job_contract" >/dev/null
grep -Fx '  /api/jobs/saved/{savedJobId}:' "$job_contract" >/dev/null
grep -Fx '      operationId: searchJobs' "$job_contract" >/dev/null
for required_operation in save list get unsave; do
    grep -Fx "      operationId: $required_operation" "$job_contract" >/dev/null
done
grep -Fx '      - bearerAuth: []' "$job_contract" >/dev/null
test "$(grep -c '^      - bearerAuth: \[\]$' "$job_contract")" = 5
grep -Fx '      scheme: bearer' "$job_contract" >/dev/null
grep -Fx '  version: 2.1.0' "$job_contract" >/dev/null
if grep -F 'name: X-User-Id' "$job_contract" >/dev/null; then
    echo "contract policy: Job Service contract reintroduced raw identity header" >&2
    exit 1
fi
for required_field in \
    homeLocation \
    selectedProviders \
    page \
    pageSize \
    sort \
    resultsByTargetRole \
    totalPages \
    providerResults \
    searchStatus \
    matchingStatus \
    canonicalSchemaVersion \
    canonicalJobId \
    employmentTypeCode \
    contractTypeCode \
    workplaceType \
    skills \
    experience \
    fieldProvenance \
    savedJobId \
    snapshotVersion \
    contentVersion \
    contentSha256 \
    capturedAt \
    sourceState \
    savedAt \
    updatedAt \
    job; do
    grep -Fx "        $required_field:" "$job_contract" >/dev/null
done

for sort in MOST_RELEVANT CLOSEST HIGHEST_SALARY NEWEST_POSTED \
    OLDEST_POSTED COMPANY_AZ JOB_TITLE_AZ; do
    grep -Fx "          - $sort" "$job_contract" >/dev/null
done

grep -Fx '    SavedJobResponse:' "$job_contract" >/dev/null
grep -Fx '    SavedJobPageResponse:' "$job_contract" >/dev/null
grep -Fx '          - EXPIRED_SNAPSHOT' "$job_contract" >/dev/null

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.components.securitySchemes.bearerAuth.scheme == "bearer") and
    (.paths["/api/profiles/me"].get.operationId == "getMyProfile") and
    (.paths["/api/profiles/me"].get.security | any(has("bearerAuth"))) and
    (.components.schemas.UserProfile.properties.userId.readOnly == true)
' "$profile_contract" >/dev/null

echo "contract policy: pinned Job Service and User Profile sources are present, intact and compatible"
