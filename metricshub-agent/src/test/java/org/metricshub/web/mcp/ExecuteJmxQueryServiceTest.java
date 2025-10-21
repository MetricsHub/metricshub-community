package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.jmx.JmxConfiguration;
import org.metricshub.extension.jmx.JmxExtension;
import org.metricshub.extension.jmx.JmxRequestExecutor;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteJmxQueryServiceTest {

	/**
	 * A hostname where the JMX request is executed.
	 */
	private static final String HOSTNAME = "hostname";

	/**
	 * Sample MBean object name used in tests.
	 */
	private static final String OBJECT_NAME = "org.apache.cassandra.db:type=StorageService";

	/**
	 * Comma-separated list of MBean attributes used in tests.
	 */
	private static final String OBJECT_ATTRIBUTES = "LiveNodes , MovingNodes";

	/**
	 * Comma-separated key property names used in tests (e.g., JMX query filters).
	 */
	private static final String KEY_PROPERTIES = "scope, path";

	/**
	 * A Timeout for the query execution
	 */
	private static final Long TIMEOUT = 10L;

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteJmxQueryService jmxQueryService;

	@InjectMocks
	private JmxExtension jmxExtension;

	@Mock
	private JmxRequestExecutor jmxRequestExecutorMock;

	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Creating an extension manager with the JMX Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(jmxExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Injecting the agent context holder in the JMX query service.
		jmxQueryService = new ExecuteJmxQueryService(agentContextHolder);
	}

	@Test
	void testExecuteJmxRequestWithoutConfiguration() {
		setup();
		// creating a host configuration without configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Calling execute query
		final QueryResponse result = jmxQueryService.executeQuery(
			HOSTNAME,
			OBJECT_NAME,
			OBJECT_ATTRIBUTES,
			KEY_PROPERTIES,
			TIMEOUT
		);

		assertNotNull(result, "Result should not be null when executing a query");

		assertEquals(
			"No valid configuration found for JMX on %s.".formatted(HOSTNAME),
			result.getIsError(),
			() -> "Unexpected error message when host has no configurations. "
		);
	}

	@Test
	void testExecuteJmxRequestWithoutExtension() {
		setup();
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final QueryResponse result = jmxQueryService.executeQuery(
			HOSTNAME,
			OBJECT_NAME,
			OBJECT_ATTRIBUTES,
			KEY_PROPERTIES,
			TIMEOUT
		);

		assertNull(result.getResponse(), () -> "Response shouldn't be null.");
		assertEquals(
			"No Extension found for JMX.",
			result.getIsError(),
			() -> "Unexpected error message when the JMX extension isn't found"
		);
	}

	@Test
	void testExecuteJmxRequest() throws Exception {
		setup();
		// Creating a JMX Configuration for the host
		JmxConfiguration jmxConfiguration = JmxConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the JMX configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(JmxConfiguration.class, jmxConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// List of values resulted from the JMX request.
		final List<String> values = List.of("scopeVal", "pathVal", "LiveNodesVal", "MovingNodesVal");

		// Mocking fetchMBean query on JMX request executor
		when(
			jmxRequestExecutorMock.fetchMBean(
				any(JmxConfiguration.class),
				eq(OBJECT_NAME),
				eq(List.of("LiveNodes", "MovingNodes")),
				eq(List.of("scope", "path"))
			)
		)
			.thenReturn(List.of(values));

		final QueryResponse result = jmxQueryService.executeQuery(
			HOSTNAME,
			OBJECT_NAME,
			OBJECT_ATTRIBUTES,
			KEY_PROPERTIES,
			TIMEOUT
		);

		assertEquals(
			TextTableHelper.generateTextTable("scope;path;LiveNodes;MovingNodes", List.of(values)),
			result.getResponse(),
			() -> "JMX response mismatch for mocked values [scopeVal, pathVal, LiveNodesVal, MovingNodesVal]"
		);
	}

	@Test
	void testExecuteJmxResponseExtensionThrows() throws Exception {
		// Setup the mocks
		setup();

		// Creating a JMX Configuration for the host
		JmxConfiguration jmxConfiguration = JmxConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the JMX configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(JmxConfiguration.class, jmxConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Make the executor throw when the extension tries to execute
		when(
			jmxRequestExecutorMock.fetchMBean(
				any(JmxConfiguration.class),
				eq(OBJECT_NAME),
				eq(List.of("LiveNodes", "MovingNodes")),
				eq(List.of("scope", "path"))
			)
		)
			.thenThrow(new IllegalArgumentException("An error has occurred"));

		// Call the execute query method
		QueryResponse result = jmxQueryService.executeQuery(
			HOSTNAME,
			OBJECT_NAME,
			OBJECT_ATTRIBUTES,
			KEY_PROPERTIES,
			TIMEOUT
		);

		// Assertions
		assertNotNull(result.getIsError(), () -> "Error message should be returned when an exception is throws");
		assertTrue(
			result.getIsError().contains("An error has occurred"),
			() -> "Error message should contain 'An error has occurred'"
		);
	}

	@Test
	void testNormalizeElements() {
		// trims tokens and drops empties
		final ArrayNode array = ExecuteJmxQueryService.normalizeElements("  A , , B,  C  , ");
		assertEquals(3, array.size(), () -> "Should keep exactly 3 non empty tokens");
		assertEquals("A", array.get(0).asText(), () -> "First token should be 'A'");
		assertEquals("B", array.get(1).asText(), () -> "Second token should be 'B'");
		assertEquals("C", array.get(2).asText(), () -> "Third token should be 'C'");

		// empty string -> empty array
		assertEquals(
			0,
			ExecuteJmxQueryService.normalizeElements("").size(),
			() -> "Empty input should produce empty array"
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> ExecuteJmxQueryService.normalizeElements(null),
			() -> "Null input should throw an Illegal Argument Exception"
		);
	}

	@Test
	void testCreateQueryNode() {
		// Attributes only
		JsonNode queryNode = ExecuteJmxQueryService.createQueryNode("java.lang:type=Memory", "HeapMemoryUsage, Uptime", "");
		assertEquals("java.lang:type=Memory", queryNode.get("objectName").asText(), () -> "objectName must match");
		assertEquals(2, queryNode.get("attributes").size(), () -> "Should have 2 attributes");
		assertEquals(0, queryNode.get("keyProperties").size(), () -> "keyProperties should be empty");

		// Key properties only
		queryNode = ExecuteJmxQueryService.createQueryNode("java.lang:type=GC,name=*", "", "name, type");
		assertEquals(0, queryNode.get("attributes").size(), () -> "attributes should be empty");
		assertEquals(2, queryNode.get("keyProperties").size(), () -> "Should have 2 key properties");

		// Both empty, an exception should be thrown
		assertThrows(
			IllegalArgumentException.class,
			() -> ExecuteJmxQueryService.createQueryNode("domain:type=Something", " , ", "  , "),
			() -> "Should throw when both attributes and keyProperties are empty"
		);
	}
}
