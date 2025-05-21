package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.PowerMeasurement;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.MonoInstanceCollect;
import org.metricshub.engine.connector.model.monitor.task.MultiInstanceCollect;
import org.metricshub.engine.connector.model.monitor.task.Simple;

class PowerMeasurementStatusUpdateTest {

	private ConnectorIdentity connectorIdentity = ConnectorIdentity
		.builder()
		.detection(Detection.builder().appliesTo(Set.of(DeviceKind.OTHER)).tags(Set.of("hardware")).build())
		.build();

	@Test
	void testMeasuredWithMultiInstanceCollect() {
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
		connector.setConnectorIdentity(connectorIdentity);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertEquals(PowerMeasurement.MEASURED, connector.getPowerMeasurement(), "Power measurement should be MEASURED");
	}

	@Test
	void testMeasuredWithMonoInstanceCollect() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
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
		assertEquals(PowerMeasurement.MEASURED, connector.getPowerMeasurement(), "Power measurement should be MEASURED");
	}

	@Test
	void testMeasuredWithSimpleJob() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
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
		assertEquals(PowerMeasurement.MEASURED, connector.getPowerMeasurement(), "Power measurement should be MEASURED");
	}

	@Test
	void testEstimatedWhenNoPowerMetricPresent() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.collect(
						MonoInstanceCollect
							.builder()
							.mapping(Mapping.builder().metrics(Map.of("temperature", "$1")).build())
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertEquals(PowerMeasurement.ESTIMATED, connector.getPowerMeasurement(), "Power measurement should be ESTIMATED");
	}

	@Test
	void testEstimatedWithNullMetrics() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
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
		assertEquals(PowerMeasurement.ESTIMATED, connector.getPowerMeasurement(), "Power measurement should be ESTIMATED");
	}

	@Test
	void testNullWithNoMonitors() {
		{
			final Connector connector = new Connector();
			connector.setConnectorIdentity(connectorIdentity);
			connector.setMonitors(null);
			new PowerMeasurementStatusUpdate().doUpdate(connector);
			// The connector should not have 0 monitors
			assertNull(connector.getPowerMeasurement(), "Power measurement should be null");
		}
		{
			final Connector connector = new Connector();
			connector.setConnectorIdentity(connectorIdentity);
			connector.setMonitors(Map.of());
			new PowerMeasurementStatusUpdate().doUpdate(connector);
			// The connector should not have 0 monitors
			assertNull(connector.getPowerMeasurement(), "Power measurement should be null");
		}
	}

	@Test
	void testEstimatedWithNoEnclosureMonitor() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setMonitors(Map.of("storage", StandardMonitorJob.standardBuilder().build()));
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertEquals(PowerMeasurement.ESTIMATED, connector.getPowerMeasurement(), "Power measurement should be ESTIMATED");
	}

	@Test
	void testEstimatedWithUnsupportedMonitorType() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		final MonitorJob unknown = new MonitorJob() {
			private static final long serialVersionUID = 1L;

			public Set<String> getKeys() {
				return Set.of();
			}
		};
		connector.setMonitors(Map.of("enclosure", unknown));
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertEquals(PowerMeasurement.ESTIMATED, connector.getPowerMeasurement(), "Power measurement should be ESTIMATED");
	}

	@Test
	void testConditionalPowerMetric() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.collect(
						MonoInstanceCollect
							.builder()
							.mapping(
								Mapping
									.builder()
									.metrics(Map.of("hw.enclosure.power", "$3"))
									.conditionalCollection(Map.of("hw.enclosure.energy", "$5"))
									.build()
							)
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertEquals(
			PowerMeasurement.CONDITIONAL,
			connector.getPowerMeasurement(),
			"Power measurement should be CONDITIONAL"
		);
	}

	@Test
	void testOnlyConditionalPowerMetric() {
		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.collect(
						MonoInstanceCollect
							.builder()
							.mapping(Mapping.builder().conditionalCollection(Map.of("hw.enclosure.power", "$3")).build())
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertEquals(PowerMeasurement.ESTIMATED, connector.getPowerMeasurement(), "Power measurement should be ESTIMATED");
	}

	@Test
	void testPowerMetricNoHardwareConnector() {
		final Connector connector = new Connector();
		final ConnectorIdentity connectorIdentity = ConnectorIdentity
			.builder()
			.detection(Detection.builder().appliesTo(Set.of(DeviceKind.OTHER)).tags(Set.of("application")).build())
			.build();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setMonitors(
			Map.of(
				"enclosure",
				StandardMonitorJob
					.standardBuilder()
					.collect(
						MonoInstanceCollect
							.builder()
							.mapping(
								Mapping
									.builder()
									.metrics(Map.of("hw.enclosure.power", "$3"))
									.conditionalCollection(Map.of("hw.enclosure.energy", "$5"))
									.build()
							)
							.build()
					)
					.build()
			)
		);
		new PowerMeasurementStatusUpdate().doUpdate(connector);
		assertNull(connector.getPowerMeasurement(), "Power measurement should be null");
	}
}
