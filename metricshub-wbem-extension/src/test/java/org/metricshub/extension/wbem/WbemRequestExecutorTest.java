package org.metricshub.extension.wbem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.wbem.client.WbemExecutor;
import org.metricshub.wbem.client.WbemQueryResult;
import org.metricshub.wbem.javax.wbem.WBEMException;
import org.mockito.MockedStatic;

class WbemRequestExecutorTest {

	private static final String HOST_NAME = "test-host" + UUID.randomUUID();
	private static final String USERNAME = "testUser";
	private static final String PASSWORD = "testPassword";
	private static final String NAMESPACE = "testNamespace";
	private static final String QUERY = "testQuery";

	WbemRequestExecutor wbemRequestExecutor = new WbemRequestExecutor();

	@Test
	void testIsAcceptableException() {
		assertFalse(wbemRequestExecutor.isAcceptableException(null));
		assertFalse(wbemRequestExecutor.isAcceptableException(new Exception()));
		assertFalse(wbemRequestExecutor.isAcceptableException(new Exception(new Exception())));

		assertFalse(wbemRequestExecutor.isAcceptableException(new WBEMException("other")));
		assertFalse(wbemRequestExecutor.isAcceptableException(new WBEMException(0)));
		assertTrue(wbemRequestExecutor.isAcceptableException(new WBEMException(WBEMException.CIM_ERR_NOT_FOUND)));
		assertTrue(wbemRequestExecutor.isAcceptableException(new WBEMException(WBEMException.CIM_ERR_INVALID_NAMESPACE)));
		assertTrue(wbemRequestExecutor.isAcceptableException(new WBEMException(WBEMException.CIM_ERR_INVALID_CLASS)));

		assertTrue(
			wbemRequestExecutor.isAcceptableException(new Exception(new WBEMException(WBEMException.CIM_ERR_NOT_FOUND)))
		);
	}

	@Test
	void testDoWbemQuery() throws ClientException {
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			final WbemConfiguration wbemConfiguration = WbemConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.timeout(120L)
				.build();

			final List<String> properties = Arrays.asList("value1a", "value2a", "value3a");

			final List<List<String>> values = Arrays.asList(
				Arrays.asList("value1a", "value2a", "value3a"),
				Arrays.asList("value1b", "value2b", "value3b")
			);

			assertThrows(
				ClientException.class,
				() -> wbemRequestExecutor.doWbemQuery(HOST_NAME, wbemConfiguration, QUERY, NAMESPACE)
			);

			WbemQueryResult wbemQueryResult = new WbemQueryResult(properties, values);

			wbemExecutorMock
				.when(() ->
					WbemExecutor.executeWql(
						any(URL.class),
						anyString(),
						anyString(),
						eq(PASSWORD.toCharArray()),
						anyString(),
						anyInt(),
						eq(null)
					)
				)
				.thenReturn(wbemQueryResult);

			assertEquals(values, wbemRequestExecutor.doWbemQuery(HOST_NAME, wbemConfiguration, QUERY, NAMESPACE));
		}
	}

	@Test
	void testExecuteWbemRecordEnabled(@TempDir final Path tempDir) throws Exception {
		WbemRecorder.clearInstances();
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			final WbemConfiguration wbemConfiguration = WbemConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.timeout(120L)
				.build();

			final List<String> properties = Arrays.asList("value1a", "value2a");
			final List<List<String>> values = Arrays.asList(Arrays.asList("value1a", "value2a"));
			final WbemQueryResult wbemQueryResult = new WbemQueryResult(properties, values);

			wbemExecutorMock
				.when(() ->
					WbemExecutor.executeWql(
						any(URL.class),
						anyString(),
						anyString(),
						eq(PASSWORD.toCharArray()),
						anyString(),
						anyInt(),
						eq(null)
					)
				)
				.thenReturn(wbemQueryResult);

			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.recordOutputDirectory(tempDir.toString())
				.build();

			final List<List<String>> result = wbemRequestExecutor.executeWbem(
				HOST_NAME,
				wbemConfiguration,
				QUERY,
				NAMESPACE,
				telemetryManager,
				null
			);

			assertEquals(values, result);
			final Path wbemDir = tempDir.resolve(WbemRecorder.WBEM_SUBDIR);
			final Path imageFile = wbemDir.resolve(WbemRecorder.IMAGE_YAML);
			assertTrue(Files.isRegularFile(imageFile));
			assertNotNull(Files.readString(imageFile));
		}
	}
}
