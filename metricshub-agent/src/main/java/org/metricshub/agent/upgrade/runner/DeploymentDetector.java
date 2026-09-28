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

import java.io.File;
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
		 * @return {@code true} when the command completed with exit code 0, {@code false} when it
		 *         completed with a non-zero exit code or its binary does not exist on this host —
		 *         both definitive answers
		 * @throws DetectionIndeterminateException when the probe cannot deliver a verdict (timeout,
		 *                                         interruption): the caller must not treat this as
		 *                                         "not installed"
		 */
		boolean succeeds(String... command);
	}

	/**
	 * Signals that a deployment probe could not deliver a verdict: the detection must be retried,
	 * never cached, and never interpreted as an {@code ARCHIVE} deployment — a packaged host whose
	 * package manager transiently hangs must not be misclassified until restart.
	 */
	public static class DetectionIndeterminateException extends IllegalStateException {

		private static final long serialVersionUID = 1L;

		/**
		 * Creates the exception.
		 *
		 * @param message what prevented the verdict
		 */
		public DetectionIndeterminateException(final String message) {
			super(message);
		}
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
	 * Detects the deployment kind, caching the result. Only a delivered verdict is cached: an
	 * indeterminate probe (timeout, interruption) throws and the next call retries, so a transient
	 * package-manager hiccup never freezes a wrong classification until restart.
	 *
	 * @return the detected deployment kind
	 * @throws DetectionIndeterminateException when a probe could not deliver a verdict
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
	 * Runs a probe command with a short timeout, discarding its output. A process that starts and
	 * exits delivers a definitive verdict (exit code), and a binary that does not exist on this
	 * host is a definitive "no" (that package manager is not here). A timeout or interruption is
	 * indeterminate and throws instead of masquerading as "not installed".
	 *
	 * @param command the command and its arguments
	 * @return {@code true} when the command completed with exit code 0
	 */
	private static boolean runProbe(final String... command) {
		if (!isCommandAvailable(command[0])) {
			// The probe binary is absent (dpkg on an RPM host, rpm on a Debian
			// host): a definitive negative, not a transient failure.
			log.debug("Deployment probe binary {} is not on the PATH.", command[0]);
			return false;
		}
		final Process process;
		try {
			process = new ProcessBuilder(command)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectErrorStream(false)
				.start();
		} catch (Exception e) {
			// The binary exists but the process could not start (permissions,
			// resource exhaustion): indeterminate, never proof of an archive
			// deployment.
			throw new DetectionIndeterminateException(
				"The deployment probe cannot start: " + String.join(" ", command) + ": " + e.getMessage()
			);
		}
		try {
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new DetectionIndeterminateException("The deployment probe timed out: " + String.join(" ", command));
			}
			return process.exitValue() == 0;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new DetectionIndeterminateException("The deployment probe was interrupted: " + String.join(" ", command));
		}
	}

	/**
	 * Reports whether an executable of the given name exists on the {@code PATH}: its absence is
	 * the definitive "that package manager is not on this host", as opposed to a start failure of
	 * an existing binary, which is indeterminate.
	 *
	 * @param command the plain command name
	 * @return {@code true} when an executable of that name is on the PATH
	 */
	private static boolean isCommandAvailable(final String command) {
		final String pathVariable = System.getenv("PATH");
		if (pathVariable == null || pathVariable.isBlank()) {
			return false;
		}
		final String[] extensions = LocalOsHandler.isWindows()
			? new String[] { ".exe", ".com", ".cmd", ".bat" }
			: new String[] { "" };
		for (final String directory : pathVariable.split(File.pathSeparator)) {
			if (directory.isBlank()) {
				continue;
			}
			for (final String extension : extensions) {
				try {
					if (Files.isExecutable(Path.of(directory, command + extension))) {
						return true;
					}
				} catch (Exception e) {
					// An unparseable PATH entry: skip it.
				}
			}
		}
		return false;
	}
}
