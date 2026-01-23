#!/usr/bin/env bash
set -euo pipefail

# ============================================================
#  build-windows.sh
#  Local Windows build script from Linux/macOS (no signing, no uploads)
# ============================================================

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

info "Starting Windows installer build process"

# ------------------------------------------------------------
# Step 1: Check Java / JDK (analog to 'Set up JDK')
# ------------------------------------------------------------
step "Checking Java / JDK"

JAVA_OUTPUT=""
if ! JAVA_OUTPUT="$(java --version 2>&1)"; then
  fail "Java (JDK) not found on PATH. Please install JDK 21+ and ensure 'java' is on PATH."
  exit 1
fi

JAVA_VERSION_LINE="$(printf '%s\n' "$JAVA_OUTPUT" | head -n1)"

# Extract major version number
# Handles outputs like:
#   openjdk version "21.0.2" ...
#   openjdk version "23" ...
#   openjdk 21.0.2 2024-01-16
JAVA_MAJOR=""

# Try to extract a number inside quotes first
if [[ "$JAVA_VERSION_LINE" =~ \"([0-9]+) ]]; then
  JAVA_MAJOR="${BASH_REMATCH[1]}"
# Fallback: first number on the line
elif [[ "$JAVA_VERSION_LINE" =~ ([0-9]+) ]]; then
  JAVA_MAJOR="${BASH_REMATCH[1]}"
fi

if [[ -z "${JAVA_MAJOR}" || ! "$JAVA_MAJOR" =~ ^[0-9]+$ ]]; then
  fail "Unable to parse Java version from: $JAVA_VERSION_LINE"
  exit 1
fi

if (( JAVA_MAJOR < 21 )); then
  fail "Java version ${JAVA_MAJOR} detected. JDK 21 or newer is required."
  exit 1
fi

info "Java version ${JAVA_MAJOR} detected - OK."

# ------------------------------------------------------------
# Step 2: Prepare packaging-assets directory
# (analog to 'Download Assets' + 'Get Windows Assets')
# ------------------------------------------------------------
step "Preparing packaging assets"

DISTRIB=""
if [ -f /etc/debian_version ]; then
  DISTRIB="debian"
fi
if [ -f /etc/redhat-release ]; then
  DISTRIB="rhel"
fi
if [ "${DISTRIB}" != "debian" ] && [ "${DISTRIB}" != "rhel" ]; then
  fail "This build script is intended to run on Debian or RedHat systems."
  exit 1
fi

if [ ! -d "${BUILD_DIR}/assets-${DISTRIB}" ]; then
  fail "Unable to find Linux assets in the Maven build directory: ${BUILD_DIR}/assets-${DISTRIB}"
  exit 1
fi

info "Found asset directory: ${BUILD_DIR}/assets-${DISTRIB}"

if [ -d "${ROOT_DIR}/packages" ]; then
  # Remove previous packages dir entirely (simpler & safe)
  if ! rm -rf "${ROOT_DIR}/packages"; then
    fail "Unable to remove existing '${ROOT_DIR}/packages' directory."
    exit 1
  fi
  info "Removed existing packages directory: ${ROOT_DIR}/packages"
fi

# ------------------------------------------------------------
# Step 3: Create JRE for Linux (jlink)
# ------------------------------------------------------------
step "Creating custom JRE for Linux via jlink"

CREATE_JRE_SCRIPT="${BASE_DIR}/create-jre.sh"
if [ ! -x "${CREATE_JRE_SCRIPT}" ]; then
  fail "JRE creation script not found or not executable: ${CREATE_JRE_SCRIPT}"
  exit 1
fi

if ! "${CREATE_JRE_SCRIPT}"; then
  fail "JRE creation script (create-jre.sh) failed."
  exit 1
fi

# IMPORTANT: adjust if your create-jre.sh outputs somewhere else
JRE_DIR="${BUILD_DIR}/assets-local/jre-linux"

if [ ! -d "${JRE_DIR}" ]; then
  fail "Expected JRE directory not found: ${JRE_DIR}"
  exit 1
fi

# ------------------------------------------------------------
# Step 4: Check Distribution packages prerequisites
# ------------------------------------------------------------

check_deb_pkg() {
  local pkg="$1"
  if ! dpkg-query -W -f='${Status}' "$pkg" 2>/dev/null | grep -q "install ok installed"; then
    fail "Required Debian package '$pkg' is not installed. Install it with: sudo apt-get install $pkg"
    exit 1
  fi
}

check_rpm_pkg() {
  local pkg="$1"
  if ! rpm -q "$pkg" >/dev/null 2>&1; then
    fail "Required RPM package '$pkg' is not installed. Install it with: sudo dnf install $pkg  (or yum on older systems)."
    exit 1
  fi
}

if [ "${DISTRIB}" == "debian" ]; then
  step "Checking Debian packaging prerequisites"
  check_deb_pkg "fakeroot"
  if [ "$(uname -m)" == "x86_64" ]; then
    check_deb_pkg "gcc-multilib"
  fi

elif [ "${DISTRIB}" == "rhel" ]; then
  step "Checking RedHat packaging prerequisites"
  check_rpm_pkg "rpm-build"
fi

# ------------------------------------------------------------
# Step 5: Run Linux Packaging Script (jpackage wrapper)
# ------------------------------------------------------------
step "Running Linux packaging script"

if [ ! -d "${ROOT_DIR}/packages" ]; then
  if ! mkdir -p "${ROOT_DIR}/packages"; then
    fail "Unable to create '${ROOT_DIR}/packages' directory."
    exit 1
  fi
fi

PKG_SCRIPT="${BUILD_DIR}/assets-${DISTRIB}/jpackage/package.sh"
if [ ! -f "${PKG_SCRIPT}" ]; then
  fail "Missing '${PKG_SCRIPT}'."
  exit 1
fi

pushd "${BUILD_DIR}/assets-${DISTRIB}/jpackage" >/dev/null

set +e
bash "./package.sh" "${JRE_DIR}" "${ROOT_DIR}/packages"
PKG_EXIT=$?
set -e

popd >/dev/null

if [ "${PKG_EXIT}" -ne 0 ]; then
  fail "Packaging script (package.sh) failed with exit code ${PKG_EXIT}."
  exit 1
fi

info "Resulting packages:"

# ------------------------------------------------------------
# Done
# ------------------------------------------------------------
exit 0
