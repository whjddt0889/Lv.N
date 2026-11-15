#!/usr/bin/env bash
# Android Studio 없이 APK 를 만드는 스크립트 (aapt2 · D8 · apksig 는 bundletool jar 에 포함된 것을 사용).
# 필요: JDK 17+, python3, TOOLS 폴더에 bundletool.jar / android.jar(API 34) / aapt2
# 사용: TOOLS=/path/to/tools KEYSTORE=lvn.p12 KEYPASS=... ./tools/build_apk.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS:?TOOLS 폴더를 지정하세요}"
AJ="$TOOLS/android.jar"; BT="$TOOLS/bundletool.jar"; AAPT2="$TOOLS/aapt2"; LMF="$TOOLS/lmf"
OUT="$ROOT/build/out"; APP="$ROOT/app/src/main"
NAME="${APK_NAME:-우희준_LvN_release.apk}"
rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/5] 리소스 컴파일"
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res/res.zip"
# Gradle(AGP 8)은 namespace 를 쓰므로 manifest 에 package 가 없다 → 스크립트 빌드용으로만 주입
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lvn.app">#' \
  "$APP/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$AAPT2" link -I "$AJ" --manifest "$OUT/AndroidManifest.xml" -o "$OUT/base.apk" \
  --min-sdk-version 28 --target-sdk-version 34 --version-code 1 --version-name 1.0.0 \
  --java "$OUT/gen" "$OUT/res/res.zip"

echo "[2/5] Java 컴파일"
find "$APP/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$AJ:$LMF" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[3/5] DEX 변환 (D8)"
java -cp "$BT" com.android.tools.r8.D8 --release --min-api 28 --lib "$AJ" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[4/5] APK 조립 · 정렬"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/unaligned.apk" classes.dex)
python3 "$ROOT/tools/zipalign.py" "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[5/5] 서명 (v1+v2+v3)"
mkdir -p "$OUT/signer"
javac -nowarn -cp "$BT" -d "$OUT/signer" "$ROOT/tools/SignApk.java"
mkdir -p "$ROOT/apk"
java -cp "$BT:$OUT/signer" SignApk "$OUT/aligned.apk" "$ROOT/apk/$NAME" "${KEYSTORE:?}" "${KEYPASS:?}" "${KEYALIAS:-lvn}"
ls -la "$ROOT/apk/$NAME"
