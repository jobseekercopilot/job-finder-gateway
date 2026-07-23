#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

copy_contracts() {
    local destination="$1"
    mkdir -p "$destination"
    cp "$repository_root/src/main/openapi/job-service.yaml" \
       "$repository_root/src/main/openapi/user-profile-service.json" \
       "$repository_root/src/main/openapi/job-service.SOURCE" \
       "$repository_root/src/main/openapi/user-profile-service.SOURCE" \
       "$repository_root/src/main/openapi/SHA256SUMS" \
       "$destination/"
}

"$repository_root/scripts/verify-contracts.sh" "$repository_root/src/main/openapi" >/dev/null

copy_contracts "$temporary_dir/missing"
rm "$temporary_dir/missing/user-profile-service.json"
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/missing" >/dev/null 2>&1; then
    echo "contract policy negative test accepted a missing producer contract" >&2
    exit 1
fi

copy_contracts "$temporary_dir/drift"
jq '.info.description = "unreviewed drift"' "$temporary_dir/drift/user-profile-service.json" > "$temporary_dir/drift/changed.json"
mv "$temporary_dir/drift/changed.json" "$temporary_dir/drift/user-profile-service.json"
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/drift" >/dev/null 2>&1; then
    echo "contract policy negative test accepted checksum drift" >&2
    exit 1
fi

copy_contracts "$temporary_dir/profile-operation"
jq 'del(.paths["/api/profiles/me"].get)' "$temporary_dir/profile-operation/user-profile-service.json" > "$temporary_dir/profile-operation/changed.json"
mv "$temporary_dir/profile-operation/changed.json" "$temporary_dir/profile-operation/user-profile-service.json"
(cd "$temporary_dir/profile-operation" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/profile-operation" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of profile lookup" >&2
    exit 1
fi

copy_contracts "$temporary_dir/job-operation"
sed 's/operationId: searchJobs/operationId: removedSearchJobs/' \
    "$temporary_dir/job-operation/job-service.yaml" > "$temporary_dir/job-operation/changed.yaml"
mv "$temporary_dir/job-operation/changed.yaml" "$temporary_dir/job-operation/job-service.yaml"
(cd "$temporary_dir/job-operation" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/job-operation" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of job search" >&2
    exit 1
fi

copy_contracts "$temporary_dir/job-boundary"
sed 's/^        selectedProviders:$/        removedSelectedProviders:/' \
    "$temporary_dir/job-boundary/job-service.yaml" > "$temporary_dir/job-boundary/changed.yaml"
mv "$temporary_dir/job-boundary/changed.yaml" "$temporary_dir/job-boundary/job-service.yaml"
(cd "$temporary_dir/job-boundary" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/job-boundary" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of the provider-selection boundary" >&2
    exit 1
fi

echo "contract policy tests passed"
