package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JmxExtensionTest {

	@Mock
	private JmxRequestExecutor jmxRequestExecutorMock;

	@InjectMocks
	private JmxExtension jmxExtension;

	private ObjectMapper objectMapper;

	@BeforeEach
	void setup() {
		objectMapper = new ObjectMapper();
	}

	@Test
	void testShouldReturnTrueForValidConfigurationType() {
		assertTrue(jmxExtension.isSupportedConfigurationType("jmx"), "Should support 'jmx' type");
	}

	@Test
	void testShouldReturnFalseForInvalidConfigurationType() {
		assertFalse(jmxExtension.isSupportedConfigurationType("snmp"), "Should not support other types");
	}

	@Test
	void testShouldBuildConfigurationSuccessfully() throws Exception {
		final JsonNode jsonNode = objectMapper.readTree("{\"hostname\": \"host\", \"port\": 1099, \"password\": \"enc\"}");
		final UnaryOperator<char[]> decrypt = p -> "dec".toCharArray();

		final JmxConfiguration config = (JmxConfiguration) jmxExtension.buildConfiguration("jmx", jsonNode, decrypt);

		assertEquals("host", config.getHostname(), "Hostname should be parsed");
		assertEquals(1099, config.getPort(), "Port should be parsed");
		assertArrayEquals("dec".toCharArray(), config.getPassword(), "Password should be decrypted");
	}

	@Test
	void testShouldThrowOnInvalidBuildConfiguration() {
		final JsonNode invalidNode = mock(JsonNode.class);
		final UnaryOperator<char[]> decrypt = c -> c;

		assertThrows(
			InvalidConfigurationException.class,
			() -> jmxExtension.buildConfiguration("jmx", invalidNode, decrypt),
			"Should throw on invalid configuration input"
		);
	}

	@Test
	void testShouldReturnEmptyOnMissingJmxConfiguration() {
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);
		when(telemetryManager.getHostConfiguration()).thenReturn(mock(HostConfiguration.class));
		when(telemetryManager.getHostConfiguration().getConfigurations()).thenReturn(Map.of());

		final Optional<Boolean> result = jmxExtension.checkProtocol(telemetryManager);
		assertTrue(result.isEmpty(), "Expected Optional.empty when config is missing");
	}

	@Test
	void testShouldReturnTrueOnValidJmxConfiguration() throws Exception {
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);
		when(telemetryManager.getHostConfiguration()).thenReturn(mock(HostConfiguration.class));
		final JmxConfiguration jmxConfiguration = JmxConfiguration.builder().hostname("localhost").port(1099).build();
		when(telemetryManager.getHostConfiguration().getConfigurations())
			.thenReturn(Map.of(JmxConfiguration.class, jmxConfiguration));

		when(jmxRequestExecutorMock.checkConnection(any(), any())).thenReturn(true);

		final Optional<Boolean> result = jmxExtension.checkProtocol(telemetryManager);
		assertTrue(result.isPresent(), "Expected Optional<Boolean> with true when config is valid");
		assertTrue(result.get(), "Expected true for valid JMX connection check");
	}

	@Test
	void testShouldThrowForInvalidSourceType() {
		final Source invalidSource = mock(Source.class);
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);

		final Exception exception = assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.processSource(invalidSource, "conn", telemetryManager),
			"Should throw for unsupported source type"
		);
		assertTrue(exception.getMessage().contains("Cannot process source"), "Should throw for unsupported source type");
	}

	@Test
	void testShouldProcessSourceSuccessfully() throws Exception {
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);

		final JmxSource jmxSource = new JmxSource();
		jmxSource.setObjectName("java.lang:type=Memory");
		jmxSource.setAttributes(List.of("HeapMemoryUsage"));
		jmxSource.setKeyProperties(List.of("type"));

		// Mock behavior: delegate to JmxSourceProcessor, which internally uses our mocked executor
		final List<List<String>> table = List.of(List.of("value1"));
		when(jmxRequestExecutorMock.fetchMBean(any(), anyString(), anyList(), anyList(), any())).thenReturn(table);

		final JmxConfiguration jmxConfiguration = JmxConfiguration.builder().hostname("my-host").build();
		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, jmxConfiguration));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final var result = jmxExtension.processSource(jmxSource, "connId", telemetryManager);

		assertEquals(
			SourceTable.builder().table(table).rawData(SourceTable.tableToCsv(table, TABLE_SEP, true)).build(),
			result,
			"Expected SourceTable with fetched data should match"
		);
	}

	@Test
	void testShouldProcessCriterionSuccessfully() throws Exception {
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);

		final JmxCriterion jmxCriterion = new JmxCriterion();
		jmxCriterion.setObjectName("java.lang:type=Runtime");
		jmxCriterion.setAttributes(List.of("Uptime"));
		jmxCriterion.setExpectedResult(".*");

		// Prepare configuration needed for the processor to work
		final JmxConfiguration jmxConfiguration = JmxConfiguration.builder().hostname("my-host").build();
		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, jmxConfiguration));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		// Mock response from fetchBeanInfo
		when(jmxRequestExecutorMock.fetchMBean(any(), anyString(), anyList(), anyList(), any()))
			.thenReturn(List.of(List.of("12345")));

		final var result = jmxExtension.processCriterion(jmxCriterion, "connId", telemetryManager, true);

		assertNotNull(result, "CriterionTestResult should not be null");
		assertTrue(result.isSuccess(), "Criterion processing should succeed with valid data");
	}

	@Test
	void testShouldThrowForInvalidCriterionType() {
		final Criterion invalidCriterion = mock(Criterion.class);
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);

		final Exception exception = assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.processCriterion(invalidCriterion, "conn", telemetryManager, true),
			"Should throw for unsupported criterion type"
		);
		assertTrue(
			exception.getMessage().contains("Cannot process criterion"),
			"Should throw for unsupported criterion type"
		);
	}

	@Test
	void testShouldThrowForInvalidQueryInput() {
		final IConfiguration configuration = new JmxConfiguration();
		final JsonNode jsonNode = objectMapper.createObjectNode(); // Missing "objecttName"

		assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.executeQuery(configuration, jsonNode),
			"Should throw for missing objectName"
		);
	}

	@Test
	void testShouldExecuteQuerySuccessfullyWithMockedExecutor() throws Exception {
		// Prepare configuration
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testHost").build();

		// JSON with correct spelling ("objecttName" as used in source)
		final JsonNode query = objectMapper.readTree(
			"""
			    {
			      "objectName": "myDomain:type=MyType,key1=*",
			      "attributes": ["Attr1", "Attr2"],
			      "keyProperties": ["key1"]
			    }
			"""
		);

		final List<List<String>> rows = List.of(List.of("Key1Val", "Attr1Val", "Attr2Val"));

		// Mock fetchBeanInfo result
		when(jmxRequestExecutorMock.fetchMBean(eq(config), anyString(), anyList(), anyList(), any())).thenReturn(rows);

		// Run executeQuery
		final String table = jmxExtension.executeQuery(config, query);

		assertNotNull(table, "Table output should not be null");
		assertTrue(
			table.contains("Attr1Val") && table.contains("Attr1"),
			"Output should contain values and column headers"
		);
	}

	@Test
	void testShouldReturnSupportedSources() {
		final var sources = jmxExtension.getSupportedSources();
		assertNotNull(sources, "Supported sources should not be null");
		assertTrue(sources.contains(JmxSource.class), "Should support JmxSource");
	}

	@Test
	void testShouldReturnSupportedCriteria() {
		final var criteria = jmxExtension.getSupportedCriteria();
		assertNotNull(criteria, "Supported criteria should not be null");
		assertTrue(criteria.contains(JmxCriterion.class), "Should support JmxCriterion");
	}

	@Test
	void testShouldReturnConfigurationToSourceMapping() {
		final var mapping = jmxExtension.getConfigurationToSourceMapping();
		assertNotNull(mapping, "Mapping should not be null");
		assertTrue(mapping.containsKey(JmxConfiguration.class), "Should map JmxConfiguration");
		assertTrue(
			mapping.get(JmxConfiguration.class).contains(JmxSource.class),
			"Should map JmxConfiguration to JmxSource"
		);
	}

	@Test
	void testShouldReturnIdentifier() {
		assertEquals("jmx", jmxExtension.getIdentifier(), "Identifier should be 'jmx'");
	}

	@Test
	void testShouldValidateCorrectConfigurationType() {
		final IConfiguration config = new JmxConfiguration();
		assertTrue(jmxExtension.isValidConfiguration(config), "Should validate JmxConfiguration as supported");
	}

	@Test
	void testShouldRejectInvalidConfigurationType() {
		final IConfiguration invalidConfig = mock(IConfiguration.class);
		assertFalse(jmxExtension.isValidConfiguration(invalidConfig), "Should reject unsupported configuration types");
	}

	@Test
	void testShouldReturnFalseWhenCheckProtocolThrows() throws Exception {
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);
		final JmxConfiguration jmxConfiguration = JmxConfiguration.builder().hostname("failhost").port(1099).build();
		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, jmxConfiguration));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		when(jmxRequestExecutorMock.checkConnection(any(), any())).thenThrow(new RuntimeException("Connection failed"));

		final Optional<Boolean> result = jmxExtension.checkProtocol(telemetryManager);
		assertTrue(result.isPresent(), "Expected Optional<Boolean> with false on exception");
		assertFalse(result.get(), "Expected false when health check throws");
	}

	@Test
	void testShouldThrowForNullSourceType() {
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);

		final Exception exception = assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.processSource(null, "conn", telemetryManager),
			"Should throw for null source"
		);
		assertTrue(exception.getMessage().contains("<null>"), "Error message should mention null source");
	}

	@Test
	void testShouldThrowForNullCriterionType() {
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);

		final Exception exception = assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.processCriterion(null, "conn", telemetryManager, true),
			"Should throw for null criterion"
		);
		assertTrue(exception.getMessage().contains("<null>"), "Error message should mention null criterion");
	}

	@Test
	void testShouldThrowForInvalidConfigurationTypeInExecuteQuery() {
		final IConfiguration invalidConfig = mock(IConfiguration.class);
		final JsonNode query = objectMapper.createObjectNode();

		assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.executeQuery(invalidConfig, query),
			"Should throw for non-JMX configuration in executeQuery"
		);
	}

	@Test
	void testShouldThrowForBlankObjectNameInExecuteQuery() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testHost").build();
		final JsonNode query = objectMapper.readTree("{\"objectName\": \"   \"}");

		assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.executeQuery(config, query),
			"Should throw for blank objectName"
		);
	}

	@Test
	void testShouldThrowForNullObjectNameValueInExecuteQuery() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testHost").build();
		final JsonNode query = objectMapper.readTree("{\"objectName\": null}");

		assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.executeQuery(config, query),
			"Should throw for null objectName value"
		);
	}

	@Test
	void testShouldThrowForEmptyAttributesAndKeyPropertiesInExecuteQuery() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testHost").build();
		final JsonNode query = objectMapper.readTree(
			"{\"objectName\": \"java.lang:type=Runtime\", \"attributes\": [], \"keyProperties\": []}"
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> jmxExtension.executeQuery(config, query),
			"Should throw when both attributes and keyProperties are empty"
		);
	}

	@Test
	void testShouldBuildConfigurationWithNullDecrypt() throws Exception {
		final JsonNode jsonNode = objectMapper.readTree("{\"hostname\": \"host\", \"port\": 1099, \"password\": \"pwd\"}");

		final JmxConfiguration config = (JmxConfiguration) jmxExtension.buildConfiguration("jmx", jsonNode, null);

		assertEquals("host", config.getHostname(), "Hostname should be parsed");
		assertArrayEquals("pwd".toCharArray(), config.getPassword(), "Password should remain unchanged with null decrypt");
	}

	@Test
	void testShouldBuildConfigurationWithNullPassword() throws Exception {
		final JsonNode jsonNode = objectMapper.readTree("{\"hostname\": \"host\", \"port\": 1099}");
		final UnaryOperator<char[]> decrypt = p -> "decrypted".toCharArray();

		final JmxConfiguration config = (JmxConfiguration) jmxExtension.buildConfiguration("jmx", jsonNode, decrypt);

		assertEquals("host", config.getHostname(), "Hostname should be parsed");
		assertNull(config.getPassword(), "Password should be null when not set");
	}

	@Test
	void testShouldReturnNewObjectMapper() {
		final var mapper = jmxExtension.newObjectMapper();
		assertNotNull(mapper, "ObjectMapper should not be null");
	}

	@Test
	void testShouldExecuteQueryWithOnlyKeyProperties() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testHost").build();

		final JsonNode query = objectMapper.readTree(
			"""
			    {
			      "objectName": "java.lang:type=GarbageCollector,name=*",
			      "keyProperties": ["name"]
			    }
			"""
		);

		final List<List<String>> rows = List.of(List.of("G1YoungGen"));
		when(jmxRequestExecutorMock.fetchMBean(eq(config), anyString(), anyList(), anyList(), any())).thenReturn(rows);

		final String table = jmxExtension.executeQuery(config, query);

		assertNotNull(table, "Table output should not be null");
		assertTrue(table.contains("G1YoungGen"), "Output should contain key property value");
	}
}
