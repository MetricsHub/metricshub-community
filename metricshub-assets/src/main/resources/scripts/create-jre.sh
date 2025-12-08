#!/usr/bin/env bash

# ============================================================
#  create-jre.sh
#  Local JRE creation script for Windows builds (run on Linux)
# ============================================================

# --- Configurable JDK version (placeholder for build tool filtering) ---
JAVA_DOCKER_VERSION="@metricshub-jre.version@"
# Replace Docker's "_" with "+" as used in JDK versioning
JAVA_EMBEDDING_VERSION="${JAVA_DOCKER_VERSION//_/+}"

# --- Setup paths ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="${SCRIPT_DIR}"
BUILD_DIR="${BASE_DIR}/.."
PROJECT_DIR="${BUILD_DIR}/../.."

# --- Setup ANSI colors (blue for steps, red for errors) ---
ESC=$'\033'
BLUE="${ESC}[36m"
GREEN="${ESC}[32m"
RED="${ESC}[31m"
RESET="${ESC}[0m"

ROOT_DIR="$(pwd)"

step() {
  echo "[${GREEN}STEP${RESET}]  $*"
}

info() {
  echo "[${BLUE}INFO${RESET}]  $*"
}

fail() {
  echo "[${RED}ERROR${RESET}] $*"
}

# ------------------------------------------------------------
# Step 1: Check for existing JRE
# ------------------------------------------------------------
if [ -d "${BUILD_DIR}/assets-local/jre-linux" ]; then
  info "Custom JRE directory already exists (${BUILD_DIR}/assets-local/jre-linux); skipping jlink step."
  echo
  info "JRE creation completed successfully."
  exit 0
fi

# ------------------------------------------------------------
# Step 2: Create Variables
# ------------------------------------------------------------
JDK_ARCH="x64"
if [ "$(uname -m)" == "aarch64" ] || [ "$(uname -m)" == "arm64" ]; then
  JDK_ARCH="aarch64"
fi
JDK_URL="https://api.adoptium.net/v3/binary/version/jdk-${JAVA_EMBEDDING_VERSION}/linux/${JDK_ARCH}/jdk/hotspot/normal/eclipse?project=jdk"

info "Download JDK archive from URL: ${JDK_URL}"

mkdir -p "${BUILD_DIR}/assets-local"

curl -sLo "${BUILD_DIR}/assets-local/jdk-linux.tar.gz" "${JDK_URL}"
if [ $? -ne 0 ]; then
  fail "Failed to download JDK from ${JDK_URL}"
  exit 1
fi

info "Extract JDK archive to ${BUILD_DIR}/assets-local/jdk-linux"

mkdir -p "${BUILD_DIR}/assets-local/jdk-linux"
tar -xf "${BUILD_DIR}/assets-local/jdk-linux.tar.gz" -C "${BUILD_DIR}/assets-local/jdk-linux"
if [ $? -ne 0 ]; then
  fail "Failed to extract JDK archive."
  exit 1
fi

# Locate JDK bin directory
JDK_BIN_DIR=""
for d in "${BUILD_DIR}"/assets-local/jdk-linux/jdk-*; do
  if [ -d "${d}/bin" ]; then
    JDK_BIN_DIR="${d}/bin"
    info "JDK bin directory found: ${JDK_BIN_DIR}"
    break
  fi
done

if [ -z "${JDK_BIN_DIR}" ]; then
  fail "Failed to find JDK directory after extraction."
  exit 1
fi

# Ensure jlink is available (use the one from the downloaded JDK)
PATH="${JDK_BIN_DIR}:${PATH}"

if ! command -v jlink >/dev/null 2>&1; then
  fail "jlink not found on PATH. Ensure you are using a JDK that provides jlink."
  exit 1
fi

MODULES_FILE="${BUILD_DIR}/assets-debian/jre-modules.txt"

if [ ! -f "${MODULES_FILE}" ]; then
  fail "Missing '${MODULES_FILE}'. This file should list the JDK modules."
  exit 1
fi

# Build comma-separated module list
MODS=$(tr '\n' ',' < ${MODULES_FILE} | sed 's/,$//')

if [ -z "${MODS}" ]; then
  fail "No modules found in ${MODULES_FILE}"
  exit 1
fi

info "Using jlink modules: ${MODS}"

jlink \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --add-modules "${MODS}" \
  --output "${BUILD_DIR}/assets-local/jre-linux"

if [ $? -ne 0 ]; then
  fail "jlink failed to create the custom JRE."
  exit 1
fi

# ------------------------------------------------------------
# Done
# ------------------------------------------------------------
info "JRE creation completed successfully."
exit 0
