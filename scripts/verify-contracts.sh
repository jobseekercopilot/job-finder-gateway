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
grep -Fx 'revision=f370d43f07f0d31e1c170eb74019b5d99465d7be' "$job_source" >/dev/null
grep -Fx 'path=api/openapi.yaml' "$job_source" >/dev/null
grep -Fx 'sha256=d37adc1b953ef68419461c72cc1982bf7da1822d8283a4d734e41ef14ee602e0' "$job_source" >/dev/null

test "$(wc -l < "$profile_source" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/user-profile-service' "$profile_source" >/dev/null
grep -Fx 'revision=37b98fbf3cdc5ded8ee645b79eec068fa5684a0e' "$profile_source" >/dev/null
grep -Fx 'path=api/openapi.json' "$profile_source" >/dev/null
grep -Fx 'sha256=ffaaa16a169ab11d864f82440be9fcc7d5df2d4f2d63a3525d40bda497ea6598' "$profile_source" >/dev/null

grep -Fx '  /api/jobs/search:' "$job_contract" >/dev/null
grep -Fx '      operationId: searchJobs' "$job_contract" >/dev/null
grep -Fx '      - bearerAuth: []' "$job_contract" >/dev/null
grep -Fx '      scheme: bearer' "$job_contract" >/dev/null
if grep -F 'name: X-User-Id' "$job_contract" >/dev/null; then
    echo "contract policy: Job Service contract reintroduced raw identity header" >&2
    exit 1
fi
for required_field in homeLocation selectedProviders resultsByTargetRole providerResults canonicalJobId; do
    grep -Fx "        $required_field:" "$job_contract" >/dev/null
done

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.components.securitySchemes.bearerAuth.scheme == "bearer") and
    (.paths["/api/profiles/me"].get.operationId == "getMyProfile") and
    (.paths["/api/profiles/me"].get.security | any(has("bearerAuth"))) and
    (.components.schemas.UserProfile.properties.userId.readOnly == true)
' "$profile_contract" >/dev/null

echo "contract policy: pinned Job Service and User Profile sources are present, intact and compatible"
