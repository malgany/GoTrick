#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
export JAVA_HOME="${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f -- "$(command -v javac)")")")}"
export GRADLE_USER_HOME="$PWD/.tools/gradle-cache"
exec .tools/gradle/bin/gradle :plugin:buildPlugin :plugin:test :plugin:verifyPluginStructure :plugin:verifyPluginProjectConfiguration --console=plain --no-daemon "$@"
