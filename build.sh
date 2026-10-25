#!/usr/bin/env bash
# Builds J.A.R.V.I.S. APK with the raw Android toolchain (no Gradle needed).
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-$HOME/android-setup/sdk}
BUILD_TOOLS=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-35/android.jar
VOSK=$HOME/android-setup/vosk/ext/vosk-classes.jar
JNA=$HOME/android-setup/vosk/ext/jna-classes.jar
OUT=build

rm -rf $OUT
mkdir -p $OUT/gen $OUT/classes

# vosk models + voice pack live outside git; fetch when missing
if [ ! -d assets/vosk-model-small-en-us-0.15 ] || [ ! -d assets/vosk-model-spk-0.4 ] || [ ! -d assets/sounds ]; then
  bash scripts/fetch-models.sh
fi

echo "==> aapt2 compile"
$BUILD_TOOLS/aapt2 compile --dir res -o $OUT/res.zip

echo "==> aapt2 link"
$BUILD_TOOLS/aapt2 link \
    -o $OUT/base.apk \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    --version-code 9 \
    --version-name 1.2 \
    -A assets \
    --java $OUT/gen \
    --min-sdk-version 28 \
    --target-sdk-version 35 \
    $OUT/res.zip

echo "==> javac"
find src $OUT/gen -name "*.java" > $OUT/sources.txt
"$JAVA_HOME/bin/javac" \
    --release 8 \
    -classpath "$PLATFORM:$VOSK:$JNA" \
    -d $OUT/classes \
    @$OUT/sources.txt 2>&1 | sed '/^Note:/d'
TESTFS=$(ls $OUT/classes/com/stark/jarvis/*.class 2>/dev/null | head -1)
test -n "$TESTFS"

echo "==> d8 (app + vosk + jna)"
$BUILD_TOOLS/d8 --release \
    --lib "$PLATFORM" \
    --min-api 28 \
    --output $OUT \
    $(find $OUT/classes -name "*.class")

mkdir -p $OUT/ext
$BUILD_TOOLS/d8 --release \
    --lib "$PLATFORM" \
    --min-api 28 \
    --output $OUT/ext \
    "$VOSK" "$JNA"
mv $OUT/ext/classes.dex $OUT/classes2.dex

# native libs (vosk + jna jnidispatch) for ARM devices
mkdir -p $OUT/lib/arm64-v8a $OUT/lib/armeabi-v7a $OUT/stage
unzip -oq $HOME/android-setup/vosk/vosk-android.aar "jni/arm64-v8a/libvosk.so" "jni/armeabi-v7a/libvosk.so" -d $OUT/stage
unzip -oq $HOME/android-setup/vosk/jna.aar "jni/arm64-v8a/libjnidispatch.so" "jni/armeabi-v7a/libjnidispatch.so" -d $OUT/stage
cp -r $OUT/stage/jni/* $OUT/lib/ && rm -rf $OUT/stage

echo "==> pack dex + libs"
(cd $OUT && zip -q base.apk classes.dex classes2.dex lib/arm64-v8a/libvosk.so lib/arm64-v8a/libjnidispatch.so lib/armeabi-v7a/libvosk.so lib/armeabi-v7a/libjnidispatch.so)

echo "==> zipalign"
$BUILD_TOOLS/zipalign -f -p 4 $OUT/base.apk $OUT/aligned.apk

echo "==> sign"
if [ ! -f jarvis.keystore ]; then
  echo "ERROR: jarvis.keystore not found."
  echo "       APK updates must share one signature; build.sh refuses to invent a new key."
  echo "       Copy the original keystore (password inside this file) into the repo root first."
  exit 1
fi
$BUILD_TOOLS/apksigner sign --ks jarvis.keystore --ks-pass pass:jarvis123 \
    --key-pass pass:jarvis123 --out $OUT/JARVIS.apk $OUT/aligned.apk
$BUILD_TOOLS/apksigner verify --verbose $OUT/JARVIS.apk | head -3

echo "==> done: $(ls -lh $OUT/JARVIS.apk | awk '{print $5}')  $OUT/JARVIS.apk"
