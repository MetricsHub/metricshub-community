package org.metricshub.web.dto.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

class TelemetryResultConverterTest {

	private static final double TOLERANCE = 0.001;

	@Test
	void testNullTelemetryManagerReturnsNull() {
		assertNull(TelemetryResultConverter.toMonitorsVo(null));
	}

	@Test
	void testNullMonitorsMapReturnsNull() {
		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.setMonitors(null);
		assertNull(TelemetryResultConverter.toMonitorsVo(tm));
	}

	@Test
	void testEmptyMonitorsMapReturnsNull() {
		final TelemetryManager tm = TelemetryManager.builder().build();
		assertNull(TelemetryResultConverter.toMonitorsVo(tm));
	}

	@Test
	void testInternalKeysExcluded() {
		final Monitor monitor = Monitor.builder().build();
		monitor.setType("disk");
		monitor.getAttributes().put("__internal_attr", "hidden");
		monitor.getAttributes().put("device", "/dev/sda");

		final NumberMetric internalMetric = NumberMetric.builder().name("__internal.metric").value(42.0).build();
		monitor.addMetric("__internal.metric", internalMetric);

		final NumberMetric normalMetric = NumberMetric.builder().name("disk.utilization").value(45.5).build();
		monitor.addMetric("disk.utilization", normalMetric);

		monitor.getLegacyTextParameters().put("__internal_param", "hidden");
		monitor.getLegacyTextParameters().put("filesystem", "ext4");

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("disk", Map.of("disk-1", monitor));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		assertNotNull(result);

		final List<MonitorTypeItem> diskItems = result.getMonitors().get("disk");
		assertNotNull(diskItems);
		assertEquals(2, diskItems.size());

		final MonitorVo monitorVo = (MonitorVo) diskItems.get(0);
		assertFalse(monitorVo.getAttributes().containsKey("__internal_attr"));
		assertTrue(monitorVo.getAttributes().containsKey("device"));
		assertFalse(monitorVo.getMetrics().containsKey("__internal.metric"));
		assertTrue(monitorVo.getMetrics().containsKey("disk.utilization"));
		assertFalse(monitorVo.getTextParams().containsKey("__internal_param"));
		assertTrue(monitorVo.getTextParams().containsKey("filesystem"));
	}

	@Test
	void testCounterMetricEmitsRate() {
		final Monitor monitor = Monitor.builder().build();
		monitor.setType("enclosure");

		final NumberMetric counterMetric = NumberMetric
			.builder()
			.name("hw.enclosure.energy")
			.value(5000.0)
			.metricType("Counter")
			.build();
		counterMetric.setRate(20.84);
		monitor.addMetric("hw.enclosure.energy", counterMetric);

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("enclosure", Map.of("enc-1", monitor));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		final MonitorVo monitorVo = (MonitorVo) result.getMonitors().get("enclosure").get(0);

		assertFalse(monitorVo.getMetrics().containsKey("hw.enclosure.energy"));
		assertTrue(monitorVo.getMetrics().containsKey("rate(hw.enclosure.energy)"));
		assertEquals(20.84, (Double) monitorVo.getMetrics().get("rate(hw.enclosure.energy)"), TOLERANCE);
	}

	@Test
	void testCounterNullRateExcluded() {
		final Monitor monitor = Monitor.builder().build();
		monitor.setType("enclosure");

		// Counter with null rate (first collect cycle)
		final NumberMetric counterMetric = NumberMetric
			.builder()
			.name("hw.enclosure.energy")
			.value(5000.0)
			.metricType("Counter")
			.build();
		// rate is null by default
		monitor.addMetric("hw.enclosure.energy", counterMetric);

		// Also add a Gauge metric
		final NumberMetric gaugeMetric = NumberMetric
			.builder()
			.name("hw.enclosure.power")
			.value(200.0)
			.metricType("Gauge")
			.build();
		monitor.addMetric("hw.enclosure.power", gaugeMetric);

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("enclosure", Map.of("enc-1", monitor));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		final MonitorVo monitorVo = (MonitorVo) result.getMonitors().get("enclosure").get(0);

		// Counter with null rate is excluded
		assertFalse(monitorVo.getMetrics().containsKey("hw.enclosure.energy"));
		assertFalse(monitorVo.getMetrics().containsKey("rate(hw.enclosure.energy)"));
		// Gauge is included
		assertTrue(monitorVo.getMetrics().containsKey("hw.enclosure.power"));
		assertEquals(200.0, (Double) monitorVo.getMetrics().get("hw.enclosure.power"), TOLERANCE);
	}

	@Test
	void testGaugeMetricKeepsOriginalKey() {
		final Monitor monitor = Monitor.builder().build();
		monitor.setType("host");

		final NumberMetric gauge = NumberMetric.builder().name("hw.host.power").value(250.0).metricType("Gauge").build();
		monitor.addMetric("hw.host.power", gauge);

		final NumberMetric unknownType = NumberMetric.builder().name("some.metric").value(100.0).build();
		monitor.addMetric("some.metric", unknownType);

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("host", Map.of("host-1", monitor));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		final MonitorVo monitorVo = (MonitorVo) result.getMonitors().get("host").get(0);

		assertTrue(monitorVo.getMetrics().containsKey("hw.host.power"));
		assertEquals(250.0, (Double) monitorVo.getMetrics().get("hw.host.power"), TOLERANCE);

		assertTrue(monitorVo.getMetrics().containsKey("some.metric"));
		assertEquals(100.0, (Double) monitorVo.getMetrics().get("some.metric"), TOLERANCE);
	}

	@Test
	void testStateSetMetricEmitsStringValue() {
		final Monitor monitor = Monitor.builder().build();
		monitor.setType("disk");

		final StateSetMetric status = StateSetMetric
			.builder()
			.name("hw.status")
			.value("ok")
			.stateSet(new String[] { "ok", "degraded", "failed" })
			.build();
		monitor.addMetric("hw.status", status);

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("disk", Map.of("disk-1", monitor));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		final MonitorVo monitorVo = (MonitorVo) result.getMonitors().get("disk").get(0);

		assertTrue(monitorVo.getMetrics().containsKey("hw.status"));
		assertEquals("ok", monitorVo.getMetrics().get("hw.status"));
	}

	@Test
	void testSummaryStatistics() {
		final Monitor disk1 = Monitor.builder().build();
		disk1.setType("disk");
		disk1.getAttributes().put("device", "/dev/sda");
		disk1.addMetric("disk.utilization", NumberMetric.builder().name("disk.utilization").value(45.5).build());
		disk1.addMetric(
			"hw.status",
			StateSetMetric
				.builder()
				.name("hw.status")
				.value("ok")
				.stateSet(new String[] { "ok", "degraded", "failed" })
				.build()
		);

		final Monitor disk2 = Monitor.builder().build();
		disk2.setType("disk");
		disk2.getAttributes().put("device", "/dev/sdb");
		disk2.addMetric("disk.utilization", NumberMetric.builder().name("disk.utilization").value(78.2).build());
		disk2.addMetric(
			"hw.status",
			StateSetMetric
				.builder()
				.name("hw.status")
				.value("degraded")
				.stateSet(new String[] { "ok", "degraded", "failed" })
				.build()
		);

		final TelemetryManager tm = TelemetryManager.builder().build();
		final Map<String, Monitor> diskMonitors = new LinkedHashMap<>();
		diskMonitors.put("disk-1", disk1);
		diskMonitors.put("disk-2", disk2);
		tm.getMonitors().put("disk", diskMonitors);

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		final List<MonitorTypeItem> diskItems = result.getMonitors().get("disk");
		assertEquals(3, diskItems.size());

		final MonitorTypeSummaryVo summary = (MonitorTypeSummaryVo) diskItems.get(2);
		assertEquals(2, summary.getTotalMonitors());

		// Numeric stats
		assertNotNull(summary.getNumericMetrics());
		final NumericMetricStatsVo utilStats = summary.getNumericMetrics().get("disk.utilization");
		assertNotNull(utilStats);
		assertEquals(2, utilStats.getCount());
		assertEquals(45.5 + 78.2, utilStats.getSum(), TOLERANCE);
		assertEquals(45.5, utilStats.getMin(), TOLERANCE);
		assertEquals(78.2, utilStats.getMax(), TOLERANCE);
		assertEquals((45.5 + 78.2) / 2, utilStats.getAvg(), TOLERANCE);

		// State set counts
		assertNotNull(summary.getStateSetMetrics());
		final List<StateSetCountVo> statusCounts = summary.getStateSetMetrics().get("hw.status");
		assertNotNull(statusCounts);
		assertEquals(2, statusCounts.size());
		final Map<String, Integer> countMap = new HashMap<>();
		statusCounts.forEach(sc -> countMap.put(sc.getValue(), sc.getCount()));
		assertEquals(1, countMap.get("ok"));
		assertEquals(1, countMap.get("degraded"));
	}

	@Test
	void testCounterRateInSummary() {
		final Monitor enc1 = Monitor.builder().build();
		enc1.setType("enclosure");
		final NumberMetric energy1 = NumberMetric
			.builder()
			.name("hw.enclosure.energy")
			.value(5000.0)
			.metricType("Counter")
			.build();
		energy1.setRate(20.84);
		enc1.addMetric("hw.enclosure.energy", energy1);

		final Monitor enc2 = Monitor.builder().build();
		enc2.setType("enclosure");
		final NumberMetric energy2 = NumberMetric
			.builder()
			.name("hw.enclosure.energy")
			.value(8000.0)
			.metricType("Counter")
			.build();
		energy2.setRate(30.16);
		enc2.addMetric("hw.enclosure.energy", energy2);

		final TelemetryManager tm = TelemetryManager.builder().build();
		final Map<String, Monitor> encMonitors = new LinkedHashMap<>();
		encMonitors.put("enc-1", enc1);
		encMonitors.put("enc-2", enc2);
		tm.getMonitors().put("enclosure", encMonitors);

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		final MonitorTypeSummaryVo summary = (MonitorTypeSummaryVo) result.getMonitors().get("enclosure").get(2);

		assertNotNull(summary.getNumericMetrics());
		assertTrue(summary.getNumericMetrics().containsKey("rate(hw.enclosure.energy)"));
		assertFalse(summary.getNumericMetrics().containsKey("hw.enclosure.energy"));

		final NumericMetricStatsVo stats = summary.getNumericMetrics().get("rate(hw.enclosure.energy)");
		assertEquals(2, stats.getCount());
		assertEquals(20.84 + 30.16, stats.getSum(), TOLERANCE);
	}

	@Test
	void testCounterNullRateExcludedFromSummary() {
		final Monitor enc = Monitor.builder().build();
		enc.setType("enclosure");

		final NumberMetric energy = NumberMetric
			.builder()
			.name("hw.enclosure.energy")
			.value(5000.0)
			.metricType("Counter")
			.build();
		// rate is null (first collect)
		enc.addMetric("hw.enclosure.energy", energy);

		final StateSetMetric status = StateSetMetric
			.builder()
			.name("hw.status")
			.value("ok")
			.stateSet(new String[] { "ok" })
			.build();
		enc.addMetric("hw.status", status);

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("enclosure", Map.of("enc-1", enc));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		final MonitorTypeSummaryVo summary = (MonitorTypeSummaryVo) result.getMonitors().get("enclosure").get(1);

		// No numeric metrics since the only numeric was a counter with null rate
		assertNull(summary.getNumericMetrics());
		// StateSet should still be present
		assertNotNull(summary.getStateSetMetrics());
	}

	@Test
	void testEmptyAttributesAndMetricsExcluded() {
		final Monitor monitor = Monitor.builder().build();
		monitor.setType("cpu");
		// No attributes, no metrics, no text params
		// getAttributes() returns empty HashMap, getMetrics() returns empty HashMap

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("cpu", Map.of("cpu-0", monitor));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		assertNotNull(result);

		final MonitorVo monitorVo = (MonitorVo) result.getMonitors().get("cpu").get(0);
		assertNull(monitorVo.getAttributes());
		assertNull(monitorVo.getMetrics());
		assertNull(monitorVo.getTextParams());
	}

	@Test
	void testMultipleMonitorTypes() {
		final Monitor disk = Monitor.builder().build();
		disk.setType("disk");
		disk.addMetric("disk.utilization", NumberMetric.builder().name("disk.utilization").value(50.0).build());

		final Monitor cpu = Monitor.builder().build();
		cpu.setType("cpu");
		cpu.addMetric("cpu.utilization", NumberMetric.builder().name("cpu.utilization").value(30.0).build());

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("disk", Map.of("disk-1", disk));
		tm.getMonitors().put("cpu", Map.of("cpu-0", cpu));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);
		assertNotNull(result);
		assertEquals(2, result.getMonitors().size());
		assertTrue(result.getMonitors().containsKey("disk"));
		assertTrue(result.getMonitors().containsKey("cpu"));

		// Each type has 1 monitor + 1 summary = 2 items
		assertEquals(2, result.getMonitors().get("disk").size());
		assertEquals(2, result.getMonitors().get("cpu").size());
	}

	@Test
	void testJacksonPolymorphicSerialization() throws Exception {
		final Monitor monitor = Monitor.builder().build();
		monitor.setType("cpu");
		monitor.getAttributes().put("core", "0");
		monitor.addMetric("cpu.utilization", NumberMetric.builder().name("cpu.utilization").value(23.4).build());
		monitor.addMetric(
			"hw.status",
			StateSetMetric
				.builder()
				.name("hw.status")
				.value("ok")
				.stateSet(new String[] { "ok", "degraded", "failed" })
				.build()
		);

		final TelemetryManager tm = TelemetryManager.builder().build();
		tm.getMonitors().put("cpu", Map.of("cpu-0", monitor));

		final MonitorsVo result = TelemetryResultConverter.toMonitorsVo(tm);

		final ObjectMapper mapper = new ObjectMapper();
		final String json = mapper.writeValueAsString(result);

		// JSON should contain the Jackson type discriminators
		assertTrue(json.contains("\"type\":\"monitor\""));
		assertTrue(json.contains("\"type\":\"summary\""));

		// Round-trip deserialization
		final MonitorsVo deserialized = mapper.readValue(json, MonitorsVo.class);
		assertNotNull(deserialized);
		assertEquals(result.getMonitors().size(), deserialized.getMonitors().size());
	}
}
