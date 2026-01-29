#!/bin/bash
#
# Build Isotope for all supported Minecraft versions and loaders
#
# Usage:
#   ./scripts/build-all-versions.sh           # Build all 24 combinations
#   ./scripts/build-all-versions.sh --quick   # Build only latest (1.21.11) for both loaders
#   ./scripts/build-all-versions.sh 1.21.4    # Build specific version for both loaders
#

set -e

# Change to project root
cd "$(dirname "$0")/.."

# Set Java 21 (macOS with Homebrew)
if [[ -d "/opt/homebrew/opt/openjdk@21" ]]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi

# Supported versions
# Note: MC 1.21.1-1.21.3 and 1.21.9-1.21.10 require additional compat layer work
# (ToastManager, EntitySpawnReason, registry API changes)
# Currently supported: 1.21.4-1.21.8, 1.21.11
ALL_VERSIONS=("1.21.4" "1.21.5" "1.21.6" "1.21.7" "1.21.8" "1.21.11")
LOADERS=("neoforge" "fabric")

# Parse arguments
VERSIONS=("${ALL_VERSIONS[@]}")
if [[ "$1" == "--quick" ]]; then
    VERSIONS=("1.21.11")
    echo "Quick mode: building only MC 1.21.11"
elif [[ -n "$1" ]]; then
    VERSIONS=("$1")
    echo "Building specific version: $1"
fi

TOTAL=$((${#VERSIONS[@]} * ${#LOADERS[@]}))
CURRENT=0
FAILED=()

echo "========================================"
echo "Isotope Multi-Version Build"
echo "========================================"
echo "Versions: ${VERSIONS[*]}"
echo "Loaders: ${LOADERS[*]}"
echo "Total builds: ${TOTAL}"
echo "========================================"
echo ""

for version in "${VERSIONS[@]}"; do
    for loader in "${LOADERS[@]}"; do
        CURRENT=$((CURRENT + 1))
        echo ""
        echo "========================================"
        echo "[${CURRENT}/${TOTAL}] Building ${loader} for MC ${version}..."
        echo "========================================"

        if ./gradlew :${loader}:build -PmcVersion=${version} --no-daemon; then
            echo "[${CURRENT}/${TOTAL}] SUCCESS: ${loader} MC ${version}"
        else
            echo "[${CURRENT}/${TOTAL}] FAILED: ${loader} MC ${version}"
            FAILED+=("${loader}:${version}")
        fi
    done
done

echo ""
echo "========================================"
echo "Build Summary"
echo "========================================"

if [[ ${#FAILED[@]} -eq 0 ]]; then
    echo "All ${TOTAL} builds completed successfully!"
    echo ""
    echo "Artifacts:"
    find . -path "*/build/libs/*.jar" -name "isotope-*.jar" ! -name "*-dev-shadow.jar" ! -name "*-sources.jar" 2>/dev/null | sort
    exit 0
else
    echo "FAILURES: ${#FAILED[@]}/${TOTAL}"
    for fail in "${FAILED[@]}"; do
        echo "  - ${fail}"
    done
    exit 1
fi
