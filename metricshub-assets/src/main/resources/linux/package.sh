#!/usr/bin/env bash

# Exit immediately on any error
# Treat unset variables as errors
# Fail if any command in a pipeline fails
set -euo pipefail

# -----------------------------------------------------------------------------
# package.sh
# -----------------------------------------------------------------------------
# This script packages the MetricsHub application using jpackage.
# It reproduces the two packages under <DEST_DIR>:
#   1. installable (E.g., .deb or .rpm)
#   2. app-image   (Directory with the application image, e.g. metricshub/)
#
# To include a custom asset, place it in the assets/ directory defined by --app-content
# in jpackage.txt. For example, to include the OpenTelemetry Collector Contrib binary,
# place it in assets/otel/ (otelcol-contrib) and it will be included in the package.
#
# Usage:
#   ./package.sh <JRE_DIR> <DEST_DIR>
#
# Example:
#   ./package.sh /tmp/jre /tmp/builds
# -----------------------------------------------------------------------------

# Ensure JAVA_HOME is defined
if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set. Please set it before running this script."
  exit 1
fi

# Ensure JAVA_HOME exists
if [[ ! -d "${JAVA_HOME}" ]]; then
  echo "JAVA_HOME directory '${JAVA_HOME}' does not exist."
  exit 1
fi

# Ensure destination directory is provided
if [[ "$#" -ne 2 ]]; then
  echo "Incorrect number of arguments."
  echo "Usage: $0 <JRE_DIR> <DEST_DIR>"
  exit 1
fi

JRE_DIR="$1"
if [[ ! -d "${JRE_DIR}" ]]; then
  echo "JRE directory '${JRE_DIR}' does not exist."
  exit 1
fi

DEST_DIR="$2"
mkdir -p "${DEST_DIR}"

JPACKAGE_BIN="${JAVA_HOME}/bin/jpackage"

# -----------------------------------------------------------------------------
# Shared arguments
# -----------------------------------------------------------------------------
COMMON_ARGS=(
  --runtime-image "${JRE_DIR}"
  --add-launcher metricshub-encrypt=metricshub-encrypt.properties
  --add-launcher apikey=metricshub-apikey.properties
  --add-launcher encrypt=metricshub-encrypt.properties
  --add-launcher httpcli=metricshub-httpcli.properties
  --add-launcher ipmicli=metricshub-ipmicli.properties
  --add-launcher jawk=metricshub-jawk.properties
  --add-launcher jdbccli=metricshub-jdbccli.properties
  --add-launcher jmxcli=metricshub-jmxcli.properties
  --add-launcher pingcli=metricshub-pingcli.properties
  --add-launcher snmpcli=metricshub-snmpcli.properties
  --add-launcher snmpv3cli=metricshub-snmpv3cli.properties
  --add-launcher sshcli=metricshub-sshcli.properties
  --add-launcher user=metricshub-user.properties
  --add-launcher wbemcli=metricshub-wbemcli.properties
  --add-launcher winremotecli=metricshub-winremotecli.properties
  --add-launcher winrmcli=metricshub-winrmcli.properties
  --add-launcher wmicli=metricshub-wmicli.properties
  --dest "${DEST_DIR}"
)

# -----------------------------------------------------------------------------
# Helper function
# -----------------------------------------------------------------------------
run_jpackage() {
  local id="$1"
  shift
  echo "Running jpackage for ${id}..."
  "${JPACKAGE_BIN}" --verbose "$@"
  echo "${id} packaging completed."
  echo
}

# -----------------------------------------------------------------------------
# installable
# -----------------------------------------------------------------------------
run_jpackage "installable" \
  --add-launcher community-service=metricshub-agent.properties \
  --license-file assets/LICENSE \
  "${COMMON_ARGS[@]}" \
  @jpackage.txt

# -----------------------------------------------------------------------------
# app-image
# -----------------------------------------------------------------------------
run_jpackage "app-image" \
  --add-launcher metricshub-community-service=metricshub-agent.properties \
  "${COMMON_ARGS[@]}" \
  @jpackage.txt \
  --type app-image

echo "All packaging steps completed successfully! Output in: ${DEST_DIR}"

echo "${DEST_DIR} content:"
echo "------------------------------------------------------------"
ls -lh "${DEST_DIR}"
echo "------------------------------------------------------------"