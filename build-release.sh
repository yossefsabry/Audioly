#!/bin/bash

# InGuide Kotlin Android App - Build Release APK Script
# This script builds the optimized release APK

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Audioly - Release Build Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if Gradle is available
if [ ! -f "/tmp/gradle-8.2/bin/gradle" ]; then
    echo -e "${YELLOW}Gradle not found in /tmp/gradle-8.2${NC}"
    echo "Downloading Gradle 8.2..."
    curl -L https://services.gradle.org/distributions/gradle-8.2-bin.zip -o /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d /tmp/
    rm /tmp/gradle.zip
    echo -e "${GREEN}Gradle downloaded successfully${NC}"
fi

GRADLE="/tmp/gradle-8.2/bin/gradle"

# Clean previous build
echo -e "${YELLOW}Cleaning previous build...${NC}"
$GRADLE clean --console=plain > /dev/null 2>&1 || true

# Build release APK
echo -e "${YELLOW}Building RELEASE APK (optimized with R8/ProGuard)...${NC}"
echo ""

$GRADLE assembleRelease --console=plain

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  RELEASE BUILD SUCCESSFUL!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    
    # Get APK path
    APK_PATH="$(pwd)/app/build/outputs/apk/release/app-release-unsigned.apk"
    
    # Check if APK exists
    if [ -f "$APK_PATH" ]; then
        # Get APK size
        APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
        
        echo -e "${BLUE}APK Location:${NC}"
        echo -e "${GREEN}$APK_PATH${NC}"
        echo ""
        echo -e "${BLUE}APK Size:${NC} $APK_SIZE"
        echo ""
        echo -e "${YELLOW}Note: This is an UNSIGNED release APK${NC}"
        echo -e "${YELLOW}For production, you need to sign it with a keystore${NC}"
        echo ""
        echo -e "${BLUE}To install on emulator (for testing):${NC}"
        echo "adb install -r $APK_PATH"
    else
        echo -e "${RED}Error: APK file not found at expected location${NC}"
    fi
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  BUILD FAILED${NC}"
    echo -e "${RED}========================================${NC}"
    exit 1
fi
