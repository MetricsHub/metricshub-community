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
#   --service <unit>    systemd service unit of the MetricsHub agent, supplied by the agent so any
#                       edition works (metricshub-community-service.service,
#                       metricshub-enterprise-service.service, ...)
#   --staging <dir>     Staging directory receiving runner.result and runner.log
#   --mode <mode>       install (newer version), reinstall (same version hotfix) or downgrade
#                       (older version); selects the package-manager operation so the package is
#                       really replaced instead of being reported as already current
#
# Exit codes: 0 success, 2 usage, 10 hash mismatch, 11 stop failed, 12 install failed,
#             13 service did not come back up, 15 terminated on installation timeout.
#
# The transient unit carries TimeoutStartSec=<install timeout>, so systemd TERMinates this
# script when the deadline elapses; the TERM trap restarts the service and records the failure
# so the agent never stays down without a verdict.

set -u

PACKAGE=""
SHA256=""
PKG_TYPE=""
SERVICE=""
STAGING=""
MODE="install"

while [ $# -gt 0 ]; do
	case "$1" in
		--package) PACKAGE="$2"; shift 2 ;;
		--sha256) SHA256="$2"; shift 2 ;;
		--type) PKG_TYPE="$2"; shift 2 ;;
		--service) SERVICE="$2"; shift 2 ;;
		--staging) STAGING="$2"; shift 2 ;;
		--mode) MODE="$2"; shift 2 ;;
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
	# A verdict is being finalized: a late TERM must not overwrite it
	trap - TERM INT
	# Write atomically so the agent never reads a half-written marker
	printf '%s\n' "$1" > "$RESULT.tmp" && mv -f "$RESULT.tmp" "$RESULT"
	log "RESULT: $1 (exit $2)"
	exit "$2"
}

# systemd kills the transient unit when the installation timeout elapses (TimeoutStartSec).
# Restart the service and record the failure so the agent does not stay down without a verdict.
on_terminated() {
	trap - TERM INT
	log "Terminated (installation timeout); restarting $SERVICE"
	systemctl start "$SERVICE" >>"$LOG" 2>&1 || true
	finish "INSTALL_FAILED exit=15 step=timeout" 15
}
trap on_terminated TERM INT

log "Starting upgrade: package=$PACKAGE type=$PKG_TYPE mode=$MODE service=$SERVICE"

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
# A same-version offer needs an explicit reinstall (the package manager otherwise considers the
# installed version current and exits successfully without replacing anything), and an older offer
# needs an explicit downgrade.
INSTALL_RC=1
if [ "$PKG_TYPE" = "deb" ]; then
	if command -v apt-get >/dev/null 2>&1; then
		case "$MODE" in
			reinstall)
				DEBIAN_FRONTEND=noninteractive apt-get install -y --reinstall "$PACKAGE" >>"$LOG" 2>&1
				;;
			downgrade)
				DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-downgrades "$PACKAGE" >>"$LOG" 2>&1
				;;
			*)
				DEBIAN_FRONTEND=noninteractive apt-get install -y "$PACKAGE" >>"$LOG" 2>&1
				;;
		esac
		INSTALL_RC=$?
	else
		# dpkg -i unpacks the given package whatever its version, so it covers every mode
		dpkg -i "$PACKAGE" >>"$LOG" 2>&1
		INSTALL_RC=$?
	fi
elif [ "$PKG_TYPE" = "rpm" ]; then
	if command -v dnf >/dev/null 2>&1; then
		case "$MODE" in
			reinstall) dnf -y reinstall "$PACKAGE" >>"$LOG" 2>&1 ;;
			downgrade) dnf -y downgrade "$PACKAGE" >>"$LOG" 2>&1 ;;
			*) dnf -y install "$PACKAGE" >>"$LOG" 2>&1 ;;
		esac
		INSTALL_RC=$?
	elif command -v yum >/dev/null 2>&1; then
		case "$MODE" in
			reinstall) yum -y reinstall "$PACKAGE" >>"$LOG" 2>&1 ;;
			downgrade) yum -y downgrade "$PACKAGE" >>"$LOG" 2>&1 ;;
			*) yum -y install "$PACKAGE" >>"$LOG" 2>&1 ;;
		esac
		INSTALL_RC=$?
	else
		case "$MODE" in
			downgrade) rpm -Uvh --replacepkgs --oldpackage "$PACKAGE" >>"$LOG" 2>&1 ;;
			*) rpm -Uvh --replacepkgs "$PACKAGE" >>"$LOG" 2>&1 ;;
		esac
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
