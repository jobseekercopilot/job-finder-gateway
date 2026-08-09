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
grep -Fx 'revision=f5a7ac1da162ceb3e7275c7a5b91658f4e709322' "$job_source" >/dev/null
grep -Fx 'path=api/openapi.yaml' "$job_source" >/dev/null
grep -Fx 'sha256=dc233e8e62a46c5022dfb074b8567e61b56a4fad0f15e98a57daf3caf1dfa81e' "$job_source" >/dev/null

test "$(wc -l < "$profile_source" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/user-profile-service' "$profile_source" >/dev/null
grep -Fx 'revision=4e8c7c4c53bc89e97c76136d5633bbf1b93c3d55' "$profile_source" >/dev/null
grep -Fx 'path=api/openapi.json' "$profile_source" >/dev/null
grep -Fx 'sha256=1e74f08ad044a144df2bafad3ef22d3b1ffde429dbdcc352cb5f1bdab5b87cc5' "$profile_source" >/dev/null

grep -Fx '  /api/jobs/search:' "$job_contract" >/dev/null
grep -Fx '  /api/jobs/{provider}/{externalJobId}:' "$job_contract" >/dev/null
grep -Fx '  /api/jobs/saved:' "$job_contract" >/dev/null
grep -Fx '  /api/jobs/saved/{savedJobId}:' "$job_contract" >/dev/null
grep -Fx '      operationId: searchJobs' "$job_contract" >/dev/null
grep -Fx '      operationId: getJobDetails' "$job_contract" >/dev/null
for required_operation in save list get unsave; do
    grep -Fx "      operationId: $required_operation" "$job_contract" >/dev/null
done
grep -Fx '      - bearerAuth: []' "$job_contract" >/dev/null
test "$(grep -c '^      - bearerAuth: \[\]$' "$job_contract")" = 6
grep -Fx '      scheme: bearer' "$job_contract" >/dev/null
grep -Fx '  version: 2.2.0' "$job_contract" >/dev/null
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
    commuteAssessment \
    commuteTravelModes \
    maximumDrivingMinutes \
    maximumTransitMinutes \
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

target_role_schema="$(
    awk '
        $0 == "    TargetRoleJobResults:" { capture = 1 }
        capture && seen && $0 ~ /^    [[:alnum:]][[:alnum:]]*:/ { exit }
        capture { print; seen = 1 }
    ' "$job_contract"
)"
for required_role_field in \
    targetRole \
    jobs \
    totalResults \
    page \
    pageSize \
    totalPages \
    providerResults \
    searchStatus \
    matchingStatus; do
    grep -Fx "        $required_role_field:" <<<"$target_role_schema" >/dev/null
    grep -Fx "      - $required_role_field" <<<"$target_role_schema" >/dev/null
done
grep -Fx '          - UNAVAILABLE' <<<"$target_role_schema" >/dev/null

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
