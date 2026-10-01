#!/usr/bin/env bash
#
# Publishes the current checkout to Maven Central, to the self-hosted Maven
# repository at https://maven.yamcs.org (a Google Cloud Storage bucket), or
# to both from a single build.
#
# Bucket layout:
#   releases/<group path>/<artifactId>/...
#   snapshots/<group path>/<artifactId>/...
#
# Publishing to maven.yamcs.org requires gcloud, logged in (`gcloud auth login`)
# with an account that can write to the bucket.
#
# In the release profile, the central-publishing-maven-plugin takes over the
# deploy phase to publish to Maven Central. For maven.yamcs.org, the deploy
# goal is instead invoked directly, to write to a local staging directory,
# which is then uploaded.
#
# Usage: ./publish.sh
#
# Environment:
#   MAVEN_BUCKET   bucket URL (default: gs://yamcs-maven)

set -euo pipefail

MAVEN_BUCKET="${MAVEN_BUCKET:-gs://yamcs-maven}"

cd "$(dirname "$0")"

evaluate() {
    mvn -q help:evaluate -Dexpression="$1" -DforceStdout
}

# Maven does not set <latest> in maven-metadata.xml (neither when creating it,
# nor when merging into it), so set it to the version just published, and
# update the checksums.
set_latest() {
    local file=$1 version=$2
    VERSION=$version perl -0pi -e '
        s#<latest>[^<]*</latest>#<latest>$ENV{VERSION}</latest># or
        s#(\s*)<versions>#$1<latest>$ENV{VERSION}</latest>$1<versions>#' "$file"
    for algorithm in md5 sha1 sha256 sha512; do
        printf '%s' "$(openssl dgst -$algorithm -r "$file" | cut -d' ' -f1)" > "$file.$algorithm"
    done
}

group_id=$(evaluate project.groupId)
artifact_id=$(evaluate project.artifactId)
version=$(evaluate project.version)

group_path="${group_id//.//}"
artifact_path="$group_path/$artifact_id"
version_path="$artifact_path/$version"

if [[ "$version" == *-SNAPSHOT ]]; then
    repo=snapshots
    central='Sonatype Snapshots'
else
    repo=releases
    central='Maven Central'
fi
remote="$MAVEN_BUCKET/$repo"

echo "Where do you want to publish $group_id:$artifact_id:$version?"
echo "  1) $central"
echo "  2) maven.yamcs.org"
echo "  3) Both"
echo "  4) Nowhere"
while true; do
    read -p "Choice [4]: " target
    target=${target:-4}
    if [[ $target =~ ^[1-4]$ ]]; then
        break
    fi
done
if [[ $target == 4 ]]; then
    exit 0
fi

if [[ $target == 2 || $target == 3 ]]; then
    # Check before building, as the same build may also publish to Maven Central
    if [[ $repo == releases ]]; then
        if existing=$(gcloud storage ls "$remote/$version_path/" 2>&1); then
            echo "$version is already published to maven.yamcs.org" >&2
            exit 1
        elif [[ $existing != *"matched no objects"* ]]; then
            echo "$existing" >&2
            exit 1
        fi
    fi

    staging=$(mktemp -d)
    trap 'rm -rf "$staging"' EXIT

    # Seed staging with the published metadata, so that Maven merges into it
    # rather than producing metadata with only this version. This includes the
    # group-level metadata, which maps the plugin prefix (yamcs:) to this plugin.
    # (For snapshots, the version-level metadata holds the build number.)
    # One checksum is enough for Maven to validate the metadata.
    seed_dirs=("$group_path" "$artifact_path")
    if [[ $repo == snapshots ]]; then
        seed_dirs+=("$version_path")
    fi
    for dir in "${seed_dirs[@]}"; do
        mkdir -p "$staging/$dir"
        for file in maven-metadata.xml maven-metadata.xml.sha1; do
            if ! result=$(gcloud storage cp "$remote/$dir/$file" "$staging/$dir/" 2>&1); then
                if [[ $result != *"matched no objects"* ]]; then
                    echo "$result" >&2
                    exit 1
                fi
            fi
        done
    done

    deploy_goal=(
        org.apache.maven.plugins:maven-deploy-plugin:3.1.2:deploy
        -DaltDeploymentRepository=yamcs-maven::file://$staging
        -Daether.checksums.algorithms=SHA-512,SHA-256,SHA-1,MD5
    )
fi

# A single build publishes to both, so the artifacts are identical
case $target in
    1) mvn -Prelease clean deploy ;;
    2) mvn -Prelease clean verify "${deploy_goal[@]}" ;;  # Not deploy, which would publish to Maven Central
    3) mvn -Prelease clean deploy "${deploy_goal[@]}" ;;
esac
if [[ $repo == releases && ($target == 1 || $target == 3) ]]; then
    echo 'Release the staging repository at https://central.sonatype.com'
fi

if [[ $target == 2 || $target == 3 ]]; then
    # Split what was deployed into artifacts and metadata
    artifacts=$staging/upload/artifacts
    metadata=$staging/upload/metadata
    mkdir -p "$artifacts/$artifact_path" "$metadata/$artifact_path" "$metadata/$group_path"
    mv "$staging/$version_path" "$artifacts/$artifact_path/"
    mv "$staging/$artifact_path"/maven-metadata.xml* "$metadata/$artifact_path/"
    set_latest "$metadata/$artifact_path/maven-metadata.xml" "$version"
    mv "$staging/$group_path"/maven-metadata.xml* "$metadata/$group_path/"
    if [[ $repo == snapshots ]]; then
        mkdir -p "$metadata/$version_path"
        mv "$artifacts/$version_path"/maven-metadata.xml* "$metadata/$version_path/"
    fi

    # Artifacts first, metadata last, so metadata never references
    # artifacts that are not uploaded yet.
    top=${group_path%%/*}
    gcloud storage cp -r --no-clobber \
        --cache-control='public, max-age=31536000, immutable' \
        "$artifacts/$top" "$remote/"
    gcloud storage cp -r \
        --cache-control='public, max-age=60' \
        "$metadata/$top" "$remote/"

    echo "Published to https://maven.yamcs.org/$repo/$version_path/"
fi
