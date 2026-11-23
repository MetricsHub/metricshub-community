package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.telemetry.RequestOutcome.COMPLETED;
import static org.metricshub.engine.telemetry.RequestOutcome.FAILED;
import static org.metricshub.engine.telemetry.RequestOutcome.TIMEOUT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelCursor;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelRead;
import org.metricshub.ipmi.client.FrusRequest;
import org.metricshub.ipmi.client.IpmiAuthenticationException;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientException;
import org.metricshub.ipmi.client.IpmiClientResults;
import org.metricshub.ipmi.client.IpmiConnectionException;
import org.metricshub.ipmi.client.IpmiTimeoutException;
import org.metricshub.ipmi.client.SelEntry;
import org.metricshub.ipmi.client.SensorsRequest;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The outcomes of the IPMI requests sent for a resource, counted in its request statistics.
 */
@ExtendWith(MockitoExtension.class)
class IpmiExtensionRequestStatsTest {

	private static final String CONNECTOR_ID = "connector";
	private static final String HOST_NAME = "resource-host";
	private static final String BMC_ADDRESS = "bmc-address";

	@Spy
	private IpmiRequestExecutor ipmiRequestExecutor = new IpmiRequestExecutor();

	@InjectMocks
	private IpmiExtension ipmiExtension;

	private IpmiConfiguration configuration;

	private TelemetryManager telemetryManager;

	@BeforeEach
	void setUp() {
		configuration = IpmiConfiguration.builder().username("user").password("pwd".toCharArray()).timeout(120L).build();
		telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.OOB)
					.configurations(Map.of(IpmiConfiguration.class, configuration))
					.build()
			)
			.build();
	}

	/**
	 * Asserts the IPMI counts of the resource.
	 *
	 * @param completed The expected completed requests
	 * @param timeout   The expected timed out requests
	 * @param failed    The expected failed requests
	 */
	private void assertCounts(final long completed, final long timeout, final long failed) {
		assertEquals(
			Map.of(IpmiExtension.IDENTIFIER, Map.of(COMPLETED, completed, TIMEOUT, timeout, FAILED, failed)),
			telemetryManager.getRequestStats().snapshot()
		);
	}

	/**
	 * @return the result of the IPMI criterion
	 */
	private CriterionTestResult processCriterion() {
		return ipmiExtension.processCriterion(IpmiCriterion.builder().build(), CONNECTOR_ID, telemetryManager, false);
	}

	@Test
	void testDetectionCompleted() {
		doReturn("System power state is up").when(ipmiRequestExecutor).executeIpmiDetection(HOST_NAME, configuration);

		assertTrue(processCriterion().isSuccess());
		assertCounts(1, 0, 0);
	}

	@Test
	void testDetectionTimeout() {
		doThrow(new IpmiTimeoutException("No reply")).when(ipmiRequestExecutor)
			.executeIpmiDetection(HOST_NAME, configuration);

		final CriterionTestResult result = processCriterion();
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().endsWith("Exception: No reply"), result.getMessage());
		assertCounts(0, 1, 0);
	}

	@Test
	void testDetectionConnectionFailed() {
		doThrow(new IpmiConnectionException("Cannot connect", new IOException("refused"))).when(ipmiRequestExecutor)
			.executeIpmiDetection(HOST_NAME, configuration);

		assertFalse(processCriterion().isSuccess());
		assertCounts(0, 0, 1);
	}

	@Test
	void testDetectionAuthenticationFailed() {
		doThrow(new IpmiAuthenticationException("Login refused", null)).when(ipmiRequestExecutor)
			.executeIpmiDetection(HOST_NAME, configuration);

		assertFalse(processCriterion().isSuccess());
		assertCounts(0, 0, 1);
	}

	@Test
	void testDetectionInterruptedIsTimeout() {
		doThrow(new IpmiClientException("Interrupted while waiting", new InterruptedException())).when(ipmiRequestExecutor)
			.executeIpmiDetection(HOST_NAME, configuration);

		assertFalse(processCriterion().isSuccess());
		assertCounts(0, 1, 0);
	}

	@Test
	void testDetectionOnAnInterruptedThreadCountsNothing() {
		// An earlier deadline of the job interrupted the thread: nothing is started
		Thread.currentThread().interrupt();
		try {
			assertFalse(processCriterion().isSuccess());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		verify(ipmiRequestExecutor, never()).executeIpmiDetection(HOST_NAME, configuration);
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testReadGivenUpAtTheDeadlineIsTimeout() {
		// ipmi-java gives up a read interrupted by the deadline of the job with an exception without cause
		doAnswer(invocation -> {
			Thread.currentThread().interrupt();
			throw new IpmiClientException("The read of SEL was given up at the deadline of its caller");
		}).when(ipmiRequestExecutor).executeIpmiDetection(HOST_NAME, configuration);

		try {
			assertFalse(processCriterion().isSuccess());
		} finally {
			Thread.interrupted();
		}
		assertCounts(0, 1, 0);
	}

	@Test
	void testMalformedAnswerIsFailed() {
		// ipmi-java decodes the answer of the BMC after the request: a short answer is an IllegalArgumentException
		doThrow(new IllegalArgumentException("Invalid response payload length")).when(ipmiRequestExecutor)
			.executeIpmiDetection(HOST_NAME, configuration);

		assertFalse(ipmiExtension.checkProtocol(telemetryManager).get());
		assertCounts(0, 0, 1);
	}

	@Test
	void testHealthCheckUp() {
		doReturn("System power state is up").when(ipmiRequestExecutor).executeIpmiDetection(HOST_NAME, configuration);

		assertTrue(ipmiExtension.checkProtocol(telemetryManager).get());
		assertCounts(1, 0, 0);
	}

	@Test
	void testHealthCheckDown() {
		doThrow(new IpmiAuthenticationException("Login refused", null)).when(ipmiRequestExecutor)
			.executeIpmiDetection(HOST_NAME, configuration);

		assertFalse(ipmiExtension.checkProtocol(telemetryManager).get());
		assertCounts(0, 0, 1);
	}

	@Test
	void testSensorsWithoutChassisStatusCompleted() {
		// The session reads FRUs and sensors although Get Chassis Status got no reply: a partial result
		final IpmiClient bmc = mock(IpmiClient.class);
		doReturn(bmc).when(ipmiRequestExecutor).newClient(HOST_NAME, configuration);
		when(bmc.chassisStatus()).thenThrow(new IpmiTimeoutException("No reply"));
		final FrusRequest frusRequest = mock(FrusRequest.class);
		when(frusRequest.execute()).thenReturn(IpmiClientResults.fruList(IpmiResultConverterTest.buildSystemBoardFrus()));
		when(bmc.frus()).thenReturn(frusRequest);
		final SensorsRequest sensorsRequest = mock(SensorsRequest.class);
		when(sensorsRequest.execute()).thenReturn(IpmiClientResults.sensorList(Collections.emptyList(), null));
		when(bmc.sensors()).thenReturn(sensorsRequest);

		assertEquals(
			SourceTable.ofText("FRU;IBM;System x3650 M2;KD9098C - 794722G"),
			ipmiExtension.processSource(IpmiSource.builder().build(), CONNECTOR_ID, telemetryManager)
		);
		assertCounts(1, 0, 0);
	}

	@Test
	void testSensorsFailed() {
		doThrow(new IpmiClientException("No common cipher suite")).when(ipmiRequestExecutor)
			.executeIpmiGetSensors(HOST_NAME, configuration);

		assertEquals(
			SourceTable.empty(),
			ipmiExtension.processSource(IpmiSource.builder().build(), CONNECTOR_ID, telemetryManager)
		);
		assertCounts(0, 0, 1);
	}

	@Test
	void testRawCompletionCodes() {
		final IpmiRawSource source = IpmiRawSource.builder().key("raw").netFn("6").command("1").build();

		doReturn("00;20 01").when(ipmiRequestExecutor).executeIpmiRaw(HOST_NAME, configuration, source);
		assertEquals(
			SourceTable.ofTable(List.of(List.of("00", "20 01"))),
			ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager)
		);
		assertCounts(1, 0, 0);

		// The BMC answered with an error completion code: the row is still returned
		doReturn("C1;").when(ipmiRequestExecutor).executeIpmiRaw(HOST_NAME, configuration, source);
		assertEquals(
			SourceTable.ofTable(List.of(List.of("C1", ""))),
			ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager)
		);
		assertCounts(1, 0, 1);
	}

	@Test
	void testRawInvalidArgumentCountsNothing() {
		// The real executor refuses the source before creating any client
		final IpmiRawSource bad = IpmiRawSource.builder().key("bad").netFn("x").command("1").build();

		assertEquals(SourceTable.empty(), ipmiExtension.processSource(bad, CONNECTOR_ID, telemetryManager));
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSelPolls() {
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);
		final IpmiSelSource source = IpmiSelSource.builder().key("sel").maxEventsPerPoll(1).build();
		final SelEntry entry6 = IpmiResultConverterTest.psuFailure(6);

		// First poll: the end of the log
		final SelCursor end = new SelCursor(null, 5, new byte[] { 0x05, 0x00, 0x02 });
		doReturn(end).when(ipmiRequestExecutor).executeIpmiSelEnd(HOST_NAME, configuration);
		assertEquals(SourceTable.empty(), ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager));
		assertCounts(1, 0, 0);

		// Next poll fails: the cursor stays
		doThrow(new IpmiTimeoutException("No reply")).when(ipmiRequestExecutor)
			.executeIpmiSel(HOST_NAME, configuration, end, true, 1);
		assertEquals(SourceTable.empty(), ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager));
		assertSame(end, namespace.getSourceCursor("sel"));
		assertCounts(1, 1, 0);

		// Next poll reads one entry
		doReturn(new SelRead(IpmiClientResults.selEntryList(List.of(entry6), null), null)).when(ipmiRequestExecutor)
			.executeIpmiSel(HOST_NAME, configuration, end, true, 1);
		assertEquals(1, ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager).getTable().size());
		assertCounts(2, 1, 0);
	}

	@Test
	void testSelFirstPollFailed() {
		doThrow(new IpmiClientException("Get SEL Entry of the last record failed")).when(ipmiRequestExecutor)
			.executeIpmiSelEnd(HOST_NAME, configuration);

		final IpmiSelSource source = IpmiSelSource.builder().key("sel").build();
		assertEquals(SourceTable.empty(), ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager));
		assertNull(telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).getSourceCursor("sel"));
		assertCounts(0, 0, 1);
	}

	@Test
	void testBmcAddressCountedInResource() {
		// The requests go to the BMC address, and count for the resource
		configuration.setHostname(BMC_ADDRESS);
		doReturn("System power state is up").when(ipmiRequestExecutor).executeIpmiDetection(BMC_ADDRESS, configuration);

		assertTrue(processCriterion().isSuccess());
		assertTrue(ipmiExtension.checkProtocol(telemetryManager).get());
		assertCounts(2, 0, 0);
	}

	@Test
	void testExecuteQueryCountsNothing() throws Exception {
		configuration.setHostname(BMC_ADDRESS);
		doReturn("sensors").when(ipmiRequestExecutor).executeIpmiGetSensors(BMC_ADDRESS, configuration);

		assertEquals("sensors", ipmiExtension.executeQuery(configuration, null));
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}
}
