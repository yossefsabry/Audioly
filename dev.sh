#!/bin/bash

# ANSI color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🤖 Starting Android Development Build...${NC}"

# Use a repo-local Gradle user home to avoid permission issues with ~/.gradle in sandboxed environments.
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
export GRADLE_USER_HOME="$PROJECT_DIR/.gradle-user-home"

# Android package/activity (used for uninstall + launch)
PACKAGE_NAME="com.audioly.app"
LAUNCH_COMPONENT="$PACKAGE_NAME/.MainActivity"

# Flags
BUILD_ONLY=false
CLEAN_INSTALL=false
GRADLE_FLAGS=()
for arg in "$@"; do
    case "$arg" in
        --build-only)
            BUILD_ONLY=true
            ;;
        --clean-install)
            CLEAN_INSTALL=true
            ;;
        --offline)
            GRADLE_FLAGS+=("--offline")
            ;;
        *)
            echo -e "${YELLOW}Unknown argument: $arg${NC}"
            echo "Usage: ./dev.sh [--build-only] [--clean-install] [--offline]"
            exit 1
            ;;
    esac
done

# Check if a device is connected
if [ "$BUILD_ONLY" = false ] && ! adb get-state 1>/dev/null 2>&1; then
    echo -e "${RED}❌ No Android device/emulator found.${NC}"
    echo "Please launch an emulator or connect a device first, or run: ./dev.sh --build-only"
    exit 1
fi

# Check if Gradle is available at the known location
GRADLE="/tmp/gradle-8.2/bin/gradle"
if [ ! -f "$GRADLE" ]; then
    echo -e "${YELLOW}Cached Gradle not found. Using wrapper...${NC}"
    GRADLE="./gradlew"
fi

# Build (and optionally install)
if [ "$BUILD_ONLY" = true ]; then
    echo -e "${BLUE}🔨 Building Debug APK...${NC}"
    $GRADLE assembleDebug "${GRADLE_FLAGS[@]}"
else
    echo -e "${BLUE}🔨 Building and Installing Debug APK...${NC}"
    if [ "$CLEAN_INSTALL" = true ]; then
        echo -e "${YELLOW}🧹 Uninstalling existing app ($PACKAGE_NAME)...${NC}"
        adb uninstall "$PACKAGE_NAME" 1>/dev/null 2>&1 || true
    fi
    $GRADLE installDebug "${GRADLE_FLAGS[@]}"
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Build Successful!${NC}"

    APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
        echo -e "${BLUE}📦 APK:${NC} ${GREEN}$APK_PATH${NC} (${APK_SIZE})"
    fi

    if [ "$BUILD_ONLY" = false ]; then
        # Launch the Activity (MainActivity is the launcher)
        echo -e "${BLUE}🚀 Launching App...${NC}"
        adb shell am start -n "$LAUNCH_COMPONENT"

        echo -e "${GREEN}✨ App is running on your emulator!${NC}"
        echo "Note: Unlike Expo, 'Hot Reload' requires Android Studio. To see changes, run this script again."
    else
        echo -e "${GREEN}✨ Build-only complete.${NC}"
    fi
else
    echo -e "${RED}❌ Build Failed. Check the error output above.${NC}"
    echo -e "${YELLOW}Tip:${NC} If you see INSTALL_FAILED_UPDATE_INCOMPATIBLE, run: adb uninstall $PACKAGE_NAME (or ./dev.sh --clean-install)"
    exit 1
fi
