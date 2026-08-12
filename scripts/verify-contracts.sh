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
grep -Fx 'revision=1768328ca0b4d906db6981f1dd7a2fa2e28c58a0' "$job_source" >/dev/null
grep -Fx 'path=api/openapi.yaml' "$job_source" >/dev/null
grep -Fx 'sha256=057988ddce0742e514b765af85ed18dae2c5eb8b0f8f206e85b3e054f64e106d' "$job_source" >/dev/null

test "$(wc -l < "$profile_source" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/user-profile-service' "$profile_source" >/dev/null
grep -Fx 'revision=13ced1c7e9138d2259b5251f36c0a419270b8976' "$profile_source" >/dev/null
grep -Fx 'path=api/openapi.json' "$profile_source" >/dev/null
grep -Fx 'sha256=f81c90a801ff877930304417be9bc7cdf2c39b2f2c61dc212b43b1cdf72dba75' "$profile_source" >/dev/null

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
grep -Fx '  version: 2.4.0' "$job_contract" >/dev/null
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
    specialistType \
    locations \
    apprenticeshipDetails \
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
    candidateProfile \
    matchAssessment \
    discoveryAssessment \
    freshness \
    qualitySummary \
    dataProvenance \
    hardGateReasons \
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

for required_schema in \
    CandidateProfile \
    CandidateRole \
    CandidateQualification \
    JobDiscoveryAssessment \
    MatchAssessment \
    MatchScoreComponent \
    MatchReason \
    ProviderDataProvenance \
    SearchFreshness \
    SearchQualitySummary; do
    grep -Fx "    $required_schema:" "$job_contract" >/dev/null
done

for discovery_value in CLOSED EXPIRED PAID_TRAINING MISMATCHED; do
    grep -Fx "          - $discovery_value" "$job_contract" >/dev/null
done

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.components.securitySchemes.bearerAuth.scheme == "bearer") and
    (.paths["/api/profiles/me"].get.operationId == "getMyProfile") and
    (.paths["/api/profiles/me"].get.security | any(has("bearerAuth"))) and
    (.components.schemas.UserProfile.properties.userId.readOnly == true)
' "$profile_contract" >/dev/null

echo "contract policy: pinned Job Service and User Profile sources are present, intact and compatible"
