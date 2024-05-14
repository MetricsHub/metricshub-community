package org.sentrysoftware.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.sentrysoftware.metricshub.extension.http.HttpExtension.HTTP_UP_METRIC;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.common.HttpMethod;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.extension.http.utils.HttpRequest;

@ExtendWith(MockitoExtension.class)
class HttpExtensionTest {

	@Mock
	private HttpRequestExecutor httpRequestExecutorMock;

	@InjectMocks
	private HttpExtension httpExtension;

	private TelemetryManager telemetryManager;

	private static final String TEST_URL = "/test";
	private static final String HTTP_CRITERION_TYPE = "http";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final String CONNECTOR_ID = "connector";
	private static final String TEST_BODY = "test body";
	private static final String RESULT = "result";
	private static final String ERROR = "error";
	private static final String HTTP_GET = "GET";

	/**
	 * Creates a TelemetryManager instance with an HTTP configuration.
	 */
	void initHttp() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
						.build()
				)
				.build();
	}

	@Test
	void testCheckHttpDownHealth() {
		initHttp();

		// Mock HTTP protocol health check response
		doReturn(null)
			.when(httpRequestExecutorMock)
			.executeHttp(any(HttpRequest.class), anyBoolean(), any(TelemetryManager.class));

		httpExtension.checkProtocol(telemetryManager);

		assertEquals(HttpExtension.DOWN, telemetryManager.getEndpointHostMonitor().getMetric(HTTP_UP_METRIC).getValue());
	}

	@Test
	void testCheckHttpUpHealth() {
		initHttp();

		// Mock HTTP protocol health check response
		doReturn("success")
			.when(httpRequestExecutorMock)
			.executeHttp(any(HttpRequest.class), anyBoolean(), any(TelemetryManager.class));

		httpExtension.checkProtocol(telemetryManager);

		assertEquals(HttpExtension.UP, telemetryManager.getEndpointHostMonitor().getMetric(HTTP_UP_METRIC).getValue());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(httpExtension.isValidConfiguration(HttpConfiguration.builder().build()));
		assertFalse(
			httpExtension.isValidConfiguration(
				new IConfiguration() {
					@Override
					public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertFalse(httpExtension.getSupportedSources().isEmpty());
		assertTrue(httpExtension.getSupportedSources().contains(HttpSource.class));
	}

	@Test
	void testGetSupportedCriteria() {
		assertFalse(httpExtension.getSupportedCriteria().isEmpty());
		assertTrue(httpExtension.getSupportedCriteria().contains(HttpCriterion.class));
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertFalse(httpExtension.getConfigurationToSourceMapping().isEmpty());
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(httpExtension.isSupportedConfigurationType(HTTP_CRITERION_TYPE));
		assertFalse(httpExtension.isSupportedConfigurationType("snmp"));
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("https", BooleanNode.FALSE);
		configuration.set("username", new TextNode("username"));
		configuration.set("password", new TextNode("password"));
		configuration.set("port", new IntNode(443));
		configuration.set("timeout", new TextNode("120"));

		assertEquals(
			HttpConfiguration
				.builder()
				.https(false)
				.username("username")
				.password("password".toCharArray())
				.port(443)
				.timeout(120L)
				.build(),
			httpExtension.buildConfiguration(HTTP_CRITERION_TYPE, configuration, value -> value)
		);

		assertEquals(
			HttpConfiguration
				.builder()
				.https(false)
				.username("username")
				.password("password".toCharArray())
				.port(443)
				.timeout(120L)
				.build(),
			httpExtension.buildConfiguration(HTTP_CRITERION_TYPE, configuration, null)
		);
	}

	@Test
	void testProcessCriterionNullTest() {
		initHttp();

		final HttpCriterion httpCriterion = null;

		assertThrows(
			IllegalArgumentException.class,
			() -> httpExtension.processCriterion(httpCriterion, CONNECTOR_ID, telemetryManager)
		);
	}

	@Test
	void testProcessCriterionConfigurationNullTest() {
		initHttp();

		telemetryManager.getHostConfiguration().setConfigurations(Map.of());

		final HttpCriterion httpCriterion = HttpCriterion
			.builder()
			.type(HTTP_CRITERION_TYPE)
			.method(HttpMethod.GET)
			.url(TEST_URL)
			.body(TEST_BODY)
			.resultContent(ResultContent.ALL)
			.expectedResult(RESULT)
			.errorMessage(ERROR)
			.build();

		assertEquals(
			CriterionTestResult.empty(),
			httpExtension.processCriterion(httpCriterion, CONNECTOR_ID, telemetryManager)
		);
	}

	@Test
	void testProcessCriterionRequestWrongResultTest() throws IOException {
		initHttp();

		final HttpCriterion httpCriterion = HttpCriterion
			.builder()
			.type(HTTP_CRITERION_TYPE)
			.method(HttpMethod.GET)
			.url(TEST_URL)
			.body(TEST_BODY)
			.resultContent(ResultContent.ALL)
			.expectedResult(RESULT)
			.errorMessage(ERROR)
			.build();

		final String result = "Something went Wrong";
		final HttpRequest httpRequest = HttpRequest
			.builder()
			.hostname(HOST_NAME)
			.method(HTTP_GET)
			.url(httpCriterion.getUrl())
			.header(httpCriterion.getHeader(), Map.of(), CONNECTOR_ID, HOST_NAME)
			.body(httpCriterion.getBody(), Map.of(), CONNECTOR_ID, HOST_NAME)
			.httpConfiguration(
				(HttpConfiguration) telemetryManager.getHostConfiguration().getConfigurations().get(HttpConfiguration.class)
			)
			.resultContent(httpCriterion.getResultContent())
			.authenticationToken(httpCriterion.getAuthenticationToken())
			.build();
		doReturn(result).when(httpRequestExecutorMock).executeHttp(httpRequest, false, telemetryManager);

		final String message = String.format(
			"Hostname %s - HTTP test failed - " +
			"The result (%s) returned by the HTTP test did not match the expected result (%s)." +
			"Expected value: %s - returned value %s.",
			HOST_NAME,
			result,
			RESULT,
			RESULT,
			result
		);
		final CriterionTestResult criterionTestResult = httpExtension.processCriterion(
			httpCriterion,
			CONNECTOR_ID,
			telemetryManager
		);

		assertEquals(result, criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessCriterionOk() throws IOException {
		initHttp();

		final HttpCriterion httpCriterion = HttpCriterion
			.builder()
			.type(HTTP_CRITERION_TYPE)
			.method(HttpMethod.GET)
			.url(TEST_URL)
			.body(TEST_BODY)
			.resultContent(ResultContent.ALL)
			.expectedResult(RESULT)
			.errorMessage(ERROR)
			.build();

		final HttpRequest httpRequest = HttpRequest
			.builder()
			.hostname(HOST_NAME)
			.method(HTTP_GET)
			.url(httpCriterion.getUrl())
			.header(httpCriterion.getHeader(), Map.of(), CONNECTOR_ID, HOST_NAME)
			.body(httpCriterion.getBody(), Map.of(), CONNECTOR_ID, HOST_NAME)
			.httpConfiguration(
				(HttpConfiguration) telemetryManager.getHostConfiguration().getConfigurations().get(HttpConfiguration.class)
			)
			.resultContent(httpCriterion.getResultContent())
			.authenticationToken(httpCriterion.getAuthenticationToken())
			.build();
		doReturn(RESULT).when(httpRequestExecutorMock).executeHttp(httpRequest, false, telemetryManager);

		final String message = String.format("Hostname %s - HTTP test succeeded. Returned result: result.", HOST_NAME);
		final CriterionTestResult criterionTestResult = httpExtension.processCriterion(
			httpCriterion,
			CONNECTOR_ID,
			telemetryManager
		);

		assertEquals(RESULT, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessHttpSourceOk() {
		initHttp();

		doReturn(RESULT)
			.when(httpRequestExecutorMock)
			.executeHttp(any(HttpRequest.class), eq(true), any(TelemetryManager.class));
		final SourceTable actual = httpExtension.processSource(
			HttpSource.builder().url(TEST_URL).method(HttpMethod.GET).build(),
			CONNECTOR_ID,
			telemetryManager
		);

		final SourceTable expected = SourceTable.builder().rawData(RESULT).build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessHttpSourceThrowsException() {
		initHttp();

		doThrow(new RuntimeException("excepton"))
			.when(httpRequestExecutorMock)
			.executeHttp(any(HttpRequest.class), eq(true), any(TelemetryManager.class));
		final SourceTable actual = httpExtension.processSource(
			HttpSource.builder().url(TEST_URL).method(HttpMethod.GET).build(),
			CONNECTOR_ID,
			telemetryManager
		);

		final SourceTable expected = SourceTable.empty();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessHttpSourceEmptyResult() {
		initHttp();

		{
			doReturn(null)
				.when(httpRequestExecutorMock)
				.executeHttp(any(HttpRequest.class), eq(true), any(TelemetryManager.class));
			final SourceTable actual = httpExtension.processSource(
				HttpSource.builder().url(TEST_URL).method(HttpMethod.GET).build(),
				CONNECTOR_ID,
				telemetryManager
			);

			final SourceTable expected = SourceTable.empty();
			assertEquals(expected, actual);
		}

		{
			doReturn("")
				.when(httpRequestExecutorMock)
				.executeHttp(any(HttpRequest.class), eq(true), any(TelemetryManager.class));
			final SourceTable actual = httpExtension.processSource(
				HttpSource.builder().url(TEST_URL).method(HttpMethod.GET).build(),
				CONNECTOR_ID,
				telemetryManager
			);

			final SourceTable expected = SourceTable.empty();
			assertEquals(expected, actual);
		}
	}

	@Test
	void testProcessHttpSourceNoHttpConfiguration() {
		initHttp();

		telemetryManager.getHostConfiguration().setConfigurations(Map.of());

		assertEquals(
			SourceTable.empty(),
			httpExtension.processSource(
				HttpSource.builder().url(TEST_URL).method(HttpMethod.GET).build(),
				CONNECTOR_ID,
				telemetryManager
			)
		);
	}
}
