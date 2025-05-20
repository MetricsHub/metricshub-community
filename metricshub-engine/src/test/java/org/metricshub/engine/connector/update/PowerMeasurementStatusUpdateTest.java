package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.MonoInstanceCollect;
import org.metricshub.engine.connector.model.monitor.task.MultiInstanceCollect;
import org.metricshub.engine.connector.model.monitor.task.Simple;

class PowerMeasurementStatusUpdateTest {

	@Test
	void testDoUpdateReadsEnclosureMultiInstanceCollect() {
		final Connector connector = new Connector();
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.discovery(Discovery.builder().build())
					.collect(
						MultiInstanceCollect
							.builder()
							.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.power", "$3")).build())
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertTrue(connector.isPowerMeasured(), "Power measurement status should be true");
	}

	@Test
	void testDoUpdateReadsEnclosureMonoInstanceCollect() {
		final Connector connector = new Connector();
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.discovery(Discovery.builder().build())
					.collect(
						MonoInstanceCollect
							.builder()
							.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.power", "$3")).build())
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertTrue(connector.isPowerMeasured(), "Power measurement status should be true");
	}

	@Test
	void testDoUpdateReadsEnclosureSimpleJob() {
		final Connector connector = new Connector();
		connector.setMonitors(
			Map.of(
				"enclosure",
				SimpleMonitorJob
					.simpleBuilder()
					.simple(
						Simple.builder().mapping(Mapping.builder().metrics(Map.of("hw.enclosure.power", "$3")).build()).build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertTrue(connector.isPowerMeasured(), "Power measurement status should be true");
	}

	@Test
	void testDoUpdateStandardMonitorJobNoPowerMetric() {
		final Connector connector = new Connector();
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.discovery(Discovery.builder().build())
					.collect(
						MonoInstanceCollect
							.builder()
							.mapping(Mapping.builder().metrics(Map.of("hw.enclosure.temperature", "$2")).build())
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertFalse(connector.isPowerMeasured(), "Power measurement status should be false");
	}

	@Test
	void testDoUpdateSimpleMonitorJobNoPowerMetric() {
		final Connector connector = new Connector();
		connector.setMonitors(
			Map.of(
				"enclosure",
				SimpleMonitorJob
					.simpleBuilder()
					.simple(
						Simple.builder().mapping(Mapping.builder().metrics(Map.of("hw.enclosure.status", "$1")).build()).build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertFalse(connector.isPowerMeasured(), "Power measurement status should be false");
	}

	@Test
	void testDoUpdateWithNoMonitors() {
		final Connector connector = new Connector();
		connector.setMonitors(null);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertFalse(connector.isPowerMeasured(), "Power measurement status should default to false");
	}

	@Test
	void testDoUpdateWithNoEnclosureMonitor() {
		final Connector connector = new Connector();
		connector.setMonitors(Map.of("storage", StandardMonitorJob.standardBuilder().build()));
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertFalse(connector.isPowerMeasured(), "Power measurement status should default to false");
	}

	@Test
	void testDoUpdateWithUnsupportedMonitorType() {
		final Connector connector = new Connector();
		// anonymous class to simulate unknown type
		final MonitorJob unknownMonitorJob = new MonitorJob() {
			private static final long serialVersionUID = 1L;

			@Override
			public Set<String> getKeys() {
				return Set.of();
			}
		};
		connector.setMonitors(Map.of("enclosure", unknownMonitorJob));
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertFalse(connector.isPowerMeasured(), "Power measurement status should default to false");
	}

	@Test
	void testDoUpdateHandlesNullMetricsMap() {
		final Connector connector = new Connector();
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.collect(MonoInstanceCollect.builder().mapping(Mapping.builder().metrics(null).build()).build())
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertFalse(connector.isPowerMeasured(), "Power measurement status should be false");
	}

	@Test
	void testDoUpdateWithIrrelevantMetricKeys() {
		final Connector connector = new Connector();
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.collect(
						MonoInstanceCollect
							.builder()
							.mapping(Mapping.builder().metrics(Map.of("voltage.input", "$5", "temperature.internal", "$3")).build())
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertFalse(connector.isPowerMeasured(), "Power measurement status should be false");
	}
}
