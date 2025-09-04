package org.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.utils.HttpRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
	private static final String TEST_HEADER = "Content-Type: application/xml";
	private static final String HTTP_SUCCESSFUL_RESPONSE = "Successful HTTP query";

	/**
	 * Creates a TelemetryManager instance with an HTTP configuration.
	 */
	void initHttp() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final HttpConfiguration httpConfiguration = HttpConfiguration.builder().hostname(HOST_NAME).build();

		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

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
				.connectorStore(connectorStore)
				.build();
	}

	@Test
	void testCheckHttpDownHealth() {
		initHttp();

		// Mock HTTP protocol health check response
		doReturn(null)
			.when(httpRequestExecutorMock)
			.executeHttp(any(HttpRequest.class), anyBoolean(), any(TelemetryManager.class));

		Optional<Boolean> result = httpExtension.checkProtocol(telemetryManager);

		// Assert the result
		assertFalse(result.get());
	}

	@Test
	void testCheckHttpUpHealth() {
		initHttp();

		// Mock HTTP protocol health check response
		doReturn("success")
			.when(httpRequestExecutorMock)
			.executeHttp(any(HttpRequest.class), anyBoolean(), any(TelemetryManager.class));

		Optional<Boolean> result = httpExtension.checkProtocol(telemetryManager);

		// Assert the result
		assertTrue(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(httpExtension.isValidConfiguration(HttpConfiguration.builder().build()));
		assertFalse(
			httpExtension.isValidConfiguration(
				new IConfiguration() {
					@Override
					public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

					@Override
					public String getHostname() {
						return null;
					}

					@Override
					public void setHostname(String hostname) {}

					@Override
					public IConfiguration copy() {
						return null;
					}

					@Override
					public void setTimeout(Long timeout) {}
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
	void testProcessCriterionNoHostConfiguration() {
		initHttp();

		telemetryManager.setHostConfiguration(null);

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
	void testProcessCriterionRequestWrongResultTest() {
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
	void testProcessCriterionOk() {
		// Test case where the HTTP request is executed successfully and matches the
		// expected result
		{
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
		// Test case where the HTTP request is executed successfully and there is no expected matching result
		{
			initHttp();

			final HttpCriterion httpCriterion = HttpCriterion
				.builder()
				.type(HTTP_CRITERION_TYPE)
				.method(HttpMethod.GET)
				.url(TEST_URL)
				.body(TEST_BODY)
				.resultContent(ResultContent.ALL)
				.expectedResult(null)
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
	}

	@Test
	void testProcessCriterionNullResultKo() {
		initHttp();

		final HttpCriterion httpCriterion = HttpCriterion
			.builder()
			.type(HTTP_CRITERION_TYPE)
			.method(HttpMethod.GET)
			.url(TEST_URL)
			.body(TEST_BODY)
			.resultContent(ResultContent.ALL)
			.expectedResult(null)
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
		doReturn(null).when(httpRequestExecutorMock).executeHttp(httpRequest, false, telemetryManager);

		final CriterionTestResult criterionTestResult = httpExtension.processCriterion(
			httpCriterion,
			CONNECTOR_ID,
			telemetryManager
		);

		assertFalse(criterionTestResult.isSuccess());
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

	@Test
	void testExecuteQuery() {
		initHttp();
		HttpConfiguration httpConfiguration = (HttpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(HttpConfiguration.class);
		ObjectNode httpQueryConfiguration = JsonNodeFactory.instance.objectNode();
		httpQueryConfiguration.set("hostname", new TextNode(HOST_NAME));
		httpQueryConfiguration.set("method", new TextNode("GET"));
		httpQueryConfiguration.set("url", new TextNode(TEST_URL));
		httpQueryConfiguration.set("header", new TextNode(TEST_HEADER));
		httpQueryConfiguration.set("body", new TextNode(TEST_BODY));

		HttpRequest httpRequest = HttpRequest
			.builder()
			.hostname(HOST_NAME)
			.httpConfiguration(httpConfiguration)
			.method(HTTP_GET)
			.url(TEST_URL)
			.header(TEST_HEADER, Map.of(), "", HOST_NAME)
			.body(TEST_BODY, Map.of(), "", HOST_NAME)
			.resultContent(ResultContent.detect(ResultContent.ALL_WITH_STATUS.getName()))
			.build();

		// Mock HTTP Client response
		doReturn(HTTP_SUCCESSFUL_RESPONSE)
			.when(httpRequestExecutorMock)
			.executeHttp(eq(httpRequest), anyBoolean(), any(TelemetryManager.class));

		final String result = httpExtension.executeQuery(httpConfiguration, httpQueryConfiguration);
		assertEquals(HTTP_SUCCESSFUL_RESPONSE, result);
	}
}
