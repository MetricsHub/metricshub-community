package org.metricshub.agent.upgrade.runner;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.engine.common.helpers.LocalOsHandler;

/**
 * Detects how MetricsHub was deployed on this host: Debian/RPM package, Windows MSI, extracted
 * archive or container image. The result drives whether the {@code AcceptsPackages} OpAMP
 * capability is advertised. Detection runs once and is cached.
 */
@Slf4j
public class DeploymentDetector {

	/**
	 * Environment variable stamped in the official MetricsHub container image.
	 */
	static final String DEPLOYMENT_ENVIRONMENT_VARIABLE = "METRICSHUB_DEPLOYMENT";

	/**
	 * Name of the dpkg/rpm package owning the MetricsHub installation.
	 */
	static final String LINUX_PACKAGE_NAME = "metricshub";

	/**
	 * Registry path holding the Windows service keys, searched for a MetricsHub service of any
	 * edition.
	 */
	static final String WINDOWS_SERVICES_REGISTRY_KEY = "HKLM\\SYSTEM\\CurrentControlSet\\Services";

	/**
	 * Runs a probe command and reports whether it succeeded, so tests can substitute a fake.
	 */
	@FunctionalInterface
	public interface ProcessProbe {
		/**
		 * Runs the given command and waits for its completion.
		 *
		 * @param command the command and its arguments
		 * @return {@code true} when the command completed with exit code 0
		 */
		boolean succeeds(String... command);
	}

	private final ProcessProbe probe;
	private volatile DeploymentKind detected;

	/**
	 * Creates a detector using real process probes.
	 */
	public DeploymentDetector() {
		this(DeploymentDetector::runProbe);
	}

	/**
	 * Creates a detector with a caller-provided probe (used by tests).
	 *
	 * @param probe the process probe
	 */
	public DeploymentDetector(final ProcessProbe probe) {
		this.probe = probe;
	}

	/**
	 * Detects the deployment kind, caching the result.
	 *
	 * @return the detected deployment kind
	 */
	public DeploymentKind detect() {
		DeploymentKind result = detected;
		if (result == null) {
			result = doDetect();
			detected = result;
			log.info("Detected MetricsHub deployment kind: {}.", result);
		}
		return result;
	}

	/**
	 * Performs the actual detection.
	 *
	 * @return the detected deployment kind
	 */
	private DeploymentKind doDetect() {
		if (
			"docker".equalsIgnoreCase(System.getenv(DEPLOYMENT_ENVIRONMENT_VARIABLE)) || Files.exists(Path.of("/.dockerenv"))
		) {
			return DeploymentKind.DOCKER;
		}
		if (LocalOsHandler.isWindows()) {
			// Matches the service of any edition (MetricsHub Community, MetricsHub Enterprise, ...)
			return probe.succeeds(
					"reg",
					"query",
					WINDOWS_SERVICES_REGISTRY_KEY,
					"/k",
					"/f",
					AgentConstants.PRODUCT_WIN_DIR_NAME + "*"
				)
				? DeploymentKind.MSI
				: DeploymentKind.ARCHIVE;
		}
		if (probe.succeeds("dpkg", "-s", LINUX_PACKAGE_NAME)) {
			return DeploymentKind.DEB;
		}
		if (probe.succeeds("rpm", "-q", LINUX_PACKAGE_NAME)) {
			return DeploymentKind.RPM;
		}
		return DeploymentKind.ARCHIVE;
	}

	/**
	 * Runs a probe command with a short timeout, discarding its output.
	 *
	 * @param command the command and its arguments
	 * @return {@code true} when the command completed with exit code 0
	 */
	private static boolean runProbe(final String... command) {
		try {
			final Process process = new ProcessBuilder(command)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectErrorStream(false)
				.start();
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			log.debug("Deployment probe {} failed: {}", String.join(" ", command), e.getMessage());
			return false;
		}
	}
}
