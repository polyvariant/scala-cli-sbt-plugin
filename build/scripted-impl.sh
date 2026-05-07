#!/usr/bin/env bash
# Usage: scripted-impl.sh <publish_local: yes|no> <version> [tests...]
#
# Driver invoked from the justfile. Publishes the plugin locally (when asked),
# fetches sbt-launch via coursier, writes repo.config, and hands off to the
# scala-cli-based ScriptedRunner driver in build/scripted.scala.
set -euo pipefail

publish_local="$1"; shift
version="$1"; shift
tests=("$@")

plugin_org="org.polyvariant"
plugin_name="scala-cli-sbt-plugin-poc"
plugin_module="${plugin_name}_2.12_1.0"
sbt_version="1.10.7"
work_dir=".scripted-work"
plugin_dir="plugin"
sbt_test_root="${plugin_dir}/src/sbt-test"

rm -rf "$work_dir"
mkdir -p "$work_dir/ivy" "$work_dir/sbt-boot" "$work_dir/m2"

if [[ "$publish_local" == "yes" ]]; then
  echo "[scripted-impl] publishing ${plugin_org}:${plugin_module}:${version} to ${work_dir}/m2"
  scala-cli --power publish local "$plugin_dir" \
    --maven-local --m2-home "$(pwd)/${work_dir}/m2" \
    --module-name "$plugin_module" \
    --organization "$plugin_org" \
    --name "$plugin_name" \
    --project-version "$version" \
    --signer none
  scratch_line="  scratch-m2: file://$(pwd)/${work_dir}/m2"
else
  echo "[scripted-impl] expecting ${plugin_org}:${plugin_module}:${version} on Maven Central"
  scratch_line=""
fi

cat > "$work_dir/repo.config" <<EOF
[repositories]
  local
${scratch_line}
  maven-central
  sbt-plugin-releases: https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
EOF

sbt_launch=$(cs fetch "org.scala-sbt:sbt-launch:${sbt_version}" | grep "/sbt-launch-${sbt_version}.jar$" | head -1)
echo "[scripted-impl] using sbt-launch: $sbt_launch"

scala-cli run build/scripted.scala -- \
  "$(pwd)/${sbt_test_root}" \
  "$sbt_launch" \
  "$(pwd)/${work_dir}/repo.config" \
  "$(pwd)/${work_dir}/ivy" \
  "$(pwd)/${work_dir}/sbt-boot" \
  "$version" \
  "${tests[@]}"
