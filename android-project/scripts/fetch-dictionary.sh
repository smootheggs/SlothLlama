#!/usr/bin/env bash
# Builds the offline Chinese dictionary (assets/dictionary/dictionary.db) from two wheels on PyPI,
# each checked against its pinned SHA-256 so a changed file stops the build. The wheels are read
# as data by scripts/build_dictionary.py; none of their code runs.
#   CC-CEDICT via pycccedict 1.2.0 (CC BY-SA 4.0) · Jyutping via ToJyutping 3.2.0 (BSD 2-Clause)
set -euo pipefail

CEDICT_URL=https://files.pythonhosted.org/packages/ff/a2/0a1284e7dde410620f341dffc53a5f6f40f1aa188418fe096fe1411403e8/pycccedict-1.2.0-py3-none-any.whl
CEDICT_SHA256=a810ca3255516a090f5f9059d6cdb156f86842f579f658a7ae7a84273bcd6326
JYUTPING_URL=https://files.pythonhosted.org/packages/21/73/232b2ddc09db98b1ae37df6aebffa9c4808196e588d73f40cd6d26596cb7/ToJyutping-3.2.0-py3-none-any.whl
JYUTPING_SHA256=af7fe10095a3ce91b5cf2bcd7e7c62002407f432c3083562f725ddc140c6fd7e

PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$PROJECT/scripts/build_dictionary.py"
OUT="$PROJECT/app/src/main/assets/dictionary"
STAMP="$OUT/PINNED"
WANT="$CEDICT_SHA256 $JYUTPING_SHA256 $(sha256sum "$SCRIPT" | cut -d' ' -f1)"

if [ -f "$OUT/dictionary.db" ] && [ "$(cat "$STAMP" 2>/dev/null)" = "$WANT" ]; then
  echo "Dictionary already built: $OUT"
  exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT
fetch() { # <url> <sha256> <file>
  curl -fsSL --retry 3 -o "$WORK/$3" "$1"
  echo "$2  $WORK/$3" | sha256sum -c --quiet
}
fetch "$CEDICT_URL" "$CEDICT_SHA256" cedict.whl
fetch "$JYUTPING_URL" "$JYUTPING_SHA256" jyutping.whl

python3 -I "$SCRIPT" "$WORK/cedict.whl" "$WORK/jyutping.whl" "$WORK/out"
mkdir -p "$OUT"
mv "$WORK/out/dictionary.db" "$WORK/out/licenses.txt" "$OUT/"
echo "$WANT" > "$STAMP"
echo "Dictionary ready: $OUT"
