package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.withSettings;

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
import org.mockito.MockedConstruction;
import org.mockito.MockedConstruction.MockInitializer;

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
	 * Make the {@link WmiRequestExecutor} of the extensions created until closed a spy, whose WMI connection and query
	 * (run on a thread of their own, out of reach of a static mock) answer no row unless stubbed otherwise.
	 */
	private static MockedConstruction<WmiRequestExecutor> executors(final MockInitializer<WmiRequestExecutor> stubbing) {
		return mockConstruction(
			WmiRequestExecutor.class,
			withSettings().defaultAnswer(CALLS_REAL_METHODS),
			(executor, context) -> {
				doReturn(List.of()).when(executor).query(any(), any(), any(), any(), anyLong());
				stubbing.prepare(executor, context);
			}
		);
	}

	@Test
	void testCheckProtocolCountsAnAcceptedErrorAsFailedOnTheResource() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		try (MockedConstruction<WmiRequestExecutor> executors = executors((executor, context) ->
		// The request goes to the protocol hostname...
		doThrow(new WmiComException("WBEM_E_INVALID_NAMESPACE (0x8004100E)")).when(executor)
			.query(contains(PROTOCOL_HOSTNAME), any(), any(), any(), anyLong()))) {
			// ... which the health check still considers up
			assertEquals(Optional.of(true), new WmiExtension().checkProtocol(telemetryManager));
		}
		// ... and it is counted on the resource, as failed
		assertEquals(List.of(0L, 0L, 1L), wmiCounts(telemetryManager));
	}

	@Test
	void testCheckProtocolCountsCompleted() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		try (MockedConstruction<WmiRequestExecutor> executors = executors((executor, context) -> {})) {
			assertEquals(Optional.of(true), new WmiExtension().checkProtocol(telemetryManager));
		}
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(telemetryManager));
	}

	@Test
	void testEventLogSourceIsCountedAsWmi() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		try (MockedConstruction<WmiRequestExecutor> executors = executors((executor, context) -> {})) {
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
	void testLocalProcessCriterionIsCountedOnTheResource() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine("MBM");
		try (MockedConstruction<WmiRequestExecutor> executors = executors((executor, context) -> {
			// A local query: the local namespace, without credentials
			doThrow(new WmiComException("credentials sent")).when(executor).query(any(), any(), any(), any(), anyLong());
			doReturn(List.of()).when(executor).query(any(), isNull(), isNull(), any(), anyLong());
		})) {
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
