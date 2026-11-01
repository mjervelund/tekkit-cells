#!/usr/bin/env bash
# Put the AE2 and GuideME jars this add-on builds against into libs/.
#
#   scripts/fetch-libs.sh                 # AE2 26.3.1-alpha + GuideME release jars from Modrinth (sha512-checked)
#   AE2_JAR=<path> scripts/fetch-libs.sh  # use a locally built AE2 jar instead (same version)
set -euo pipefail
[[ -n "${AE2_JAR:-}" ]] && AE2_JAR="$(realpath "$AE2_JAR")"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
mkdir -p libs

fetch() { # url sha512 name
  local out="libs/$3"
  if [[ ! -f "$out" ]] || ! echo "$2  $out" | sha512sum -c --quiet - 2>/dev/null; then
    curl -fsSL -o "$out.part" "$1"
    echo "$2  $out.part" | sha512sum -c --quiet -
    mv "$out.part" "$out"
  fi
}

if [[ -n "${AE2_JAR:-}" ]]; then
  cp "$AE2_JAR" libs/appliedenergistics2-26.3.1-alpha.jar
else
  fetch https://cdn.modrinth.com/data/XxWD5pD3/versions/PDPQ4WfW/appliedenergistics2-26.3.1-alpha.jar \
    0b2cd09cb2fc63c39e600d2989edb6c8ac2ceac396cbb15224aadd87c555ffa8465503af165a02df73646f1e0cc2f3a6d711f669fa7ba9a5dd9fe88d0b9533cc \
    appliedenergistics2-26.3.1-alpha.jar
fi
fetch https://cdn.modrinth.com/data/Ck4E7v7R/versions/KCZu3q1I/guideme-26.3.6-alpha.jar \
  0ab2eb604a6c132f9b9eb46b55419bacf12969750e9cc4510ed3f496df3e5d48d67034378c2f69870ee37058a714f886c06d71a01939ae70fb339fa2b1da52a2 \
  guideme-26.3.6-alpha.jar
ls -l libs
