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

copy_contracts "$temporary_dir/job-paging"
sed 's/^        sort:$/        removedSort:/' \
    "$temporary_dir/job-paging/job-service.yaml" \
    > "$temporary_dir/job-paging/changed.yaml"
mv "$temporary_dir/job-paging/changed.yaml" \
    "$temporary_dir/job-paging/job-service.yaml"
(cd "$temporary_dir/job-paging" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/job-paging" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of the paging sort boundary" >&2
    exit 1
fi

copy_contracts "$temporary_dir/target-role-paging"
awk '
    $0 == "    TargetRoleJobResults:" { in_target_role = 1 }
    in_target_role && $0 == "        totalResults:" {
        print "        removedTotalResults:"
        in_target_role = 0
        next
    }
    { print }
' "$temporary_dir/target-role-paging/job-service.yaml" \
    > "$temporary_dir/target-role-paging/changed.yaml"
mv "$temporary_dir/target-role-paging/changed.yaml" \
    "$temporary_dir/target-role-paging/job-service.yaml"
(cd "$temporary_dir/target-role-paging" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/target-role-paging" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of target-role paging metadata" >&2
    exit 1
fi

copy_contracts "$temporary_dir/job-result-state"
sed 's/^        searchStatus:$/        removedSearchStatus:/' \
    "$temporary_dir/job-result-state/job-service.yaml" \
    > "$temporary_dir/job-result-state/changed.yaml"
mv "$temporary_dir/job-result-state/changed.yaml" \
    "$temporary_dir/job-result-state/job-service.yaml"
(cd "$temporary_dir/job-result-state" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/job-result-state" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of the aggregate search status" >&2
    exit 1
fi

copy_contracts "$temporary_dir/job-provenance"
sed 's/^        dataProvenance:$/        removedDataProvenance:/' \
    "$temporary_dir/job-provenance/job-service.yaml" \
    > "$temporary_dir/job-provenance/changed.yaml"
mv "$temporary_dir/job-provenance/changed.yaml" \
    "$temporary_dir/job-provenance/job-service.yaml"
(cd "$temporary_dir/job-provenance" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/job-provenance" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of provider data provenance" >&2
    exit 1
fi

copy_contracts "$temporary_dir/job-authentication"
sed 's/^      scheme: bearer$/      scheme: removed/' \
    "$temporary_dir/job-authentication/job-service.yaml" \
    > "$temporary_dir/job-authentication/changed.yaml"
mv "$temporary_dir/job-authentication/changed.yaml" \
    "$temporary_dir/job-authentication/job-service.yaml"
(cd "$temporary_dir/job-authentication" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/job-authentication" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of Job Service Bearer authentication" >&2
    exit 1
fi

copy_contracts "$temporary_dir/saved-operation"
sed 's/operationId: save/operationId: removedSave/' \
    "$temporary_dir/saved-operation/job-service.yaml" \
    > "$temporary_dir/saved-operation/changed.yaml"
mv "$temporary_dir/saved-operation/changed.yaml" \
    "$temporary_dir/saved-operation/job-service.yaml"
(cd "$temporary_dir/saved-operation" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/saved-operation" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of saved-job creation" >&2
    exit 1
fi

copy_contracts "$temporary_dir/saved-identity"
sed 's/^        contentSha256:$/        removedContentSha256:/' \
    "$temporary_dir/saved-identity/job-service.yaml" \
    > "$temporary_dir/saved-identity/changed.yaml"
mv "$temporary_dir/saved-identity/changed.yaml" \
    "$temporary_dir/saved-identity/job-service.yaml"
(cd "$temporary_dir/saved-identity" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/saved-identity" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of saved-job content identity" >&2
    exit 1
fi

copy_contracts "$temporary_dir/saved-authentication"
awk '
    $0 == "  /api/jobs/saved:" { in_saved_jobs = 1 }
    $0 == "  /api/jobs/saved/{savedJobId}:" { in_saved_jobs = 0 }
    in_saved_jobs && !removed && $0 == "      - bearerAuth: []" {
        print "      - removedBearerAuth: []"
        removed = 1
        next
    }
    { print }
' "$temporary_dir/saved-authentication/job-service.yaml" \
    > "$temporary_dir/saved-authentication/changed.yaml"
mv "$temporary_dir/saved-authentication/changed.yaml" \
    "$temporary_dir/saved-authentication/job-service.yaml"
(cd "$temporary_dir/saved-authentication" && sha256sum job-service.yaml user-profile-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/saved-authentication" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of saved-job Bearer authentication" >&2
    exit 1
fi

echo "contract policy tests passed"
