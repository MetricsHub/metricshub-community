package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.metricshub.engine.constants.Constants.HARDCODED_SOURCE;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.constants.Constants;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class MappingProcessorTest {

	private static final String HW_VM_POWER_RATIO = "hw.vm.power_ratio";
	private static final String HW_VM_POWER_RATIO_RAW_POWER_SHARE = "__hw.vm.power_ratio.raw_power_share";

	@Test
	void testInterpretNonContextMapping() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname("hostname").build());

		final List<String> row = List.of("1", "1", "1", "10", "10", "1", "1", "1", "1", "2", "1");

		final String vendorSource1Ref = "${source::monitors.cpu.discovery.sources.vendor}";
		final String vendorIdSource1Ref = "${source::monitors.cpu.discovery.sources.id}";

		final MappingProcessor mappingProcessor = MappingProcessor
			.builder()
			.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
			.telemetryManager(telemetryManager)
			.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
			.row(row)
			.build();

		final ConnectorNamespace connectorNamespace = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME);
		connectorNamespace.addSourceTable(
			vendorSource1Ref,
			SourceTable.builder().table(SourceTable.csvToTable("vendor", MetricsHubConstants.TABLE_SEP)).build()
		);
		connectorNamespace.addSourceTable(
			vendorIdSource1Ref,
			SourceTable.builder().table(SourceTable.csvToTable("1", MetricsHubConstants.TABLE_SEP)).build()
		);

		// Value conversion tests, basic value and invalid value
		{
			final Map<String, String> expected = new LinkedHashMap<>();
			expected.put("testMebiByte2Byte", "1048576.0");
			expected.put("testMegaHertz2Hertz", "1000000.0");
			expected.put("testMilliVolt2Volt", "0.001");
			expected.put("testMegaBit2Bit", "1000000.0");
			expected.put("testMegaBit2Byte", "125000.0");
			expected.put("testPercent2Ratio", "0.1");
			expected.put("testValue", "10");
			expected.put("testSourceReferenceKey", "vendor1");
			expected.put("testInvalidValue", "");

			final Map<String, String> keyValuePairs = Map.of(
				"testMebiByte2Byte",
				"mebibyte2byte(1)",
				"testMegaHertz2Hertz",
				"megahertz2hertz(1)",
				"testMilliVolt2Volt",
				"milliVolt2Volt(1)",
				"testMegaBit2Bit",
				"megabit2bit(1)",
				"testMegaBit2Byte",
				"megabit2byte(1)",
				"testPercent2Ratio",
				"percent2ratio(10)",
				"testValue",
				"10",
				"testSourceReferenceKey",
				vendorSource1Ref + vendorIdSource1Ref,
				"testInvalidValue",
				"percent2ratio(ten)"
			);

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}

		// Value conversion tests and basic value
		{
			final Map<String, String> expected = Map.of(
				"testMebiByte2Byte",
				"1048576.0",
				"testMegaHertz2Hertz",
				"1000000.0",
				"testMilliVolt2Volt",
				"0.001",
				"testMegaBit2Bit",
				"1000000.0",
				"testMegaBit2Byte",
				"125000.0",
				"testPercent2Ratio",
				"0.1",
				"testValue",
				"10"
			);

			final Map<String, String> keyValuePairs = Map.of(
				"testMebiByte2Byte",
				"mebibyte2byte($1)",
				"testMegaHertz2Hertz",
				"megahertz2hertz($2)",
				"testMilliVolt2Volt",
				"milliVolt2Volt($2)",
				"testMegaBit2Bit",
				"megabit2bit($3)",
				"testMegaBit2Byte",
				"megabit2byte($3)",
				"testPercent2Ratio",
				"percent2ratio($4)",
				"testValue",
				"$5"
			);

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}

		// Legacy conversion tests with correct values
		{
			final Map<String, String> expected = Map.of(
				"testLegacyFullDuplex",
				"1",
				"testLegacyPredictedFailure",
				"1",
				"testLegacyIntrusionStatus",
				"1",
				"testLegacyNeedsCleaning",
				"1",
				"testLegacyLinkStatus",
				"0",
				"testBoolean",
				"1",
				"testBooleanUpperCase",
				"1"
			);

			final Map<String, String> keyValuePairs = Map.of(
				"testLegacyFullDuplex",
				"legacyfullduplex(1)",
				"testLegacyPredictedFailure",
				"legacypredictedfailure(1)",
				"testLegacyIntrusionStatus",
				"legacyintrusionstatus(1)",
				"testLegacyNeedsCleaning",
				"legacyneedscleaning(1)",
				"testLegacyLinkStatus",
				"legacylinkstatus(2)",
				"testBoolean",
				"boolean(true)",
				"testBooleanUpperCase",
				"boolean(TRUE)"
			);

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}

		// Legacy conversion tests with column references
		{
			final Map<String, String> expected = Map.of(
				"testLegacyFullDuplex",
				"1",
				"testLegacyPredictedFailure",
				"1",
				"testLegacyIntrusionStatus",
				"1",
				"testLegacyNeedsCleaning",
				"1",
				"testLegacyLinkStatus",
				"0",
				"testBoolean",
				"1"
			);

			final Map<String, String> keyValuePairs = Map.of(
				"testLegacyFullDuplex",
				"legacyfullduplex($6)",
				"testLegacyPredictedFailure",
				"legacypredictedfailure($7)",
				"testLegacyIntrusionStatus",
				"legacyintrusionstatus($8)",
				"testLegacyNeedsCleaning",
				"legacyneedscleaning($9)",
				"testLegacyLinkStatus",
				"legacylinkstatus($10)",
				"testBoolean",
				"boolean($11)"
			);

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}

		// Legacy conversion tests with incorrect values
		{
			final Map<String, String> expected = new LinkedHashMap<>();
			expected.put("testLegacyFullDuplex", null);
			expected.put("testLegacyPredictedFailure", null);
			expected.put("testLegacyIntrusionStatus", null);
			expected.put("testLegacyNeedsCleaning", null);
			expected.put("testLegacyLinkStatus", null);
			expected.put("testBoolean", "0");

			final Map<String, String> keyValuePairs = Map.of(
				"testLegacyFullDuplex",
				"legacyfullduplex(invalid)",
				"testLegacyPredictedFailure",
				"legacypredictedfailure(invalid)",
				"testLegacyIntrusionStatus",
				"legacyintrusionstatus(invalid)",
				"testLegacyNeedsCleaning",
				"legacyneedscleaning(invalid)",
				"testLegacyLinkStatus",
				"legacylinkstatus(invalid)",
				"testBoolean",
				"boolean(invalid)"
			);

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}
	}

	@Test
	void testInterpretNonContextMappingReplaceColumnReferences() {
		final List<String> row = Arrays.asList("cpu", "DellOpenManage", "1.1", "Dell $1", null);
		final MappingProcessor mappingProcessor = MappingProcessor
			.builder()
			.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
			.telemetryManager(new TelemetryManager())
			.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
			.row(row)
			.indexCounter(1)
			.build();

		// Test case for replacement of each column reference with the actual value of the corresponding column
		final Map<String, String> keyValuePairs = new HashMap<>();
		keyValuePairs.put("type", "$1");
		keyValuePairs.put("name", "cpu $2 $index");
		keyValuePairs.put("id", "$1_$2_$3");
		keyValuePairs.put("__display_id", "$index");
		keyValuePairs.put("vendor", "$4");
		keyValuePairs.put("serial", "$5 $6");
		keyValuePairs.put("microcode", "$7");
		keyValuePairs.put("firmware", "Dell $7");
		keyValuePairs.put("info", "$5");

		final Map<String, String> result = mappingProcessor.interpretNonContextMapping(keyValuePairs);
		assertEquals("cpu", result.get("type"));
		assertEquals("cpu DellOpenManage 1", result.get("name"));
		assertEquals("cpu_DellOpenManage_1.1", result.get("id"));
		assertEquals("Dell $1", result.get("vendor"));
		assertEquals(" ", result.get("serial"));
		assertEquals("", result.get("microcode"));
		assertEquals("Dell ", result.get("firmware"));
		assertEquals("", result.get("info"));
		assertEquals("1", result.get("__display_id"));
	}

	@Test
	void testInterpretNonContextMappingAwk() {
		final TelemetryManager telemetryManager = new TelemetryManager();

		final List<String> row = List.of(
			"arg1",
			"arg2",
			"arg3",
			"arg4",
			"arg5",
			"1074000000",
			"1000000000",
			"1048576",
			"4000"
		);

		final MappingProcessor mappingProcessor = MappingProcessor
			.builder()
			.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
			.telemetryManager(telemetryManager)
			.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
			.row(row)
			.build();

		{
			final Map<String, String> expected = Map.of(
				"bytes2HumanFormatBase2",
				"1.00 GiB",
				"bytes2HumanFormatBase10",
				"1.00 GB",
				"mebiBytes2HumanFormat",
				"1.00 TiB",
				"megaHertz2HumanFormat",
				"4.00 GHz",
				"join",
				"arg1 arg2 arg3",
				"failed",
				"",
				"outOfBounds",
				""
			);

			final Map<String, String> keyValuePairs = Map.of(
				"bytes2HumanFormatBase2",
				"${awk::bytes2HumanFormatBase2($6)}",
				"bytes2HumanFormatBase10",
				"${awk::bytes2HumanFormatBase10($7)}",
				"mebiBytes2HumanFormat",
				"${awk::mebiBytes2HumanFormat($8)}",
				"megaHertz2HumanFormat",
				"${awk::megaHertz2HumanFormat($9)}",
				"join",
				"${awk::join(\" \", $1, $2, $3)}",
				"failed",
				"${awk::asdbytes2HumanFormatBase2($6)}",
				"outOfBounds",
				"${awk::bytes2HumanFormatBase2($100)}"
			);

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}
	}

	@Test
	void testInterpretNonContextMappingRate() {
		Monitor monitor = Monitor.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(Map.of("enclosure", Map.of("monitor", monitor)))
			.build();

		final MetricFactory metricFactory = new MetricFactory(Constants.HOSTNAME, new ConnectorStore());
		metricFactory.collectNumberMetric(monitor, "__hw.enclosure.power.rate_from", 1000.0, 120000L);
		monitor.getMetric("__hw.enclosure.power.rate_from", NumberMetric.class).save();

		// Correct values
		{
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.power", "rate($1)")).build())
				.row(List.of("2000"))
				.collectTime(240000L)
				.build();

			final Map<String, String> expected = Map.of("hw.enclosure.power", Double.valueOf(1000.0 / 120.0).toString());

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}

		// Non Double value
		{
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.power", "rate($1)")).build())
				.row(List.of("value"))
				.collectTime(240000L)
				.build();

			final Map<String, String> expected = Map.of("hw.enclosure.power", "");

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}

		// No collect time
		{
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.power", "rate($1)")).build())
				.row(List.of("2000"))
				.build();

			final Map<String, String> expected = Map.of("hw.enclosure.power", "");

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}
	}

	@Test
	void testInterpretNonContextMappingFakeCounter() {
		Monitor monitor = Monitor.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(Map.of("enclosure", Map.of("monitor", monitor)))
			.build();

		final MetricFactory metricFactory = new MetricFactory(Constants.HOSTNAME, new ConnectorStore());
		metricFactory.collectNumberMetric(monitor, "__hw.enclosure.energy.fake_counter_from", 1000.0, 120000L);
		monitor.getMetric("__hw.enclosure.energy.fake_counter_from", NumberMetric.class).save();

		// Correct values with no previous value
		{
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.energy", "fakeCounter($1)")).build())
				.row(List.of("2000"))
				.collectTime(240000L)
				.build();

			final Map<String, String> expected = Map.of("hw.enclosure.energy", Double.valueOf(240000.0).toString());

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}

		metricFactory.collectNumberMetric(monitor, "hw.enclosure.energy", 120000.0, 120000L);
		monitor.getMetric("hw.enclosure.energy", NumberMetric.class).save();

		// Correct values with previous value
		{
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.energy", "fakeCounter($1)")).build())
				.row(List.of("2000"))
				.collectTime(240000L)
				.build();

			final Map<String, String> expected = Map.of("hw.enclosure.energy", Double.valueOf(360000.0).toString());

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}

		// Non Double value
		{
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.energy", "fakeCounter($1)")).build())
				.row(List.of("value"))
				.collectTime(240000)
				.build();

			final Map<String, String> expected = Map.of("hw.enclosure.energy", "");

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}

		// No collect time
		{
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.energy", "fakeCounter($1)")).build())
				.row(List.of("2000"))
				.build();

			final Map<String, String> expected = Map.of("hw.enclosure.energy", "");

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}
	}

	@Test
	void testInterpretNonContextMappingLegacyPowerSupplyUtilization() {
		// Correct values
		{
			Monitor monitor = Monitor.builder().build();

			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.monitors(Map.of("enclosure", Map.of("monitor", monitor)))
				.build();

			final MetricFactory metricFactory = new MetricFactory(Constants.HOSTNAME, new ConnectorStore());
			metricFactory.collectNumberMetric(monitor, "hw.power_supply.limit", 1000.0, 120000L);
			monitor.getMetric("hw.power_supply.limit", NumberMetric.class).save();

			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(
					Mapping.builder().metrics(Map.of("hw.power_supply.utilization", "legacyPowerSupplyUtilization($1)")).build()
				)
				.row(List.of("500"))
				.collectTime(240000L)
				.build();

			final Map<String, String> expected = Map.of("hw.power_supply.utilization", Double.valueOf(0.5).toString());

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}

		// Non Double value
		{
			Monitor monitor = Monitor.builder().build();

			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.monitors(Map.of("enclosure", Map.of("monitor", monitor)))
				.build();

			final MetricFactory metricFactory = new MetricFactory(Constants.HOSTNAME, new ConnectorStore());
			metricFactory.collectNumberMetric(monitor, "hw.power_supply.limit", 1000.0, 120000L);
			monitor.getMetric("hw.power_supply.limit", NumberMetric.class).save();

			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(
					Mapping.builder().metrics(Map.of("hw.power_supply.utilization", "legacyPowerSupplyUtilization($1)")).build()
				)
				.row(List.of("value"))
				.collectTime(240000L)
				.build();

			final Map<String, String> expected = Map.of("hw.power_supply.utilization", "");

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}

		// No limit metric
		{
			Monitor monitor = Monitor.builder().build();

			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.monitors(Map.of("enclosure", Map.of("monitor", monitor)))
				.build();

			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
				.telemetryManager(telemetryManager)
				.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
				.mapping(
					Mapping.builder().metrics(Map.of("hw.power_supply.utilization", "legacyPowerSupplyUtilization($1)")).build()
				)
				.row(List.of("value"))
				.collectTime(240000L)
				.build();

			final Map<String, String> expected = Map.of("hw.power_supply.utilization", "");

			mappingProcessor.interpretNonContextMappingMetrics();

			assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
		}
	}

	@Test
	void testInterpretContextMappingLegacyLedStatus() {
		Monitor monitor = Monitor
			.builder()
			.attributes(Map.of("__on_status", "ok", "__off_status", "failed", "__blinking_status", "degraded"))
			.build();

		final Map<String, String> keyValuePairs = Map.of(
			"on",
			"legacyLedStatus(on)",
			"off",
			"legacyLedStatus(off)",
			"blinking",
			"legacyLedStatus(blinking)",
			"wrong input",
			"legacyLedStatus(broken)"
		);

		final MappingProcessor mappingProcessor = MappingProcessor
			.builder()
			.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
			.mapping(Mapping.builder().source(HARDCODED_SOURCE).metrics(keyValuePairs).build())
			.build();

		final Map<String, String> expected = Map.of("on", "ok", "off", "failed", "blinking", "degraded", "wrong input", "");

		mappingProcessor.interpretNonContextMapping(keyValuePairs);
		assertEquals(expected, mappingProcessor.interpretContextMappingMetrics(monitor));
	}

	@Test
	void testInterpretNonContextMappingComputePowerShareRatio() {
		final Monitor monitor = new Monitor();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(Map.of("vm", Map.of("monitor", monitor)))
			.build();

		final MappingProcessor mappingProcessor = MappingProcessor
			.builder()
			.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
			.telemetryManager(telemetryManager)
			.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
			.build();

		{
			final Map<String, String> keyValuePairs = Map.of(HW_VM_POWER_RATIO, "computePowerShareRatio(10)");
			final Map<String, String> expected = Map.of(HW_VM_POWER_RATIO_RAW_POWER_SHARE, "10.0");

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}

		{
			final Map<String, String> keyValuePairs = Map.of(HW_VM_POWER_RATIO, "computePowerShareRatio(ten)");
			final Map<String, String> expected = Map.of(HW_VM_POWER_RATIO_RAW_POWER_SHARE, "");

			assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));
		}
	}

	@Test
	void testInterpretNonContextMappingLookup() {
		Monitor monitor = Monitor.builder().attributes(Map.of("id", "3", "controller_number", "2")).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(Map.of("disk_controller", Map.of("monitor", monitor)))
			.build();

		final List<String> row = List.of("randomValue", "2");

		final MappingProcessor mappingProcessor = MappingProcessor
			.builder()
			.jobInfo(JobInfo.builder().connectorId(MY_CONNECTOR_1_NAME).build())
			.telemetryManager(telemetryManager)
			.mapping(Mapping.builder().source(HARDCODED_SOURCE).build())
			.row(row)
			.build();

		final Map<String, String> expected = Map.of("hw.parent.id", "3");

		final Map<String, String> keyValuePairs = Map.of(
			"hw.parent.id",
			"lookup(\"disk_controller\", \"id\", \"controller_number\", $2)"
		);

		assertEquals(expected, mappingProcessor.interpretNonContextMapping(keyValuePairs));

		// Invalid lookup values tests

		final Map<String, String> expectedNull = new HashMap<>();
		expectedNull.put("hw.parent.id", null);

		final Map<String, String> keyValuePairsTooManyArguments = Map.of(
			"hw.parent.id",
			"lookup(\"disk_controller\", \"id\", \"controller_number\", $2, \"extraValue\")"
		);

		assertEquals(expectedNull, mappingProcessor.interpretNonContextMapping(keyValuePairsTooManyArguments));

		final Map<String, String> keyValuePairsInvalidFirstArgument = Map.of(
			"hw.parent.id",
			"lookup(\"$11\", \"id\", \"controller_number\", $2)"
		);

		assertEquals(expectedNull, mappingProcessor.interpretNonContextMapping(keyValuePairsInvalidFirstArgument));

		final Map<String, String> keyValuePairsInvalidSecondArgument = Map.of(
			"hw.parent.id",
			"lookup(\"disk_controller\", $11, \"controller_number\", $2)"
		);

		assertEquals(expectedNull, mappingProcessor.interpretNonContextMapping(keyValuePairsInvalidSecondArgument));

		final Map<String, String> keyValuePairsInvalidThirdArgument = Map.of(
			"hw.parent.id",
			"lookup(\"disk_controller\", \"id\", $11, $2)"
		);

		assertEquals(expectedNull, mappingProcessor.interpretNonContextMapping(keyValuePairsInvalidThirdArgument));

		final Map<String, String> keyValuePairsInvalidFourthArgument = Map.of(
			"hw.parent.id",
			"lookup(\"disk_controller\", \"id\", \"controller_number\", $11)"
		);

		assertEquals(expectedNull, mappingProcessor.interpretNonContextMapping(keyValuePairsInvalidFourthArgument));

		final Map<String, String> keyValuePairsMonitorTypeNotFound = Map.of(
			"hw.parent.id",
			"lookup(\"enclosure\", \"id\", \"controller_number\", $2)"
		);

		assertEquals(expectedNull, mappingProcessor.interpretNonContextMapping(keyValuePairsMonitorTypeNotFound));

		final Map<String, String> keyValuePairsMonitorWithAttributeValueNotFound = Map.of(
			"hw.parent.id",
			"lookup(\"disk_controller\", \"id\", \"wrongAttribute\", $2)"
		);

		assertEquals(
			expectedNull,
			mappingProcessor.interpretNonContextMapping(keyValuePairsMonitorWithAttributeValueNotFound)
		);
	}
}
