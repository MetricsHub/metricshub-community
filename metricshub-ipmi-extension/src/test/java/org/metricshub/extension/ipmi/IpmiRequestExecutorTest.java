package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelCursor;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelRead;
import org.metricshub.ipmi.client.FrusRequest;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientException;
import org.metricshub.ipmi.client.IpmiClientResults;
import org.metricshub.ipmi.client.IpmiTimeoutException;
import org.metricshub.ipmi.client.RawRequest;
import org.metricshub.ipmi.client.SelEntry;
import org.metricshub.ipmi.client.SelEntryList;
import org.metricshub.ipmi.client.SelRequest;
import org.metricshub.ipmi.client.SensorList;
import org.metricshub.ipmi.client.SensorsRequest;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;
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
		doReturn(bmc).when(executor).newClient(HOSTNAME, ipmiConfiguration, PrivilegeLevel.Operator);
		doReturn(bmc).when(executor).newClient(HOSTNAME, ipmiConfiguration, PrivilegeLevel.MaximumAvailable);
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
		when(bmc.chassisStatus()).thenReturn(
			IpmiClientResults.chassisStatus(IpmiResultConverterTest.buildChassisStatusResponseData(1))
		);

		assertEquals(
			"Chassis;1;0;0;0;0;0;0;0;0;PoweredOff;;\n" +
				"FRU;IBM;System x3650 M2;KD9098C - 794722G\n" +
				"System Board;1;System Board 1;IBM;System x3650 M2;KD9098C - 794722G;Base board=Device Present|Video USB=Connected;;;;\n" +
				"Battery;1;Battery 1;;;;Battery 1=Transition to OK;;;;\n" +
				"Front Panel Board;1;Front Panel Board 1;;;;Front Panel=Device Present;;;;\n" +
				"Temperature;0001;Ambient Temp;Front Panel Board 1;22.0;38;41",
			executor().executeIpmiGetSensors(HOSTNAME, ipmiConfiguration)
		);
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiGetSensorsWithoutChassisStatus() {
		final FrusRequest frusRequest = mock(FrusRequest.class);
		when(frusRequest.execute()).thenReturn(IpmiClientResults.fruList(IpmiResultConverterTest.buildSystemBoardFrus()));
		when(bmc.frus()).thenReturn(frusRequest);
		final SensorsRequest sensorsRequest = mock(SensorsRequest.class);
		when(sensorsRequest.execute()).thenReturn(IpmiClientResults.sensorList(Collections.emptyList(), "OEM record"));
		when(bmc.sensors()).thenReturn(sensorsRequest);
		when(bmc.chassisStatus()).thenThrow(new IpmiTimeoutException("No reply"));

		// The FRUs and the sensors are still collected, without the Chassis line
		assertEquals(
			"FRU;IBM;System x3650 M2;KD9098C - 794722G",
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

	@Test
	void testExecuteIpmiSel() {
		final SelEntry since = IpmiResultConverterTest.psuFailure(5);
		final SelEntryList entries = IpmiClientResults.selEntryList(List.of(IpmiResultConverterTest.psuFailure(6)), null);
		final SelRequest selRequest = mock(SelRequest.class, RETURNS_SELF);
		when(selRequest.execute()).thenReturn(entries);
		when(bmc.sel()).thenReturn(selRequest);
		final SensorList sensors = IpmiClientResults.sensorList(List.of(IpmiResultConverterTest.psuSensor()), "OEM record");
		final SensorsRequest sensorsRequest = mock(SensorsRequest.class);
		when(sensorsRequest.execute()).thenReturn(sensors);
		when(bmc.sensors()).thenReturn(sensorsRequest);

		final SelRead read = executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, SelCursor.of(since), true, 0);

		assertSame(entries, read.entries());
		assertSame(sensors, read.sensors());
		verify(selRequest).since(since);
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiSelKeepsEntriesWhenSensorsFail() {
		final SelEntryList entries = IpmiClientResults.selEntryList(List.of(IpmiResultConverterTest.psuFailure(6)), null);
		final SelRequest selRequest = mock(SelRequest.class, RETURNS_SELF);
		when(selRequest.execute()).thenReturn(entries);
		when(bmc.sel()).thenReturn(selRequest);
		final SensorsRequest sensorsRequest = mock(SensorsRequest.class);
		when(sensorsRequest.execute()).thenThrow(new IpmiTimeoutException("No reply"));
		when(bmc.sensors()).thenReturn(sensorsRequest);

		final SelRead read = executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, null, true, 0);

		assertSame(entries, read.entries());
		assertNull(read.sensors());
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiSelWholeLogWithoutSensors() {
		final SelEntryList entries = IpmiClientResults.selEntryList(List.of(IpmiResultConverterTest.psuFailure(6)), null);
		final SelRequest selRequest = mock(SelRequest.class, RETURNS_SELF);
		when(selRequest.execute()).thenReturn(entries);
		when(bmc.sel()).thenReturn(selRequest);

		final SelRead read = executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, null, false, 0);

		assertSame(entries, read.entries());
		assertNull(read.sensors());
		verify(selRequest, never()).since(any(SelEntry.class));
		verify(bmc, never()).sensors();
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiRaw() {
		final RawRequest rawRequest = mock(RawRequest.class, RETURNS_SELF);
		when(rawRequest.execute()).thenReturn(IpmiClientResults.rawResponse(0, new byte[] { 0x20, 0x01 }));
		when(bmc.raw(6, 1)).thenReturn(rawRequest);
		final IpmiRawSource source = IpmiRawSource.builder().netFn("0x06").command("1").data("0x00 255").build();

		assertEquals("00;20 01", executor().executeIpmiRaw(HOSTNAME, ipmiConfiguration, source));
		verify(rawRequest).data(0, 255);
		verify(bmc).close();
	}

	@Test
	void testRawQuery() {
		assertEquals(
			"06 01 00 FF",
			IpmiRequestExecutor.rawQuery(IpmiRawSource.builder().netFn("0x06").command("1").data("0x00 255").build())
		);
		assertEquals("06 01", IpmiRequestExecutor.rawQuery(IpmiRawSource.builder().netFn("6").command("0x01").build()));
		// A leading zero is not octal
		assertEquals(
			"0A 08 10",
			IpmiRequestExecutor.rawQuery(IpmiRawSource.builder().netFn("010").command("08").data("0X10").build())
		);
		final IpmiRawSource noNetFn = IpmiRawSource.builder().command("1").build();
		assertThrows(IllegalArgumentException.class, () -> IpmiRequestExecutor.rawQuery(noNetFn));
		final IpmiRawSource badData = IpmiRawSource.builder().netFn("6").command("1").data("0x00 zz").build();
		assertThrows(IllegalArgumentException.class, () -> IpmiRequestExecutor.rawQuery(badData));
	}

	@Test
	void testExecuteIpmiSelFromRecordId() {
		final SelEntryList entries = IpmiClientResults.selEntryList(List.of(IpmiResultConverterTest.psuFailure(6)), null);
		final SelRequest selRequest = mock(SelRequest.class, RETURNS_SELF);
		when(selRequest.execute()).thenReturn(entries);
		when(bmc.sel()).thenReturn(selRequest);

		final RawRequest rawRequest = mock(RawRequest.class, RETURNS_SELF);
		when(bmc.raw(0x0A, 0x43)).thenReturn(rawRequest);
		final SelCursor cursor = new SelCursor(null, 5, new byte[] { 0x05, 0x00, 0x02, 0x01 });

		// A cursor at a record ID only, which the log still holds: the entries after that ID
		when(rawRequest.execute()).thenReturn(
			IpmiClientResults.rawResponse(0, new byte[] { (byte) 0xFF, (byte) 0xFF, 0x05, 0x00, 0x02, 0x01 })
		);
		assertSame(entries, executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, cursor, false, 0).entries());
		verify(rawRequest).data(0x00, 0x00, 0x05, 0x00, 0x00, 0xFF);
		verify(selRequest).since(5);

		// The log was cleared and filled again up to record 5, another entry: the whole log
		when(rawRequest.execute()).thenReturn(
			IpmiClientResults.rawResponse(0, new byte[] { (byte) 0xFF, (byte) 0xFF, 0x05, 0x00, 0x02, 0x02 })
		);
		assertSame(entries, executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, cursor, false, 0).entries());
		verify(selRequest, times(1)).since(5);

		// The record is gone: the whole log
		when(rawRequest.execute()).thenReturn(IpmiClientResults.rawResponse(0xCB, new byte[0]));
		assertSame(entries, executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, cursor, false, 0).entries());
		verify(selRequest, times(1)).since(5);

		// A busy BMC is not a cleared log: the poll fails and the next one tries again
		when(rawRequest.execute()).thenReturn(IpmiClientResults.rawResponse(0xC0, new byte[0]));
		final IpmiRequestExecutor executor = executor();
		assertThrows(IpmiClientException.class, () ->
			executor.executeIpmiSel(HOSTNAME, ipmiConfiguration, cursor, false, 0)
		);

		// A cursor at an empty log: the whole log
		assertSame(
			entries,
			executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, new SelCursor(null, 0, null), false, 0).entries()
		);
		verify(selRequest, never()).since(0);
		verify(selRequest, never()).since(any(SelEntry.class));
	}

	@Test
	void testExecuteIpmiSelLimit() {
		final SelRequest selRequest = mock(SelRequest.class, RETURNS_SELF);
		when(selRequest.stream()).thenReturn(
			Stream.of(
				IpmiResultConverterTest.psuFailure(6),
				IpmiResultConverterTest.psuFailure(7),
				IpmiResultConverterTest.psuFailure(8)
			)
		);
		when(bmc.sel()).thenReturn(selRequest);

		// The walk stops at the limit: the entries left are for the next poll
		final SelRead read = executor().executeIpmiSel(HOSTNAME, ipmiConfiguration, null, false, 2);
		assertEquals(List.of(6, 7), read.entries().stream().map(SelEntry::id).toList());
		verify(selRequest, never()).execute();
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiSelEnd() {
		final RawRequest rawRequest = mock(RawRequest.class, RETURNS_SELF);
		when(bmc.raw(0x0A, 0x43)).thenReturn(rawRequest);

		// Next record ID FFFFh, then the record 0283h
		when(rawRequest.execute()).thenReturn(
			IpmiClientResults.rawResponse(0, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0x83, 0x02, 0x02 })
		);
		final SelCursor end = executor().executeIpmiSelEnd(HOSTNAME, ipmiConfiguration);
		assertEquals(0x283, end.recordId());
		assertArrayEquals(new byte[] { (byte) 0x83, 0x02, 0x02 }, end.record());
		verify(rawRequest).data(0x00, 0x00, 0xFF, 0xFF, 0x00, 0xFF);
		verify(bmc).close();

		// An empty log: requested record not present
		when(rawRequest.execute()).thenReturn(IpmiClientResults.rawResponse(0xCB, new byte[0]));
		assertEquals(0, executor().executeIpmiSelEnd(HOSTNAME, ipmiConfiguration).recordId());

		// A busy BMC is not an empty log
		when(rawRequest.execute()).thenReturn(IpmiClientResults.rawResponse(0xC0, new byte[0]));
		final IpmiRequestExecutor executor = executor();
		assertThrows(IpmiClientException.class, () -> executor.executeIpmiSelEnd(HOSTNAME, ipmiConfiguration));
	}

	@Test
	void testExecuteIpmiChassisControl() {
		when(bmc.chassisStatus()).thenReturn(
			IpmiClientResults.chassisStatus(IpmiResultConverterTest.buildChassisStatusResponseData(1))
		);

		assertEquals(
			"System power state is up",
			executor().executeIpmiChassisControl(HOSTNAME, ipmiConfiguration, PowerCommand.PowerUp)
		);
		verify(bmc).chassisControl(PowerCommand.PowerUp);
		verify(bmc).close();
	}

	@Test
	void testExecuteIpmiChassisIdentify() {
		assertEquals(
			"Chassis identify on for 15 s",
			executor().executeIpmiChassisIdentify(HOSTNAME, ipmiConfiguration, 15, false)
		);
		verify(bmc).chassisIdentify(15);
		assertEquals("Chassis identify off", executor().executeIpmiChassisIdentify(HOSTNAME, ipmiConfiguration, 0, false));
		verify(bmc).chassisIdentify(0);
		assertEquals(
			"Chassis identify on until turned off",
			executor().executeIpmiChassisIdentify(HOSTNAME, ipmiConfiguration, 0, true)
		);
		verify(bmc).chassisIdentifyForever();
	}
}
