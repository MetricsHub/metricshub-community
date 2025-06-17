package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

class JmxCriterionProcessorTest {

	private JmxRequestExecutor jmxRequestExecutor;
	private JmxCriterionProcessor processor;
	private TelemetryManager telemetryManager;

	@BeforeEach
	void setup() {
		jmxRequestExecutor = mock(JmxRequestExecutor.class);
		processor = new JmxCriterionProcessor(jmxRequestExecutor);
		telemetryManager = mock(TelemetryManager.class);
	}

	@Test
	void testShouldReturnErrorIfCriterionIsNull() {
		final CriterionTestResult result = processor.process(null, "connector123", telemetryManager);

		assertFalse(result.isSuccess(), "Expected result to be unsuccessful");
		assertTrue(result.getMessage().contains("Malformed criterion"), "Expected error message about malformed criterion");
	}

	@Test
	void testShouldReturnErrorIfJmxConfigurationIsMissing() {
		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of());
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxCriterion criterion = new JmxCriterion();
		final CriterionTestResult result = processor.process(criterion, "connector123", telemetryManager);

		assertFalse(result.isSuccess(), "Expected result to be unsuccessful");
		assertTrue(result.getMessage().contains("not configured"), "Expected message about missing JMX credentials");
	}

	@Test
	void testShouldReturnSuccessWhenExpectedResultIsNullAndResultIsNonEmpty() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host1").build();
		final List<List<String>> response = List.of(List.of("value1"));

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, config));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxCriterion criterion = new JmxCriterion();
		criterion.setAttributes(List.of("attr"));
		criterion.setObjectName("myObj");

		when(jmxRequestExecutor.fetchBeanInfo(config, "myObj", List.of("attr"), List.of())).thenReturn(response);

		final CriterionTestResult result = processor.process(criterion, "connector123", telemetryManager);

		final String expectedCsv = SourceTable.tableToCsv(response, TABLE_SEP, true);

		assertTrue(result.isSuccess(), "Expected success for non-empty result with no expected match");
		assertEquals(expectedCsv, result.getResult(), "Result should match the CSV-formatted response");
	}

	@Test
	void testShouldReturnFailureWhenExpectedResultIsNullAndResultIsEmpty() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host1").build();
		final List<List<String>> response = List.of();

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, config));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxCriterion criterion = new JmxCriterion();
		criterion.setAttributes(List.of("attr"));
		criterion.setObjectName("myObj");

		when(jmxRequestExecutor.fetchBeanInfo(config, "myObj", List.of("attr"), List.of())).thenReturn(response);

		final CriterionTestResult result = processor.process(criterion, "connector123", telemetryManager);

		assertFalse(result.isSuccess(), "Expected success for non-empty result with no expected match");
		assertTrue(result.getResult().isEmpty(), "Result should match the empty response");
		assertTrue(
			result.getMessage().contains("The JMX test did not return any result."),
			"Expected message about empty result"
		);
	}

	@Test
	void testShouldReturnSuccessWhenRegexMatches() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host2").build();
		final List<List<String>> response = List.of(List.of("UPTIME: OK"));

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, config));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxCriterion criterion = new JmxCriterion();
		criterion.setObjectName("objName");
		criterion.setAttributes(List.of("status"));
		criterion.setExpectedResult(".*uptime.*");

		when(jmxRequestExecutor.fetchBeanInfo(any(), any(), any(), any())).thenReturn(response);

		final CriterionTestResult result = processor.process(criterion, "connector321", telemetryManager);

		assertTrue(result.isSuccess(), "Expected match with regex pattern");
		assertTrue(result.getMessage().toLowerCase().contains("succeeded"), "Message should indicate test success");
	}

	@Test
	void testShouldReturnFailureWhenRegexDoesNotMatch() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host3").build();
		final List<List<String>> response = List.of(List.of("DOWN"));

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, config));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxCriterion criterion = new JmxCriterion();
		criterion.setObjectName("objName");
		criterion.setAttributes(List.of("status"));
		criterion.setExpectedResult(".*OK.*");

		when(jmxRequestExecutor.fetchBeanInfo(any(), any(), any(), any())).thenReturn(response);

		final CriterionTestResult result = processor.process(criterion, "connectorXYZ", telemetryManager);

		assertFalse(result.isSuccess(), "Expected failure because regex did not match result");
		assertTrue(result.getMessage().contains("did not match"), "Message should indicate regex mismatch");
	}

	@Test
	void testShouldReturnErrorOnFetchException() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host4").build();

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, config));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxCriterion criterion = new JmxCriterion();
		criterion.setObjectName("obj");
		criterion.setAttributes(List.of("attr"));

		when(jmxRequestExecutor.fetchBeanInfo(any(), any(), any(), any()))
			.thenThrow(new RuntimeException("Simulated failure"));

		final CriterionTestResult result = processor.process(criterion, "connectorERR", telemetryManager);

		assertFalse(result.isSuccess(), "Expected failure when fetchBeanInfo throws");
		assertTrue(result.getMessage().toLowerCase().contains("simulated"), "Message should include exception message");
	}
}
