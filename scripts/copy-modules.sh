#!/bin/bash
# Script to copy built module JARs to the modules directory
# Usage: ./scripts/copy-modules.sh [target-dir]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Default target directory
TARGET_DIR="${1:-$PROJECT_ROOT/modules}"

# Source module JAR locations
MODULE_JARS=(
    "$PROJECT_ROOT/simulation-lean-modules/target/simulation-lean-modules-*.jar"
)

echo "Copying module JARs to: $TARGET_DIR"

# Create target directory if it doesn't exist
mkdir -p "$TARGET_DIR"

# Copy each module JAR
for pattern in "${MODULE_JARS[@]}"; do
    # Expand glob pattern
    for jar in $pattern; do
        if [ -f "$jar" ]; then
            cp "$jar" "$TARGET_DIR/"
            echo "  Copied: $(basename "$jar")"
        else
            echo "  Warning: No JAR found matching $pattern"
        fi
    done
done

echo "Module copy complete. Contents of $TARGET_DIR:"
ls -la "$TARGET_DIR"
