#!/usr/bin/env bash
# Usage: scripted-impl.sh <publish_local: yes|no> <version> [tests...]
#
# When publish_local=yes: publishes the plugin into ~/.ivy2/local with sbt's
# expected sbt-plugin Ivy layout (scala_2.12/sbt_1.0/...). sbt's default
# `local` resolver picks it up — no repo.config required.
#
# When publish_local=no: assumes the artifact is already on Central. For
# -SNAPSHOT versions, plugins.sbt registers the snapshots resolver via the
# `plugin.snapshots` system property.
set -euo pipefail

publish_local="$1"; shift
version="$1"; shift
tests=("$@")

plugin_org="org.polyvariant"
plugin_name="scala-cli-sbt-plugin-poc"
sbt_version="1.10.7"
work_dir=".scripted-work"
plugin_dir="plugin"
sbt_test_root="sbt-test"

rm -rf "$work_dir"
mkdir -p "$work_dir/ivy" "$work_dir/sbt-boot" "$work_dir/staging"

if [[ "$publish_local" == "yes" ]]; then
  echo "[scripted-impl] publishing ${plugin_org}:${plugin_name}:${version} to ${work_dir}/ivy/local"

  # Stage the publish in a scratch dir so we can move it into the right
  # sbt-plugin Ivy layout afterwards. We isolate ivy home in $work_dir/ivy
  # rather than touching ~/.ivy2/local.
  staging="$(pwd)/${work_dir}/staging"
  scala-cli --power publish "$plugin_dir" \
    --workspace . \
    --publish-repo "file://${staging}" \
    --ivy2-local-like \
    --module-name "$plugin_name" \
    --project-version "$version" \
    --signer none

  src="${staging}/${plugin_org}/${plugin_name}/${version}"
  dst="$(pwd)/${work_dir}/ivy/local/${plugin_org}/${plugin_name}/scala_2.12/sbt_1.0/${version}"
  rm -rf "$dst"
  mkdir -p "$(dirname "$dst")"
  mv "$src" "$dst"

  # Patch ivy.xml to add the sbt-plugin cross-attributes that --ivy2-local-like
  # doesn't emit on its own. sbt's resolver requires these to recognize the
  # artifact as a 2.12 / sbt 1.0 plugin. Regenerate checksums afterwards.
  python3 - "$dst/ivys/ivy.xml" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p).read()
s = re.sub(
    r'(<info\b[^>]*?revision="[^"]*")(\s)',
    r'\1 e:scalaVersion="2.12" e:sbtVersion="1.0"\2',
    s, count=1,
)
open(p, 'w').write(s)
PY
  ivy_xml="$dst/ivys/ivy.xml"
  shasum -a 1 "$ivy_xml" | awk '{print $1}' > "$ivy_xml.sha1"
  md5 -q "$ivy_xml" 2>/dev/null > "$ivy_xml.md5" || md5sum "$ivy_xml" | awk '{print $1}' > "$ivy_xml.md5"
  echo "[scripted-impl] published to $dst"
fi

# Tell the scripted test's plugins.sbt to add the Central snapshots resolver
# when we're verifying a -SNAPSHOT version published remotely.
extra_props=()
if [[ "$publish_local" == "no" && "$version" == *-SNAPSHOT ]]; then
  extra_props=("plugin.snapshots=true")
fi

sbt_launch=$(cs fetch "org.scala-sbt:sbt-launch:${sbt_version}" | grep "/sbt-launch-${sbt_version}.jar$" | head -1)
echo "[scripted-impl] using sbt-launch: $sbt_launch"

scala-cli run build/scripted.scala build/SbtHandler.scala -- \
  "$(pwd)/${sbt_test_root}" \
  "$sbt_launch" \
  "$(pwd)/${work_dir}/ivy" \
  "$(pwd)/${work_dir}/sbt-boot" \
  "$version" \
  "${extra_props[@]+"${extra_props[@]}"}" \
  -- \
  "${tests[@]+"${tests[@]}"}"
