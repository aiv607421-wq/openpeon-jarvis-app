#!/usr/bin/env bash
# Installs JDK 17 + Android SDK build-tools 35 + vosk-android/jna jars into $HOME/android-setup.
# Run once per machine before build.sh.
set -euo pipefail
DV=$HOME/android-setup
mkdir -p $DV && cd $DV

if [ ! -d "jdk-17.0.13+11" ]; then
  curl -sL -o jdk17.tar.gz https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz
  tar xzf jdk17.tar.gz
fi

if [ ! -f sdk/build-tools/35.0.0/aapt2 ]; then
  curl -sL -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  rm -rf sdk && unzip -q -o cmdtools.zip -d sdk
  export JAVA_HOME=$DV/jdk-17.0.13+11
  export PATH=$JAVA_HOME/bin:$PATH
  yes | sdk/cmdline-tools/bin/sdkmanager --sdk_root=$DV/sdk "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1
fi

if [ ! -f vosk/ext/vosk-classes.jar ]; then
  mkdir -p vosk/ext
  cd vosk
  curl -sL -o vosk-android.aar https://repo1.maven.org/maven2/com/alphacephei/vosk-android/0.3.75/vosk-android-0.3.75.aar
  curl -sL -o jna.aar https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.18.1/jna-5.18.1.aar
  unzip -oq vosk-android.aar classes.jar -d va && mv va/classes.jar ext/vosk-classes.jar && rm -rf va
  unzip -oq jna.aar classes.jar -d ja && mv ja/classes.jar ext/jna-classes.jar && rm -rf ja
  cd ..
fi

echo "==> toolchain ready in $DV"
