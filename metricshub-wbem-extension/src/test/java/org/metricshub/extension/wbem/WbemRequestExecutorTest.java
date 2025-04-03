package org.metricshub.extension.wbem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.metricshub.wbem.client.WbemExecutor;
import org.metricshub.wbem.client.WbemQueryResult;
import org.metricshub.wbem.javax.wbem.WBEMException;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;

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
}
