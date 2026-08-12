#!/usr/bin/env bash
# Drives the detached upgrade runner end-to-end inside a systemd container, exactly as the
# MetricsHub agent launches it (systemd-run transient one-shot unit), and asserts the outcome.
#
# Scenarios (per package type):
#   corrupted  - a corrupted staged package with the pristine hash: the runner must fail with
#                exit 10 (hash re-check) and leave the installed service untouched
#   upgrade    - deb only: a baseline repacked as version 1.0.0 is upgraded to the built package
#                (released N-1 deb/rpm packages are not retrievable: GitHub releases only carry
#                archives, so the baseline is derived from the built package itself)
#   reinstall  - rpm only: the built package is force-reinstalled over itself, the same-version
#                hotfix path (--mode reinstall)
#
# Usage: upgrade-test-linux.sh <idPrefix> <osLabel> <deb|rpm> <packagePattern>

set -euo pipefail

ID_PREFIX="$1"
OS_LABEL="$2"
PACKAGE_TYPE="$3"
PACKAGE_PATTERN="$4"

ARCH="amd64"
CONTAINER="metricshub-upgrade-${PACKAGE_TYPE}"
RUNNER_SCRIPT="metricshub-assets/src/main/resources/linux/upgrade-runner/metricshub-upgrade-runner.sh"
STAGING="/opt/metricshub/lib/upgrade"
RESULTS_DIR="results"
FAILURES=0

mkdir -p "${RESULTS_DIR}"

log() {
	printf '=== %s\n' "$1"
}

record() {
	local scenario="$1" status="$2" details="$3"
	jq -n \
		--arg scenario "${scenario}" \
		--arg os "${OS_LABEL}" \
		--arg packageType "${PACKAGE_TYPE}" \
		--arg arch "${ARCH}" \
		--arg status "${status}" \
		--arg details "${details}" \
		'{scenario: $scenario, os: $os, packageType: $packageType, arch: $arch, status: $status, details: $details}' \
		> "${RESULTS_DIR}/${scenario}.json"
	if [ "${status}" != "passed" ]; then
		FAILURES=$((FAILURES + 1))
		log "FAILED ${scenario}: ${details}"
		diagnostics "${scenario}"
	else
		log "PASSED ${scenario}: ${details}"
	fi
}

diagnostics() {
	local scenario="$1"
	{
		echo "--- runner.result ---"
		docker exec "${CONTAINER}" cat "${STAGING}/runner.result" 2>&1 || true
		echo "--- runner.log ---"
		docker exec "${CONTAINER}" cat "${STAGING}/runner.log" 2>&1 || true
		echo "--- journal (upgrade units) ---"
		docker exec "${CONTAINER}" sh -c 'journalctl --no-pager -u "metricshub-upgrade-*" 2>&1 | tail -n 80' || true
		echo "--- service status ---"
		docker exec "${CONTAINER}" systemctl --no-pager status "${SERVICE_UNIT:-unknown}" 2>&1 | tail -n 30 || true
	} > "${RESULTS_DIR}/${scenario}.log" 2>&1 || true
}

cleanup() {
	docker rm -f "${CONTAINER}" > /dev/null 2>&1 || true
}
trap cleanup EXIT

# ------------------------------------------------------------------------------------------------
# Locate the built package
# ------------------------------------------------------------------------------------------------
PACKAGE_FILE=$(find packages -maxdepth 1 -type f -name "${PACKAGE_PATTERN}" | head -n 1)
if [ -z "${PACKAGE_FILE}" ]; then
	record "${ID_PREFIX}-setup" "failed" "package not found for pattern ${PACKAGE_PATTERN}"
	exit 1
fi
PACKAGE_NAME=$(basename "${PACKAGE_FILE}")
PACKAGE_SHA256=$(sha256sum "${PACKAGE_FILE}" | awk '{print $1}')
log "Package under test: ${PACKAGE_NAME} (sha256 ${PACKAGE_SHA256})"

# ------------------------------------------------------------------------------------------------
# Prepare the systemd container image and, for deb, the older baseline package
# ------------------------------------------------------------------------------------------------
if [ "${PACKAGE_TYPE}" = "deb" ]; then
	IMAGE="metricshub-systemd-debian:local"
	docker build -t "${IMAGE}" - <<'DOCKERFILE'
FROM debian:12
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
	&& apt-get install -y --no-install-recommends systemd systemd-sysv procps ca-certificates \
	&& rm -rf /var/lib/apt/lists/*
CMD ["/sbin/init"]
DOCKERFILE

	# Baseline: the built package repacked as version 1.0.0 (identical payload and scriptlets)
	log "Repacking ${PACKAGE_NAME} as baseline version 1.0.0"
	REPACK_DIR=$(mktemp -d)
	dpkg-deb -R "${PACKAGE_FILE}" "${REPACK_DIR}/root"
	sed -i 's/^Version: .*/Version: 1.0.0/' "${REPACK_DIR}/root/DEBIAN/control"
	dpkg-deb --root-owner-group -b "${REPACK_DIR}/root" "packages/baseline_1.0.0_${ARCH}.deb" > /dev/null
	BASELINE_NAME="baseline_1.0.0_${ARCH}.deb"
else
	IMAGE="registry.access.redhat.com/ubi9/ubi-init"
	BASELINE_NAME="${PACKAGE_NAME}"
fi

# ------------------------------------------------------------------------------------------------
# Boot systemd
# ------------------------------------------------------------------------------------------------
log "Starting the systemd container (${IMAGE})"
docker run -d --name "${CONTAINER}" \
	--privileged \
	--cgroupns=host \
	--tmpfs /run --tmpfs /run/lock \
	-v /sys/fs/cgroup:/sys/fs/cgroup:rw \
	-v "${PWD}/packages:/tmp/packages:ro" \
	-v "${PWD}/$(dirname "${RUNNER_SCRIPT}"):/tmp/runner:ro" \
	"${IMAGE}" /sbin/init > /dev/null

for _ in $(seq 1 30); do
	STATE=$(docker exec "${CONTAINER}" systemctl is-system-running 2>/dev/null || true)
	case "${STATE}" in
		running|degraded) break ;;
	esac
	sleep 2
done
log "systemd state: ${STATE:-unknown}"

# ------------------------------------------------------------------------------------------------
# Install the baseline and ensure the service is active
# ------------------------------------------------------------------------------------------------
log "Installing the baseline package ${BASELINE_NAME}"
if [ "${PACKAGE_TYPE}" = "deb" ]; then
	docker exec "${CONTAINER}" sh -c "apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y /tmp/packages/${BASELINE_NAME}" > /dev/null
else
	docker exec "${CONTAINER}" dnf install -y "/tmp/packages/${BASELINE_NAME}" > /dev/null
fi

# Discover the service unit the same way ServiceNameResolver does
SERVICE_UNIT=$(docker exec "${CONTAINER}" sh -c 'ls /lib/systemd/system/metricshub-*-service.service /etc/systemd/system/metricshub-*-service.service 2>/dev/null | head -n 1 | xargs -r basename')
if [ -z "${SERVICE_UNIT}" ]; then
	record "${ID_PREFIX}-setup" "failed" "no metricshub-*-service.service unit found after baseline installation"
	exit 1
fi
log "Service unit: ${SERVICE_UNIT}"

docker exec "${CONTAINER}" systemctl start "${SERVICE_UNIT}" || true
ACTIVE=""
for _ in $(seq 1 30); do
	if docker exec "${CONTAINER}" systemctl is-active --quiet "${SERVICE_UNIT}"; then
		ACTIVE="yes"
		break
	fi
	sleep 2
done
if [ -z "${ACTIVE}" ]; then
	record "${ID_PREFIX}-setup" "failed" "the ${SERVICE_UNIT} service did not become active after the baseline installation"
	exit 1
fi

baseline_version() {
	if [ "${PACKAGE_TYPE}" = "deb" ]; then
		docker exec "${CONTAINER}" dpkg-query -W -f='${Version}' metricshub
	else
		docker exec "${CONTAINER}" rpm -q --qf '%{VERSION}-%{RELEASE}' metricshub
	fi
}
INSTALLED_BEFORE=$(baseline_version)
log "Installed baseline version: ${INSTALLED_BEFORE}"

docker exec "${CONTAINER}" mkdir -p "${STAGING}"

# Launches the runner exactly as LinuxSystemdRunnerLauncher does and waits for runner.result.
# $1 = staged package path (in container), $2 = expected sha256, $3 = mode, $4 = wait seconds
run_runner() {
	local staged_package="$1" sha256="$2" mode="$3" wait_seconds="$4"
	local unit="metricshub-upgrade-ci-$(date +%s)"
	docker exec "${CONTAINER}" sh -c "rm -f '${STAGING}/runner.result' && cp /tmp/runner/metricshub-upgrade-runner.sh '${STAGING}/metricshub-upgrade-runner.sh' && chmod 700 '${STAGING}/metricshub-upgrade-runner.sh'"
	# This function is command-substituted: its stdout must carry only the marker, so the
	# systemd-run status text (e.g. "Running as unit: ...") is silenced and diverted to stderr
	docker exec "${CONTAINER}" systemd-run \
		--quiet \
		--unit="${unit}" \
		--collect \
		--no-block \
		--property=Type=oneshot \
		--property=TimeoutStartSec=900 \
		/bin/sh "${STAGING}/metricshub-upgrade-runner.sh" \
		--package "${staged_package}" \
		--sha256 "${sha256}" \
		--type "${PACKAGE_TYPE}" \
		--service "${SERVICE_UNIT}" \
		--staging "${STAGING}" \
		--mode "${mode}" 1>&2
	for _ in $(seq 1 $((wait_seconds / 2))); do
		if docker exec "${CONTAINER}" test -f "${STAGING}/runner.result"; then
			break
		fi
		sleep 2
	done
	docker exec "${CONTAINER}" cat "${STAGING}/runner.result" 2>/dev/null || echo "NO_RESULT"
}

# ------------------------------------------------------------------------------------------------
# Scenario 1 - corrupted package: hash re-check fails, the installed service is untouched
# ------------------------------------------------------------------------------------------------
log "Scenario: corrupted package"
# Corrupt by appending: package formats contain zero-padded regions, so overwriting a fixed
# offset can be a no-op; appending always changes the hash
docker exec "${CONTAINER}" sh -c "cp /tmp/packages/${PACKAGE_NAME} ${STAGING}/corrupted.${PACKAGE_TYPE} && printf 'CORRUPTED-BY-CI' >> ${STAGING}/corrupted.${PACKAGE_TYPE}"
RESULT=$(run_runner "${STAGING}/corrupted.${PACKAGE_TYPE}" "${PACKAGE_SHA256}" "install" 120)

SCENARIO="${ID_PREFIX}-corrupted"
if [ "${RESULT}" != "INSTALL_FAILED exit=10 step=verify" ]; then
	record "${SCENARIO}" "failed" "expected 'INSTALL_FAILED exit=10 step=verify', got '${RESULT}'"
elif ! docker exec "${CONTAINER}" systemctl is-active --quiet "${SERVICE_UNIT}"; then
	record "${SCENARIO}" "failed" "the service is no longer active after the corrupted-package attempt"
elif [ "$(baseline_version)" != "${INSTALLED_BEFORE}" ]; then
	record "${SCENARIO}" "failed" "the installed version changed after the corrupted-package attempt"
else
	record "${SCENARIO}" "passed" "runner refused the corrupted package and left the service untouched"
fi

# ------------------------------------------------------------------------------------------------
# Scenario 2 - real installation through the runner (deb: upgrade, rpm: same-version reinstall)
# ------------------------------------------------------------------------------------------------
if [ "${PACKAGE_TYPE}" = "deb" ]; then
	MODE="install"
	SCENARIO="${ID_PREFIX}-upgrade"
	log "Scenario: upgrade 1.0.0 -> built version"
else
	MODE="reinstall"
	SCENARIO="${ID_PREFIX}-reinstall"
	log "Scenario: same-version reinstall (hotfix path)"
fi

docker exec "${CONTAINER}" cp "/tmp/packages/${PACKAGE_NAME}" "${STAGING}/${PACKAGE_NAME}"
RESULT=$(run_runner "${STAGING}/${PACKAGE_NAME}" "${PACKAGE_SHA256}" "${MODE}" 600)

if [ "${PACKAGE_TYPE}" = "deb" ]; then
	EXPECTED_VERSION=$(dpkg-deb -f "${PACKAGE_FILE}" Version)
else
	EXPECTED_VERSION=$(docker exec "${CONTAINER}" rpm -qp --qf '%{VERSION}-%{RELEASE}' "/tmp/packages/${PACKAGE_NAME}")
fi
INSTALLED_AFTER=$(baseline_version)

MISSING_LINKS=""
for link in config connectors extensions logs otel security; do
	if ! docker exec "${CONTAINER}" test -L "/opt/metricshub/${link}"; then
		MISSING_LINKS="${MISSING_LINKS} ${link}"
	fi
done

if [ "${RESULT}" != "INSTALL_OK" ]; then
	record "${SCENARIO}" "failed" "expected 'INSTALL_OK', got '${RESULT}'"
elif ! docker exec "${CONTAINER}" systemctl is-active --quiet "${SERVICE_UNIT}"; then
	record "${SCENARIO}" "failed" "the ${SERVICE_UNIT} service is not active after the installation"
elif [ "${INSTALLED_AFTER}" != "${EXPECTED_VERSION}" ]; then
	record "${SCENARIO}" "failed" "installed version is '${INSTALLED_AFTER}', expected '${EXPECTED_VERSION}'"
elif [ -n "${MISSING_LINKS}" ]; then
	record "${SCENARIO}" "failed" "missing symlinks after installation:${MISSING_LINKS}"
else
	record "${SCENARIO}" "passed" "runner installed ${EXPECTED_VERSION} and the service is active (was ${INSTALLED_BEFORE})"
fi

exit $((FAILURES > 0 ? 1 : 0))
