package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.FrusRequest;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientResults;
import org.metricshub.ipmi.client.IpmiTimeoutException;
import org.metricshub.ipmi.client.SensorsRequest;
import org.mockito.MockedStatic;

class IpmiRequestExecutorTest {

	private static final String HOSTNAME = "hostname";

	final IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
		.username("username")
		.password("password".toCharArray())
		.timeout(120L)
		.bmcKey("0x0102")
		.build();

	private final IpmiClient bmc = mock(IpmiClient.class);

	/**
	 * @return an executor whose clients are {@link #bmc}
	 */
	private IpmiRequestExecutor executor() {
		final IpmiRequestExecutor executor = spy(new IpmiRequestExecutor());
		doReturn(bmc).when(executor).newClient(HOSTNAME, ipmiConfiguration);
		return executor;
	}

	@Test
	void testExecuteIpmiDetection() {
		when(bmc.chassisStatus()).thenReturn(
			IpmiClientResults.chassisStatus(IpmiResultConverterTest.buildChassisStatusResponseData(1))
		);

		assertEquals("System power state is up", executor().executeIpmiDetection(HOSTNAME, ipmiConfiguration));
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiDetectionThrowsException() {
		when(bmc.chassisStatus()).thenThrow(new IpmiTimeoutException("No reply"));

		final IpmiRequestExecutor executor = executor();
		assertThrows(IpmiTimeoutException.class, () -> executor.executeIpmiDetection(HOSTNAME, ipmiConfiguration));
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiGetSensors() {
		final FrusRequest frusRequest = mock(FrusRequest.class);
		when(frusRequest.execute()).thenReturn(IpmiClientResults.fruList(IpmiResultConverterTest.buildSystemBoardFrus()));
		when(bmc.frus()).thenReturn(frusRequest);
		final SensorsRequest sensorsRequest = mock(SensorsRequest.class);
		when(sensorsRequest.execute()).thenReturn(
			IpmiClientResults.sensorList(IpmiResultConverterTest.buildSensors(), "OEM record")
		);
		when(bmc.sensors()).thenReturn(sensorsRequest);

		assertEquals(
			"FRU;IBM;System x3650 M2;KD9098C - 794722G\n" +
				"System Board;1;System Board 1;IBM;System x3650 M2;KD9098C - 794722G;Base board=Device Present|Video USB=Connected\n" +
				"Battery;1;Battery 1;;;;Battery 1=Transition to OK\n" +
				"Front Panel Board;1;Front Panel Board 1;;;;Front Panel=Device Present\n" +
				"Temperature;0001;Ambient Temp;Front Panel Board 1;22.0;38;41",
			executor().executeIpmiGetSensors(HOSTNAME, ipmiConfiguration)
		);
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiGetSensorsThrowsException() {
		when(bmc.frus()).thenThrow(new IpmiTimeoutException("No reply"));

		final IpmiRequestExecutor executor = executor();
		assertThrows(IpmiTimeoutException.class, () -> executor.executeIpmiGetSensors(HOSTNAME, ipmiConfiguration));
		verify(bmc).close();
	}

	@Test
	void testNewClient() {
		// No session is opened before the first operation
		try (IpmiClient client = new IpmiRequestExecutor().newClient(HOSTNAME, ipmiConfiguration)) {
			assertEquals(HOSTNAME, client.host());
		}
		try (
			IpmiClient client = new IpmiRequestExecutor().newClient(
				HOSTNAME,
				IpmiConfiguration.builder().skipAuth(true).build()
			)
		) {
			assertEquals(HOSTNAME, client.host());
		}
	}

	@Test
	void testNewClientSkipAuth() {
		final IpmiClient.Builder builder = mock(IpmiClient.Builder.class, RETURNS_SELF);
		try (MockedStatic<IpmiClient> ipmi = mockStatic(IpmiClient.class)) {
			ipmi.when(() -> IpmiClient.builder(HOSTNAME)).thenReturn(builder);
			new IpmiRequestExecutor().newClient(HOSTNAME, IpmiConfiguration.builder().skipAuth(true).build());
		}
		// skipAuth uses cipher suite 3 without discovery, and a null user name is the anonymous login
		verify(builder).cipherSuite(3);
		verify(builder).credentials("", new char[0]);
		verify(builder).keepAlive(Duration.ZERO);
	}
}
