package org.metricshub.agent.opamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, DeploymentKind.DEB);

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

		final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, null);

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
			final AgentDescription description = OpAmpAgentDescriptionMapper.map(agentInfo, kind);
			assertEquals(
				Optional.of(kind.name().toLowerCase(java.util.Locale.ROOT)),
				attributeValue(description.getNonIdentifyingAttributesList(), "installer.type")
			);
		}
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
