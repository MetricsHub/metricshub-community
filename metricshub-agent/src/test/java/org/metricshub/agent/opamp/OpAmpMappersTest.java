package org.metricshub.agent.opamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.OpAmpConfig;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.upgrade.runner.DeploymentKind;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.ComponentHealth;
import org.metricshub.opamp.proto.KeyValue;
import org.metricshub.web.dto.ApplicationStatus;
import org.metricshub.web.dto.ApplicationStatus.Status;

class OpAmpMappersTest {

	private static Optional<String> attributeValue(final java.util.List<KeyValue> attributes, final String key) {
		return attributes
			.stream()
			.filter(keyValue -> keyValue.getKey().equals(key))
			.map(keyValue -> keyValue.getValue().getStringValue())
			.findFirst();
	}

	@Test
	void agentDescriptionShouldCarryIdentityAndSelectionAttributes() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(
			Map.of(
				"service.name",
				"MetricsHub Agent",
				"version",
				"3.9.05",
				"host.name",
				"server-01",
				"os.type",
				"linux",
				"build_number",
				"abcdef12"
			)
		);

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(
			agentInfo,
			AgentConfig.builder().build(),
			DeploymentKind.DEB
		);

		assertEquals(
			Optional.of("MetricsHub Agent"),
			attributeValue(description.getIdentifyingAttributesList(), "service.name")
		);
		assertEquals(Optional.of("3.9.05"), attributeValue(description.getIdentifyingAttributesList(), "service.version"));
		assertEquals(Optional.of("server-01"), attributeValue(description.getIdentifyingAttributesList(), "host.name"));
		assertEquals(Optional.of("linux"), attributeValue(description.getNonIdentifyingAttributesList(), "os.type"));
		assertEquals(
			Optional.of("abcdef12"),
			attributeValue(description.getNonIdentifyingAttributesList(), "build_number")
		);
		assertTrue(attributeValue(description.getNonIdentifyingAttributesList(), "host.arch").isPresent());
		assertEquals(
			Optional.of("deb"),
			attributeValue(description.getNonIdentifyingAttributesList(), "installer.type"),
			"The installer type must be reported lowercase so the server can select the artifact type"
		);
		assertTrue(
			attributeValue(description.getIdentifyingAttributesList(), "installer.type").isEmpty(),
			"The installer type is non-identifying: it may change on reinstallation"
		);
	}

	@Test
	void agentDescriptionShouldSkipMissingAttributes() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(Map.of());

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, null, null);

		assertTrue(attributeValue(description.getIdentifyingAttributesList(), "service.name").isEmpty());
		assertTrue(attributeValue(description.getIdentifyingAttributesList(), "service.version").isEmpty());
		assertTrue(
			attributeValue(description.getNonIdentifyingAttributesList(), "installer.type").isEmpty(),
			"An unknown deployment kind must omit the attribute instead of reporting a wrong one"
		);
	}

	@Test
	void agentDescriptionShouldReportEveryDeploymentKindLowercase() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(Map.of());

		for (final DeploymentKind kind : DeploymentKind.values()) {
			final AgentDescription description = OpAmpAgentDescriptionMapper.map(
				agentInfo,
				AgentConfig.builder().build(),
				kind
			);
			assertEquals(
				Optional.of(kind.name().toLowerCase(java.util.Locale.ROOT)),
				attributeValue(description.getNonIdentifyingAttributesList(), "installer.type")
			);
		}
	}

	@Test
	void agentDescriptionShouldReportEveryPreBuiltAttribute() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(
			Map.of(
				"service.name",
				"MetricsHub Agent",
				"agent.host.name",
				"server-01",
				"host.type",
				"compute",
				"name",
				"MetricsHub Agent",
				"build_date",
				"2026-08-31",
				"cc_version",
				"1.0.13"
			)
		);

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(
			agentInfo,
			AgentConfig.builder().build(),
			DeploymentKind.MSI
		);

		assertEquals(
			Optional.of("server-01"),
			attributeValue(description.getNonIdentifyingAttributesList(), "agent.host.name")
		);
		assertEquals(Optional.of("compute"), attributeValue(description.getNonIdentifyingAttributesList(), "host.type"));
		assertEquals(
			Optional.of("2026-08-31"),
			attributeValue(description.getNonIdentifyingAttributesList(), "build_date")
		);
		assertEquals(Optional.of("1.0.13"), attributeValue(description.getNonIdentifyingAttributesList(), "cc_version"));
		assertTrue(
			attributeValue(description.getNonIdentifyingAttributesList(), "service.name").isEmpty(),
			"An identifying attribute must not be repeated in the non-identifying block"
		);
	}

	@Test
	void agentDescriptionShouldCarryTheConfiguredAttributes() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(
			Map.of("service.name", "MetricsHub Agent", "version", "3.9.05", "host.name", "server-01", "os.type", "linux")
		);

		final AgentConfig agentConfig = AgentConfig.builder()
			.attributes(Map.of("host.name", "configured-host", "site", "data-center-1", "env", "production"))
			.build();

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, agentConfig, DeploymentKind.RPM);

		assertEquals(
			Optional.of("configured-host"),
			attributeValue(description.getIdentifyingAttributesList(), "host.name"),
			"A configured attribute must override the pre-built one"
		);
		assertEquals(
			Optional.of("data-center-1"),
			attributeValue(description.getNonIdentifyingAttributesList(), "site"),
			"A custom configured attribute must be reported as non-identifying"
		);
		assertEquals(Optional.of("production"), attributeValue(description.getNonIdentifyingAttributesList(), "env"));
		assertEquals(Optional.of("linux"), attributeValue(description.getNonIdentifyingAttributesList(), "os.type"));
		assertEquals(Optional.of("rpm"), attributeValue(description.getNonIdentifyingAttributesList(), "installer.type"));
		assertEquals(
			1,
			description
				.getIdentifyingAttributesList()
				.stream()
				.filter(kv -> "host.name".equals(kv.getKey()))
				.count(),
			"An overridden attribute must be reported once"
		);
	}

	@Test
	void agentDescriptionShouldLetTheConfigurationOverrideTheDerivedAttributes() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(Map.of("version", "3.9.05"));

		final AgentConfig agentConfig = AgentConfig.builder()
			.attributes(Map.of("host.arch", "arm64", "installer.type", "archive", "service.version", "3.9.05-custom"))
			.build();

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, agentConfig, DeploymentKind.DOCKER);

		assertEquals(Optional.of("arm64"), attributeValue(description.getNonIdentifyingAttributesList(), "host.arch"));
		assertEquals(
			Optional.of("archive"),
			attributeValue(description.getNonIdentifyingAttributesList(), "installer.type")
		);
		assertEquals(
			Optional.of("3.9.05-custom"),
			attributeValue(description.getIdentifyingAttributesList(), "service.version"),
			"A configured service.version must win over the pre-built version"
		);
		assertTrue(
			attributeValue(description.getNonIdentifyingAttributesList(), "service.version").isEmpty(),
			"The configured service.version must not be duplicated as a non-identifying attribute"
		);
	}

	@Test
	void opAmpAttributesShouldWinOverTheAgentAndPreBuiltOnes() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(
			Map.of(
				"service.name",
				"MetricsHub Agent",
				"version",
				"3.9.05",
				"host.name",
				"pre-built-host",
				"site",
				"pre-built-site"
			)
		);

		final AgentConfig agentConfig = AgentConfig.builder()
			.attributes(Map.of("host.name", "agent-host", "site", "agent-site", "env", "production"))
			.opamp(
				OpAmpConfig.builder()
					.attributes(Map.of("host.name", "opamp-host", "site", "opamp-site", "fleet", "emea"))
					.build()
			)
			.build();

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, agentConfig, DeploymentKind.DEB);

		assertEquals(
			Optional.of("opamp-host"),
			attributeValue(description.getIdentifyingAttributesList(), "host.name"),
			"The opamp: attributes are merged last and must win over the agent-level ones"
		);
		assertEquals(
			Optional.of("opamp-site"),
			attributeValue(description.getNonIdentifyingAttributesList(), "site"),
			"The opamp: attributes are merged last and must win over the agent-level ones"
		);
		assertEquals(
			Optional.of("emea"),
			attributeValue(description.getNonIdentifyingAttributesList(), "fleet"),
			"An attribute defined only under opamp: must be reported"
		);
		assertEquals(
			Optional.of("production"),
			attributeValue(description.getNonIdentifyingAttributesList(), "env"),
			"An agent-level attribute the opamp: section does not override must be kept"
		);
		assertEquals(
			Optional.of("MetricsHub Agent"),
			attributeValue(description.getIdentifyingAttributesList(), "service.name"),
			"A pre-built attribute no layer overrides must be kept"
		);
	}

	@Test
	void opAmpAttributesShouldOverrideTheIdentifyingServiceVersion() {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(Map.of("version", "3.9.05"));

		final AgentConfig agentConfig = AgentConfig.builder()
			.attributes(Map.of("service.version", "agent-version"))
			.opamp(OpAmpConfig.builder().attributes(Map.of("service.version", "opamp-version")).build())
			.build();

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, agentConfig, null);

		assertEquals(
			Optional.of("opamp-version"),
			attributeValue(description.getIdentifyingAttributesList(), "service.version")
		);
	}

	@Test
	void healthShouldReflectAnUpApplication() {
		final ApplicationStatus applicationStatus = ApplicationStatus.builder()
			.status(Status.UP)
			.otelCollectorStatus("running")
			.build();

		final ComponentHealth health = OpAmpHealthMapper.map(applicationStatus);

		assertTrue(health.getHealthy());
		assertEquals("UP", health.getStatus());
		assertTrue(health.getStartTimeUnixNano() > 0);
		final ComponentHealth collector = health.getComponentHealthMapOrThrow(OpAmpHealthMapper.OTEL_COLLECTOR_COMPONENT);
		assertTrue(collector.getHealthy());
		assertEquals("running", collector.getStatus());
	}

	@Test
	void healthShouldReflectADownApplicationAndErroredCollector() {
		final ApplicationStatus applicationStatus = ApplicationStatus.builder()
			.status(Status.DOWN)
			.otelCollectorStatus("errored")
			.build();

		final ComponentHealth health = OpAmpHealthMapper.map(applicationStatus);

		assertFalse(health.getHealthy());
		assertEquals("DOWN", health.getStatus());
		assertFalse(health.getComponentHealthMapOrThrow(OpAmpHealthMapper.OTEL_COLLECTOR_COMPONENT).getHealthy());
	}

	@Test
	void healthShouldOmitTheCollectorComponentWhenUnknown() {
		final ApplicationStatus applicationStatus = ApplicationStatus.builder().status(Status.UP).build();

		final ComponentHealth health = OpAmpHealthMapper.map(applicationStatus);

		assertEquals(0, health.getComponentHealthMapCount());
	}
}
