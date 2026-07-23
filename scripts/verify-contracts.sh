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
grep -Fx 'revision=7f03f24f130a4657dcbaad2266db69b115d14951' "$job_source" >/dev/null
grep -Fx 'path=api/openapi.yaml' "$job_source" >/dev/null
grep -Fx 'sha256=1d7ec8ed4342540c0fc0371242d093c54f38ce3faa87ce0d890a84330664f38c' "$job_source" >/dev/null

test "$(wc -l < "$profile_source" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/user-profile-service' "$profile_source" >/dev/null
grep -Fx 'revision=37b98fbf3cdc5ded8ee645b79eec068fa5684a0e' "$profile_source" >/dev/null
grep -Fx 'path=api/openapi.json' "$profile_source" >/dev/null
grep -Fx 'sha256=ffaaa16a169ab11d864f82440be9fcc7d5df2d4f2d63a3525d40bda497ea6598' "$profile_source" >/dev/null

grep -Fx '  /api/jobs/search:' "$job_contract" >/dev/null
grep -Fx '      operationId: searchJobs' "$job_contract" >/dev/null
grep -F 'name: X-User-Id' "$job_contract" >/dev/null
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
