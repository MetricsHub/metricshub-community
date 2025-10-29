package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.mockito.MockedStatic;

class IpmiRequestExecutorTest {

	final IpmiConfiguration ipmiConfiguration = IpmiConfiguration
		.builder()
		.username("username")
		.password("password".toCharArray())
		.timeout(120L)
		.bmcKey("0x0102")
		.build();

	@Test
	void testExecuteIpmiDetection() throws Exception {
		try (MockedStatic<IpmiClient> ipmiClientMock = mockStatic(IpmiClient.class)) {
			final String expected = "result";

			ipmiClientMock
				.when(() -> IpmiClient.getChassisStatusAsStringResult(any(IpmiClientConfiguration.class)))
				.thenReturn(expected);

			final String hostname = "hostname";

			final String actual = new IpmiRequestExecutor().executeIpmiDetection(hostname, ipmiConfiguration);

			assertEquals(expected, actual);
		}
	}

	@Test
	void testExecuteIpmiDetectionThrowsException() throws Exception {
		try (MockedStatic<IpmiClient> ipmiClientMock = mockStatic(IpmiClient.class)) {
			ipmiClientMock
				.when(() -> IpmiClient.getChassisStatus(any(IpmiClientConfiguration.class)))
				.thenThrow(new InterruptedException());

			final String hostname = "hostname";

			final String result = new IpmiRequestExecutor().executeIpmiDetection(hostname, ipmiConfiguration);

			assertNull(result);
		}
	}

	@Test
	void testExecuteIpmiGetSensors() throws Exception {
		try (MockedStatic<IpmiClient> ipmiClientMock = mockStatic(IpmiClient.class)) {
			final String expected = "result";

			ipmiClientMock
				.when(() -> IpmiClient.getFrusAndSensorsAsStringResult(any(IpmiClientConfiguration.class)))
				.thenReturn(expected);

			final String hostname = "hostname";

			assertEquals(expected, new IpmiRequestExecutor().executeIpmiGetSensors(hostname, ipmiConfiguration));
		}
	}

	@Test
	void testExecuteIpmiGetSensorsThrowsException() {
		try (MockedStatic<IpmiClient> ipmiClientMock = mockStatic(IpmiClient.class)) {
			ipmiClientMock
				.when(() -> IpmiClient.getFrusAndSensorsAsStringResult(any(IpmiClientConfiguration.class)))
				.thenThrow(new InterruptedException());

			final String hostname = "hostname";

			assertThrows(
				InterruptedException.class,
				() -> new IpmiRequestExecutor().executeIpmiGetSensors(hostname, ipmiConfiguration)
			);
		}
	}
}
