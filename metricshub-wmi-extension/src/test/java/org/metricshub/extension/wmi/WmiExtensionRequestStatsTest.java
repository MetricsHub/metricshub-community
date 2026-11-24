package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.wmi.exceptions.WmiComException;
import org.metricshub.wmi.wbem.WmiWbemServices;
import org.mockito.MockedStatic;

/**
 * The WMI requests of a monitored resource are counted in the request statistics of its {@link TelemetryManager}.
 */
class WmiExtensionRequestStatsTest {

	private static final String CONNECTOR_ID = "connector";

	/**
	 * The hostname of the resource (IP literals: nothing is resolved).
	 */
	private static final String RESOURCE_HOSTNAME = "192.0.2.10";

	/**
	 * The hostname the WMI configuration overrides the resource hostname with.
	 */
	private static final String PROTOCOL_HOSTNAME = "192.0.2.20";

	/**
	 * A resource whose WMI configuration overrides its hostname.
	 */
	private static TelemetryManager newTelemetryManager() {
		final WmiConfiguration wmiConfiguration = WmiConfiguration.builder()
			.hostname(PROTOCOL_HOSTNAME)
			.username("user")
			.password("pass".toCharArray())
			.timeout(30L)
			.build();
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(RESOURCE_HOSTNAME)
					.hostId(RESOURCE_HOSTNAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();
	}

	/**
	 * The counters of the "wmi" label of a resource, as completed, timeout and failed counts.
	 */
	private static List<Long> wmiCounts(final TelemetryManager telemetryManager) {
		final Map<RequestOutcome, Long> counts = telemetryManager.getRequestStats().snapshot().get(WmiExtension.IDENTIFIER);
		return counts == null
			? List.of(0L, 0L, 0L)
			: List.of(
				counts.get(RequestOutcome.COMPLETED),
				counts.get(RequestOutcome.TIMEOUT),
				counts.get(RequestOutcome.FAILED)
			);
	}

	/**
	 * WMI services whose queries return no row.
	 */
	private static WmiWbemServices emptyServices() throws Exception {
		final WmiWbemServices services = mock(WmiWbemServices.class);
		when(services.executeWql(anyString(), anyLong())).thenReturn(List.of());
		return services;
	}

	@Test
	void testCheckProtocolCountsAnAcceptedErrorAsFailedOnTheResource() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			// The request goes to the protocol hostname...
			statics.when(() -> WmiWbemServices.getInstance(contains(PROTOCOL_HOSTNAME), any(), any()))
				.thenThrow(new WmiComException("WBEM_E_INVALID_NAMESPACE (0x8004100E)"));

			// ... which the health check still considers up
			assertEquals(Optional.of(true), new WmiExtension().checkProtocol(telemetryManager));
		}
		// ... and it is counted on the resource, as failed
		assertEquals(List.of(0L, 0L, 1L), wmiCounts(telemetryManager));
	}

	@Test
	void testCheckProtocolCountsCompleted() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final WmiWbemServices services = emptyServices();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenReturn(services);
			assertEquals(Optional.of(true), new WmiExtension().checkProtocol(telemetryManager));
		}
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(telemetryManager));
	}

	@Test
	void testEventLogSourceIsCountedAsWmi() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final WmiWbemServices services = emptyServices();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenReturn(services);
			new WmiExtension().processSource(
				EventLogSource.builder().key("eventLog").logName("System").build(),
				CONNECTOR_ID,
				telemetryManager
			);
		}
		assertEquals(
			Map.of(WmiExtension.IDENTIFIER, wmiCountsMap(1L, 0L, 0L)),
			telemetryManager.getRequestStats().snapshot()
		);
	}

	@Test
	void testLocalProcessCriterionIsCountedOnTheResource() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine("MBM");
		final WmiWbemServices services = emptyServices();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			// A local query: the local namespace, without credentials
			statics.when(() -> WmiWbemServices.getInstance(any(), isNull(), isNull())).thenReturn(services);
			assertFalse(
				new WmiExtension().processCriterion(processCriterion, CONNECTOR_ID, telemetryManager, false).isSuccess()
			);
		}
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(telemetryManager));
	}

	/**
	 * The expected counters of one label.
	 */
	private static Map<RequestOutcome, Long> wmiCountsMap(final long completed, final long timeout, final long failed) {
		return Map.of(RequestOutcome.COMPLETED, completed, RequestOutcome.TIMEOUT, timeout, RequestOutcome.FAILED, failed);
	}
}
