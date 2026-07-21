#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"

echo "==> Notepad: build APK da terminale"
echo

# 1. Java
if ! command -v java >/dev/null 2>&1; then
  echo "Java non trovato."
  echo "Installalo con:"
  echo "  sudo apt update && sudo apt install -y openjdk-17-jdk"
  exit 1
fi

echo "Java OK: $(java -version 2>&1 | head -1)"

# 2. Gradle wrapper jar
if [ ! -f "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "==> Download Gradle Wrapper..."
  curl -fsSL \
    "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" \
    -o "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.jar"
fi

chmod +x "$PROJECT_DIR/gradlew"

# 3. Android SDK
if [ ! -d "$SDK_DIR" ]; then
  echo "==> Download Android SDK command-line tools..."
  mkdir -p "$SDK_DIR/cmdline-tools"
  TMP_ZIP="/tmp/${CMDLINE_TOOLS_ZIP}"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$TMP_ZIP"
  unzip -qo "$TMP_ZIP" -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -f "$TMP_ZIP"
fi

export ANDROID_HOME="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

if ! command -v sdkmanager >/dev/null 2>&1; then
  echo "Errore: sdkmanager non trovato in $SDK_DIR"
  exit 1
fi

echo "==> Installazione componenti Android SDK (solo la prima volta)..."
yes | sdkmanager --licenses >/dev/null || true
sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

# 4. local.properties
cat > "$PROJECT_DIR/local.properties" <<EOF
sdk.dir=$SDK_DIR
EOF

# 5. Build APK debug
echo
echo "==> Compilazione APK..."
cd "$PROJECT_DIR"
./gradlew assembleDebug --no-daemon

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo
echo "Fatto!"
echo "APK generato qui:"
echo "  $APK_PATH"
echo
echo "Per installarlo sul telefono (con USB debugging attivo):"
echo "  adb install -r \"$APK_PATH\""
