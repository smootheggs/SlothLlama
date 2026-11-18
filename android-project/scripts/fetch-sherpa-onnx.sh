#!/usr/bin/env bash
# Downloads the sherpa-onnx Android library (offline speech recognition) and checks it against
# the pinned SHA-256, so a changed or tampered file stops the build.
set -euo pipefail
VERSION=1.13.8
SHA256=633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/libs"
FILE="$DIR/sherpa-onnx-$VERSION.aar"
mkdir -p "$DIR"
if [ ! -f "$FILE" ] || ! echo "$SHA256  $FILE" | sha256sum -c --status; then
  curl -fL --retry 3 -o "$FILE.part" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"
  echo "$SHA256  $FILE.part" | sha256sum -c
  mv "$FILE.part" "$FILE"
fi
echo "sherpa-onnx $VERSION ready: $FILE"
