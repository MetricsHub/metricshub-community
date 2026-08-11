package org.metricshub.agent.upgrade.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceNameResolverTest {

	@TempDir
	Path tempDir;

	/**
	 * Resolver behaving as if it ran on Linux, discovering units in the temporary directory.
	 */
	private ServiceNameResolver linuxResolver(final CommandOutputReader reader) {
		return new ServiceNameResolver(reader, List.of(tempDir.toString()), () -> false);
	}

	/**
	 * Resolver behaving as if it ran on Windows.
	 */
	private ServiceNameResolver windowsResolver(final CommandOutputReader reader) {
		return new ServiceNameResolver(reader, List.of(tempDir.toString()), () -> true);
	}

	private void writeUnit(final String name) throws Exception {
		Files.writeString(tempDir.resolve(name), "[Unit]");
	}

	private static CommandOutputReader noOutput() {
		return command -> List.of();
	}

	@Test
	void configuredServiceNameShouldWin() throws Exception {
		writeUnit("metricshub-community-service.service");

		assertEquals(
			"metricshub-enterprise-service.service",
			linuxResolver(noOutput()).resolve("  metricshub-enterprise-service.service  ")
		);
	}

	@Test
	void communityUnitShouldBeDiscovered() throws Exception {
		writeUnit("metricshub-community-service.service");

		assertEquals("metricshub-community-service.service", linuxResolver(noOutput()).resolve(null));
	}

	@Test
	void enterpriseUnitShouldBeDiscovered() throws Exception {
		writeUnit("metricshub-enterprise-service.service");

		assertEquals("metricshub-enterprise-service.service", linuxResolver(noOutput()).resolve(""));
	}

	@Test
	void unrelatedUnitsShouldBeIgnored() throws Exception {
		writeUnit("metricshub-community-service.service");
		writeUnit("some-other.service");
		writeUnit("metricshub-agent.service");

		assertEquals("metricshub-community-service.service", linuxResolver(noOutput()).resolve(null));
	}

	@Test
	void sideBySideEditionsShouldResolveToTheRunningOne() throws Exception {
		writeUnit("metricshub-community-service.service");
		writeUnit("metricshub-enterprise-service.service");

		// Only the Enterprise unit is active on this host
		final ServiceNameResolver resolver = linuxResolver(command ->
			command.contains("metricshub-enterprise-service.service") ? List.of("active") : List.of("inactive")
		);

		assertEquals("metricshub-enterprise-service.service", resolver.resolve(null));
	}

	@Test
	void windowsServicesShouldBeDiscoveredFromTheRegistry() {
		final ServiceNameResolver resolver = windowsResolver(command ->
			command.contains("query")
				? List.of(
						"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\MetricsHub Enterprise",
						"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\MetricsHubHelper"
					)
				: List.of()
		);

		assertEquals("MetricsHub Enterprise", resolver.resolve(null));
	}

	@Test
	void sideBySideWindowsServicesShouldResolveToTheRunningOne() {
		final ServiceNameResolver resolver = windowsResolver(command -> {
			if (command.contains("query") && command.contains("MetricsHub*")) {
				return List.of(
					"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\MetricsHub Community",
					"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\MetricsHub Enterprise"
				);
			}
			// Only the Enterprise service is running
			return command.contains("MetricsHub Enterprise") ? List.of("        STATE              : 4  RUNNING") : List.of();
		});

		assertEquals("MetricsHub Enterprise", resolver.resolve(null));
	}

	@Test
	void missingServiceShouldFailExplicitly() {
		final ServiceNameResolver resolver = linuxResolver(noOutput());

		final IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver.resolve(null));
		assertTrue(failure.getMessage().contains("upgrade.serviceName"));
	}
}
