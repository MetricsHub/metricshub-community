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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.engine.common.helpers.LocalOsHandler;

/**
 * Resolves the name of the service the running MetricsHub edition is installed as, without
 * hardcoding any edition: Community installs {@code metricshub-community-service.service} /
 * {@code MetricsHub Community} while Enterprise installs
 * {@code metricshub-enterprise-service.service} / {@code MetricsHub Enterprise}.
 * <p>
 * Resolution order:
 * </p>
 * <ol>
 *   <li>the explicitly configured {@code upgrade.serviceName}, which an edition or a customer can
 *       always pin;</li>
 *   <li>discovery of the installed service through a product-level (edition-agnostic) pattern:
 *       {@code metricshub-*-service.service} unit files on Linux, {@code MetricsHub *} service
 *       registry keys on Windows;</li>
 *   <li>when several candidates exist (both editions installed side by side), the one that is
 *       currently running — the agent performing the upgrade is that service. When several (or
 *       none) are running, the choice is ambiguous and resolution fails, requiring
 *       {@code upgrade.serviceName}: guessing could drive the wrong edition's service.</li>
 * </ol>
 */
@Slf4j
public class ServiceNameResolver {

	/**
	 * Prefix of the systemd unit files installed by any MetricsHub edition.
	 */
	static final String LINUX_UNIT_PREFIX = AgentConstants.PRODUCT_CODE + "-";

	/**
	 * Suffix of the systemd unit files installed by any MetricsHub edition.
	 */
	static final String LINUX_UNIT_SUFFIX = "-service.service";

	/**
	 * Prefix of the Windows service names used by any MetricsHub edition.
	 */
	static final String WINDOWS_SERVICE_PREFIX = AgentConstants.PRODUCT_WIN_DIR_NAME + " ";

	/**
	 * Registry path holding the Windows service keys.
	 */
	static final String WINDOWS_SERVICES_KEY = "HKLM\\SYSTEM\\CurrentControlSet\\Services";

	/**
	 * Directories searched for MetricsHub systemd unit files.
	 */
	private static final List<String> LINUX_UNIT_DIRECTORIES = List.of("/lib/systemd/system", "/etc/systemd/system");

	private final CommandOutputReader outputReader;
	private final List<String> unitDirectories;
	private final BooleanSupplier windows;

	/**
	 * Creates a resolver using real process probes and the standard systemd directories.
	 */
	public ServiceNameResolver() {
		this(new ProcessCommandOutputReader(), LINUX_UNIT_DIRECTORIES, LocalOsHandler::isWindows);
	}

	/**
	 * Creates a resolver with caller-provided collaborators (used by tests).
	 *
	 * @param outputReader    reads the output of the discovery commands
	 * @param unitDirectories directories searched for systemd unit files
	 * @param windows         whether this host is a Windows host
	 */
	ServiceNameResolver(
		final CommandOutputReader outputReader,
		final List<String> unitDirectories,
		final BooleanSupplier windows
	) {
		this.outputReader = outputReader;
		this.unitDirectories = unitDirectories;
		this.windows = windows;
	}

	/**
	 * Resolves the service name to hand to the detached upgrade runner.
	 *
	 * @param configuredServiceName the explicitly configured service name, or {@code null}/blank
	 *                              to discover it
	 * @return the resolved service name
	 * @throws IllegalStateException when no service can be resolved, or when several are
	 *                               installed and the running one cannot be singled out
	 */
	public String resolve(final String configuredServiceName) {
		if (configuredServiceName != null && !configuredServiceName.isBlank()) {
			return configuredServiceName.trim();
		}

		final List<String> candidates = windows.getAsBoolean() ? discoverWindowsServices() : discoverLinuxUnits();
		if (candidates.isEmpty()) {
			throw new IllegalStateException(
				"Cannot determine the MetricsHub service name of this installation; set upgrade.serviceName explicitly"
			);
		}
		if (candidates.size() == 1) {
			return candidates.get(0);
		}

		// Several editions are installed side by side: the agent driving the upgrade is the
		// running one — but only when exactly one is running. Guessing here would let an
		// Enterprise-triggered upgrade stop and restart the Community service (or vice versa).
		final List<String> running = candidates.stream().filter(this::isRunning).toList();
		if (running.size() == 1) {
			return running.get(0);
		}
		throw new IllegalStateException(
			"Several MetricsHub services are installed (" +
				candidates +
				") and " +
				(running.isEmpty() ? "none is" : "several are") +
				" reported running; set upgrade.serviceName explicitly"
		);
	}

	/**
	 * Lists the MetricsHub systemd units installed on this host.
	 *
	 * @return the unit file names, sorted; empty when none is found
	 */
	private List<String> discoverLinuxUnits() {
		final List<String> units = new ArrayList<>();
		for (final String directory : unitDirectories) {
			final Path path = Path.of(directory);
			if (!Files.isDirectory(path)) {
				continue;
			}
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, LINUX_UNIT_PREFIX + "*" + LINUX_UNIT_SUFFIX)) {
				for (final Path unit : stream) {
					final String name = unit.getFileName().toString();
					if (!units.contains(name)) {
						units.add(name);
					}
				}
			} catch (IOException e) {
				log.debug("Cannot list the systemd units in {}: {}", directory, e.getMessage());
			}
		}
		units.sort(String::compareTo);
		return units;
	}

	/**
	 * Lists the MetricsHub Windows services installed on this host by matching the service
	 * registry keys.
	 *
	 * @return the service names, sorted; empty when none is found
	 */
	private List<String> discoverWindowsServices() {
		final List<String> services = new ArrayList<>();
		final List<String> lines = outputReader.readLines(
			List.of("reg", "query", WINDOWS_SERVICES_KEY, "/k", "/f", AgentConstants.PRODUCT_WIN_DIR_NAME + "*")
		);
		for (final String line : lines) {
			final String trimmed = line.trim();
			final int lastSeparator = trimmed.lastIndexOf('\\');
			if (lastSeparator < 0) {
				continue;
			}
			final String name = trimmed.substring(lastSeparator + 1);
			if (name.startsWith(WINDOWS_SERVICE_PREFIX) && !services.contains(name)) {
				services.add(name);
			}
		}
		services.sort(String::compareTo);
		return services;
	}

	/**
	 * Indicates whether the given service is currently running.
	 *
	 * @param serviceName the service (or unit) name
	 * @return {@code true} when the service is reported running
	 */
	private boolean isRunning(final String serviceName) {
		if (windows.getAsBoolean()) {
			return outputReader
				.readLines(List.of("sc", "query", serviceName))
				.stream()
				.anyMatch(line -> line.toUpperCase(Locale.ROOT).contains("RUNNING"));
		}
		return outputReader
			.readLines(List.of("systemctl", "is-active", serviceName))
			.stream()
			.anyMatch(line -> "active".equalsIgnoreCase(line.trim()));
	}
}
