#!/bin/sh
# MetricsHub detached upgrade runner (Linux).
#
# Launched by the MetricsHub agent through `systemd-run` as a transient one-shot unit so it
# survives the agent service being stopped. It re-verifies the staged package, stops the service,
# installs the package, restarts the service, and records the outcome in a result marker file that
# the agent reads when it starts again.
#
# The agent is the only writer of the upgrade transaction; this runner writes only the
# `runner.result` and `runner.log` files in the staging directory.
#
# Arguments:
#   --package <file>    Absolute path of the staged package to install
#   --sha256 <hex>      Expected SHA-256 of the package, lowercase hexadecimal
#   --type <deb|rpm>    Package type
#   --service <unit>    systemd service unit of the MetricsHub agent
#   --staging <dir>     Staging directory receiving runner.result and runner.log
#
# Exit codes: 0 success, 2 usage, 10 hash mismatch, 11 stop failed, 12 install failed,
#             13 service did not come back up.

set -u

PACKAGE=""
SHA256=""
PKG_TYPE=""
SERVICE=""
STAGING=""

while [ $# -gt 0 ]; do
	case "$1" in
		--package) PACKAGE="$2"; shift 2 ;;
		--sha256) SHA256="$2"; shift 2 ;;
		--type) PKG_TYPE="$2"; shift 2 ;;
		--service) SERVICE="$2"; shift 2 ;;
		--staging) STAGING="$2"; shift 2 ;;
		*) echo "Unknown argument: $1" >&2; exit 2 ;;
	esac
done

if [ -z "$PACKAGE" ] || [ -z "$SHA256" ] || [ -z "$PKG_TYPE" ] || [ -z "$SERVICE" ] || [ -z "$STAGING" ]; then
	echo "Missing required argument" >&2
	exit 2
fi

LOG="$STAGING/runner.log"
RESULT="$STAGING/runner.result"

log() {
	echo "$(date '+%Y-%m-%dT%H:%M:%S%z') $1" >> "$LOG" 2>&1
}

# Writes the result marker and exits. $1 = marker content, $2 = exit code.
finish() {
	# Write atomically so the agent never reads a half-written marker
	printf '%s\n' "$1" > "$RESULT.tmp" && mv -f "$RESULT.tmp" "$RESULT"
	log "RESULT: $1 (exit $2)"
	exit "$2"
}

log "Starting upgrade: package=$PACKAGE type=$PKG_TYPE service=$SERVICE"

# 1. Re-verify the package hash right before installing.
ACTUAL_SHA256=$(sha256sum "$PACKAGE" 2>>"$LOG" | awk '{print $1}')
if [ "$ACTUAL_SHA256" != "$SHA256" ]; then
	log "SHA-256 mismatch: expected $SHA256, got $ACTUAL_SHA256"
	finish "INSTALL_FAILED exit=10 step=verify" 10
fi

# 2. Stop the MetricsHub service.
if ! systemctl stop "$SERVICE" >>"$LOG" 2>&1; then
	log "Failed to stop $SERVICE"
	finish "INSTALL_FAILED exit=11 step=stop" 11
fi

# 3. Install the package, preferring the package manager (dependency resolution), falling back to
#    the low-level tool. On failure, bring the previous version's service back up.
INSTALL_RC=1
if [ "$PKG_TYPE" = "deb" ]; then
	if command -v apt-get >/dev/null 2>&1; then
		DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-downgrades "$PACKAGE" >>"$LOG" 2>&1
		INSTALL_RC=$?
	else
		dpkg -i "$PACKAGE" >>"$LOG" 2>&1
		INSTALL_RC=$?
	fi
elif [ "$PKG_TYPE" = "rpm" ]; then
	if command -v dnf >/dev/null 2>&1; then
		dnf -y install "$PACKAGE" >>"$LOG" 2>&1
		INSTALL_RC=$?
	elif command -v yum >/dev/null 2>&1; then
		yum -y install "$PACKAGE" >>"$LOG" 2>&1
		INSTALL_RC=$?
	else
		rpm -Uvh --replacepkgs "$PACKAGE" >>"$LOG" 2>&1
		INSTALL_RC=$?
	fi
else
	log "Unsupported package type: $PKG_TYPE"
	systemctl start "$SERVICE" >>"$LOG" 2>&1 || true
	finish "INSTALL_FAILED exit=12 step=install" 12
fi

if [ "$INSTALL_RC" -ne 0 ]; then
	log "Package installation failed with code $INSTALL_RC; attempting to restart the previous version"
	systemctl start "$SERVICE" >>"$LOG" 2>&1 || true
	finish "INSTALL_FAILED exit=12 step=install" 12
fi

# 4. Start the (newly installed) service and wait for it to become active.
systemctl start "$SERVICE" >>"$LOG" 2>&1 || true
i=0
while [ "$i" -lt 60 ]; do
	if systemctl is-active --quiet "$SERVICE"; then
		finish "INSTALL_OK" 0
	fi
	i=$((i + 1))
	sleep 1
done

log "$SERVICE did not become active within 60 seconds"
finish "INSTALL_FAILED exit=13 step=start" 13
