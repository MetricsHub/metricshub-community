package org.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.cli.service.PrettyPrinterService.ATTRIBUTES_HEADER;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import org.fusesource.jansi.Ansi;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.helper.StringBuilderWriter;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

class PrettyPrinterServiceTest {

	private static final String COLON_PREFIX = ": ";
	private static final String DASH_PREFIX = "- ";
	private static final String MONITORS_SUFFIX = " monitors:";
	private static final String HW_PARENT_TYPE_ATTRIBUTE_KEY = "hw.parent.type";
	private static final String HW_PARENT_ID_ATTRIBUTE_KEY = "hw.parent.id";
	private static final String INTERNAL_ATTRIBUTE_KEY = "__internal";
	private static final String VENDOR_ATTRIBUTE_KEY = "vendor";
	private static final String MODEL_ATTRIBUTE_KEY = "model";
	private static final String SERIAL_NUMBER_ATTRIBUTE_KEY = "serial_number";
	private static final String NAME_ATTRIBUTE_KEY = "name";
	private static final String ID_ATTRIBUTE_KEY = "id";
	private static final String BATTERY_HW_ENERGY_METRIC_KEY = "hw.energy{hw.type=\"battery\"}";
	private static final String BATTERY_HW_POWER_METRIC_KEY = "hw.power{hw.type=\"battery\"}";
	private static final String FAILED_STATE_VALUE = "failed";
	private static final String OK_STATE_VALUE = "ok";
	private static final String DEGRADED_STATE_VALUE = "degraded";
	private static final String HW_STATUS_METRIC_KEY = "hw.status";
	private static final String BATTERY_MODEL_VALUE = "Test Battery Model";
	private static final String METRICSHUB_VENDOR_VALUE = "MetricsHub";
	private static final String ENCLOSURE_ID_VALUE = "enclosure-1";
	private static final String BATTERY_1_ID_VALUE = "battery-1";
	private static final String BATTERY_2_ID_VALUE = "battery-2";
	private static final long COLLECT_TIME = System.currentTimeMillis();
	private static final String HOST_ID_VALUE = "server-1.organization.com";

	@Test
	void testPrint() throws IOException {
		// Create a new TelemetryManager to wrap all the monitors
		final TelemetryManager telemetryManager = new TelemetryManager();

		// Create the host monitor as root endpoint monitor
		final Monitor host = Monitor
			.builder()
			.type(KnownMonitorType.HOST.getKey())
			.attributes(
				Map.of(
					ID_ATTRIBUTE_KEY,
					HOST_ID_VALUE,
					NAME_ATTRIBUTE_KEY,
					"server-1",
					"location",
					"remote",
					"is_endpoint",
					"true"
				)
			)
			.isEndpoint(true)
			.build();

		// Add the host to the TelemetryManager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_ID_VALUE);

		// Collect the host's metrics
		final MetricFactory metricFactory = new MetricFactory(HOST_ID_VALUE, new ConnectorStore());
		metricFactory.collectNumberMetric(host, "hw.host.power", 150D, COLLECT_TIME);
		metricFactory.collectNumberMetric(host, "hw.host.heating_margin", 20D, COLLECT_TIME);
		metricFactory.collectNumberMetric(host, "hw.host.ambient_temperature", 22D, COLLECT_TIME);

		// Create a new enclosure instance
		final Monitor enclosure = Monitor
			.builder()
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.attributes(
				Map.of(
					ID_ATTRIBUTE_KEY,
					ENCLOSURE_ID_VALUE,
					NAME_ATTRIBUTE_KEY,
					"Computer (System Baord 1)",
					SERIAL_NUMBER_ATTRIBUTE_KEY,
					"SN123456",
					MODEL_ATTRIBUTE_KEY,
					"Test Model",
					VENDOR_ATTRIBUTE_KEY,
					METRICSHUB_VENDOR_VALUE,
					INTERNAL_ATTRIBUTE_KEY,
					"value"
				)
			)
			.build();

		// Add the enclosure to the TelemetryManager
		telemetryManager.addNewMonitor(enclosure, KnownMonitorType.ENCLOSURE.getKey(), ENCLOSURE_ID_VALUE);

		// Collect the status of the enclosure
		metricFactory.collectStateSetMetric(
			enclosure,
			HW_STATUS_METRIC_KEY,
			OK_STATE_VALUE,
			new String[] { OK_STATE_VALUE, DEGRADED_STATE_VALUE, FAILED_STATE_VALUE },
			COLLECT_TIME
		);

		// Collect the power and the energy metrics
		metricFactory.collectNumberMetric(enclosure, "hw.enclosure.power", 150D, COLLECT_TIME);
		metricFactory.collectNumberMetric(enclosure, "hw.enclosure.energy", 3000000D, COLLECT_TIME);

		// Create the first battery
		final Monitor battery1 = Monitor
			.builder()
			.type(KnownMonitorType.BATTERY.getKey())
			.attributes(
				Map.of(
					ID_ATTRIBUTE_KEY,
					BATTERY_1_ID_VALUE,
					NAME_ATTRIBUTE_KEY,
					"Battery: 1",
					SERIAL_NUMBER_ATTRIBUTE_KEY,
					"SN123457",
					MODEL_ATTRIBUTE_KEY,
					BATTERY_MODEL_VALUE,
					VENDOR_ATTRIBUTE_KEY,
					METRICSHUB_VENDOR_VALUE,
					HW_PARENT_ID_ATTRIBUTE_KEY,
					ENCLOSURE_ID_VALUE,
					HW_PARENT_TYPE_ATTRIBUTE_KEY,
					KnownMonitorType.ENCLOSURE.getKey()
				)
			)
			.build();

		// Collect the state of the battery as degraded
		metricFactory.collectStateSetMetric(
			battery1,
			HW_STATUS_METRIC_KEY,
			DEGRADED_STATE_VALUE,
			new String[] { OK_STATE_VALUE, DEGRADED_STATE_VALUE, FAILED_STATE_VALUE },
			COLLECT_TIME
		);

		// Collect the power and energy metrics
		metricFactory.collectNumberMetric(battery1, BATTERY_HW_POWER_METRIC_KEY, 10D, COLLECT_TIME);
		metricFactory.collectNumberMetric(battery1, BATTERY_HW_ENERGY_METRIC_KEY, 50000D, COLLECT_TIME);

		// Create the second battery
		final Monitor battery2 = Monitor
			.builder()
			.type(KnownMonitorType.BATTERY.getKey())
			.attributes(
				Map.of(
					ID_ATTRIBUTE_KEY,
					BATTERY_2_ID_VALUE,
					NAME_ATTRIBUTE_KEY,
					"Battery: 2",
					SERIAL_NUMBER_ATTRIBUTE_KEY,
					"SN123458",
					MODEL_ATTRIBUTE_KEY,
					BATTERY_MODEL_VALUE,
					VENDOR_ATTRIBUTE_KEY,
					METRICSHUB_VENDOR_VALUE,
					HW_PARENT_ID_ATTRIBUTE_KEY,
					ENCLOSURE_ID_VALUE,
					HW_PARENT_TYPE_ATTRIBUTE_KEY,
					KnownMonitorType.ENCLOSURE.getKey()
				)
			)
			.build();

		// Collect the state of the battery as failed
		metricFactory.collectStateSetMetric(
			battery2,
			HW_STATUS_METRIC_KEY,
			FAILED_STATE_VALUE,
			new String[] { OK_STATE_VALUE, DEGRADED_STATE_VALUE, FAILED_STATE_VALUE },
			COLLECT_TIME
		);

		// Collect the power and energy metrics
		metricFactory.collectNumberMetric(battery2, BATTERY_HW_POWER_METRIC_KEY, 5D, COLLECT_TIME);
		metricFactory.collectNumberMetric(battery2, BATTERY_HW_ENERGY_METRIC_KEY, 25000D, COLLECT_TIME);

		// Add the two batteries in the TelemetryManager
		telemetryManager.addNewMonitor(battery2, KnownMonitorType.BATTERY.getKey(), BATTERY_1_ID_VALUE);
		telemetryManager.addNewMonitor(battery1, KnownMonitorType.BATTERY.getKey(), BATTERY_2_ID_VALUE);

		final StringBuilder builder = new StringBuilder();
		// Instantiate a new StringBuilderWriter
		final Writer writer = new StringBuilderWriter(builder);

		// Create the PrintWriter that will be passed to the PrettyPrinterService
		final PrintWriter printWriter = new PrintWriter(writer);

		HashSet<String> monitorTypes = new HashSet<>();
		monitorTypes.add("+" + battery2.getType());
		monitorTypes.add("+" + battery1.getType());
		monitorTypes.add("+" + enclosure.getType());

		// Create a new PrettyPrinterService using the TelemetryManager and PrintWriter
		// and call the print method
		new PrettyPrinterService(telemetryManager, printWriter).print(monitorTypes);

		final String result = builder.toString();

		// Build the expected result to generate the expected ANSI escape sequences.
		final String expected = buildExpected(host, enclosure, battery1, battery2);

		// Check the actual result
		assertEquals(expected, result);
	}

	/**
	 * Create the expected result for the given monitors
	 * @param host      Host monitor instance
	 * @param enclosure Enclosure monitor instance
	 * @param battery1  First battery monitor instance
	 * @param battery2  Second battery monitor instance
	 * @return String value
	 * @throws IOException
	 */
	private String buildExpected(
		final Monitor host,
		final Monitor enclosure,
		final Monitor battery1,
		final Monitor battery2
	) throws IOException {
		final StringBuilder builder = new StringBuilder();
		// Instantiate a new StringBuilderWriter
		final Writer writer = new StringBuilderWriter(builder);
		// Create the PrintWriter that will print output ANSI escape sequences.
		final PrintWriter printWriter = new PrintWriter(writer);

		// Create a new PrettyPrinterService using the TelemetryManager and PrintWriter
		// The TelemetryManager instance will not be used
		final PrettyPrinterService prettyPrinterService = new PrettyPrinterService(new TelemetryManager(), printWriter);

		// Print host
		prettyPrinterService.addMargin(0);

		printWriter.print(DASH_PREFIX);
		printWriter.print(host.getType());

		printWriter.print(COLON_PREFIX);
		printWriter.println(Ansi.ansi().fgCyan().a(host.getAttribute(NAME_ATTRIBUTE_KEY)).reset().toString());

		prettyPrinterService.printAttributes(host.getAttributes(), 2, ATTRIBUTES_HEADER);
		prettyPrinterService.printMetrics(host, 2);

		printWriter.println();
		printWriter.flush();

		// Print enclosure instances
		prettyPrinterService.addMargin(4);
		printWriter.println(
			Ansi.ansi().bold().a(KnownMonitorType.ENCLOSURE.getKey()).boldOff().a(MONITORS_SUFFIX).toString()
		);

		// Print the enclosure data
		prettyPrinterService.addMargin(4);
		printWriter.print(DASH_PREFIX);
		printWriter.print(enclosure.getType());
		printWriter.print(COLON_PREFIX);
		printWriter.println(Ansi.ansi().fgCyan().a(enclosure.getAttribute(NAME_ATTRIBUTE_KEY)).reset().toString());

		prettyPrinterService.printAttributes(enclosure.getAttributes(), 6, ATTRIBUTES_HEADER);
		prettyPrinterService.printMetrics(enclosure, 6);

		printWriter.println();
		printWriter.flush();

		// Print battery instances
		prettyPrinterService.addMargin(8);
		printWriter.println(
			Ansi.ansi().bold().a(KnownMonitorType.BATTERY.getKey()).boldOff().a(MONITORS_SUFFIX).toString()
		);

		// Print battery 1 data
		prettyPrinterService.addMargin(8);
		printWriter.print(DASH_PREFIX);
		printWriter.print(battery1.getType());
		printWriter.print(COLON_PREFIX);
		printWriter.println(Ansi.ansi().fgCyan().a(battery1.getAttribute(NAME_ATTRIBUTE_KEY)).reset().toString());

		prettyPrinterService.printAttributes(battery1.getAttributes(), 10, ATTRIBUTES_HEADER);
		prettyPrinterService.printMetrics(battery1, 10);

		printWriter.println();
		printWriter.flush();

		// Print battery 2 data
		prettyPrinterService.addMargin(8);
		printWriter.print(DASH_PREFIX);
		printWriter.print(battery2.getType());
		printWriter.print(COLON_PREFIX);
		printWriter.println(Ansi.ansi().fgCyan().a(battery2.getAttribute(NAME_ATTRIBUTE_KEY)).reset().toString());

		prettyPrinterService.printAttributes(battery2.getAttributes(), 10, ATTRIBUTES_HEADER);
		prettyPrinterService.printMetrics(battery2, 10);

		printWriter.println();
		printWriter.flush();

		return builder.toString();
	}

	@Test
	void testFetchUnit() {
		final String energyMetric = "hw.energy";
		assertTrue(PrettyPrinterService.fetchUnit(energyMetric, Map.of()).isEmpty());
		final String unit = "J";

		final Map<String, MetricDefinition> metricDefintionsMap = Map.of(
			energyMetric,
			MetricDefinition.builder().unit(unit).build()
		);
		assertEquals(Optional.of(unit), PrettyPrinterService.fetchUnit(energyMetric, metricDefintionsMap));
	}
}
