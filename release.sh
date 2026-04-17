#!/bin/bash
# Audioly — build signed release APK and install via adb
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

KEYSTORE="release.keystore"
KEYSTORE_PROPS="keystore.properties"
KEY_ALIAS="audioly"
KEY_PASS="audioly_release_key_2024"
APK_SRC="app/build/outputs/apk/release/app-release.apk"
APK_OUT="release.apk"

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  Audioly — Release Build + Install  ${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""

# ─── 1. Generate keystore on first run ───────────────────────────────────────
if [ ! -f "$KEYSTORE" ]; then
    echo -e "${YELLOW}No keystore found — generating one...${NC}"
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$KEY_PASS" \
        -keypass "$KEY_PASS" \
        -dname "CN=Audioly, OU=Android, O=Audioly, L=Unknown, ST=Unknown, C=US" \
        -noprompt
    echo -e "${GREEN}Keystore created: $KEYSTORE${NC}"

    cat > "$KEYSTORE_PROPS" <<EOF
storeFile=${KEYSTORE}
storePassword=${KEY_PASS}
keyAlias=${KEY_ALIAS}
keyPassword=${KEY_PASS}
EOF
    echo -e "${GREEN}keystore.properties written${NC}"
    echo ""
fi

# ─── 2. Build signed release APK ─────────────────────────────────────────────
echo -e "${YELLOW}Building release APK...${NC}"
./gradlew assembleRelease --console=plain

echo ""

# ─── 3. Verify output ────────────────────────────────────────────────────────
if [ ! -f "$APK_SRC" ]; then
    echo -e "${RED}Error: expected APK not found at $APK_SRC${NC}"
    echo "Check that keystore.properties is present and correct."
    exit 1
fi

cp "$APK_SRC" "$APK_OUT"
APK_SIZE=$(ls -lh "$APK_OUT" | awk '{print $5}')

echo -e "${GREEN}Build successful  —  $APK_OUT  ($APK_SIZE)${NC}"
echo ""

# ─── 4. Install via adb ──────────────────────────────────────────────────────
if ! command -v adb &> /dev/null; then
    echo -e "${YELLOW}adb not found on PATH — skipping install.${NC}"
    echo "APK is ready at: $SCRIPT_DIR/$APK_OUT"
    exit 0
fi

DEVICES=$(adb devices | grep -v "^List" | grep "device$" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    echo -e "${YELLOW}No adb device connected — skipping install.${NC}"
    echo "APK is ready at: $SCRIPT_DIR/$APK_OUT"
    echo "Run:  adb install -r $APK_OUT"
    exit 0
fi

echo -e "${YELLOW}Installing on device...${NC}"
adb install -r "$APK_OUT"

echo ""
echo -e "${GREEN}Installed successfully.${NC}"
echo -e "${BLUE}Launching app...${NC}"
adb shell am start -n com.audioly.app/.MainActivity
