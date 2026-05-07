set shell := ["bash", "-cu"]

plugin_org    := "org.polyvariant"
plugin_name   := "scala-cli-sbt-plugin-poc"
plugin_module := plugin_name + "_2.12_1.0"
sbt_version   := "1.10.7"
work_dir      := ".scripted-work"
plugin_dir    := "plugin"
sbt_test_root := "sbt-test"

# Run scripted tests against the working tree (publishes plugin into a scratch m2 first).
scripted *tests:
    #!/usr/bin/env bash
    set -euo pipefail
    bash build/scripted-impl.sh yes "${PLUGIN_VERSION:-0.0.0-SNAPSHOT}" {{tests}}

# Run scripted tests against an already-published artifact (used by verify-release CI).
scripted-released *tests:
    #!/usr/bin/env bash
    set -euo pipefail
    : "${PLUGIN_VERSION:?PLUGIN_VERSION must be set}"
    bash build/scripted-impl.sh no "$PLUGIN_VERSION" {{tests}}

# Publish to Maven Central. Pass --signer/--gpg-key via extra args:
#   just publish --signer gpg --gpg-key <KEY_ID>
publish *args:
    scala-cli --power publish {{plugin_dir}} \
      --workspace . \
      --module-name {{plugin_module}} \
      {{args}}
