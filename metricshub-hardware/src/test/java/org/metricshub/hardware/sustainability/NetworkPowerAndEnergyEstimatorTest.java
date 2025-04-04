package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.NETWORK_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.NETWORK_LINK_SPEED_ATTRIBUTE;
import static org.metricshub.hardware.common.Constants.NETWORK_LINK_STATUS_METRIC;
import static org.metricshub.hardware.common.Constants.NETWORK_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.NETWORK_TRANSMITTED_BANDWIDTH_UTILIZATION_METRIC;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.strategy.utils.MappingProcessor;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class NetworkPowerAndEnergyEstimatorTest {

	@Test
	void testEstimatePower() {
		Monitor monitor = Monitor.builder().attributes(new HashMap<>(Map.of("name", "prefix_wan_suffix"))).build();
		TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(1696597422644L)
			.hostConfiguration(HostConfiguration.builder().hostname("localhost").build())
			.build();
		NetworkPowerAndEnergyEstimator networkPowerAndEnergyEstimator = new NetworkPowerAndEnergyEstimator(
			monitor,
			telemetryManager
		);

		// Virtual network card -> estimated power consumption is 0.0
		assertEquals(0.0, networkPowerAndEnergyEstimator.estimatePower());

		// Not virtual network card, linkStatus == 0.0 -> estimated power consumption is 1.0
		monitor.addAttribute("name", "real_network_card");
		monitor.addMetric(NETWORK_LINK_STATUS_METRIC, NumberMetric.builder().value(0.0).build());
		assertEquals(1.0, networkPowerAndEnergyEstimator.estimatePower());

		// linkStatus is up, bandwidthUtilization and linkSpeed are null
		// estimated power consumption is 10.0
		monitor.addMetric(NETWORK_LINK_STATUS_METRIC, NumberMetric.builder().value(1.0).build());
		assertEquals(10.0, networkPowerAndEnergyEstimator.estimatePower());

		// linkStatus is up, bandwidthUtilization is null and linkSpeed = 100.0 MegaBit
		// estimated power consumption is 7.5
		monitor.addMetric(
			NETWORK_LINK_SPEED_ATTRIBUTE,
			NumberMetric.builder().value(100.0 * MappingProcessor.MEGABIT_2_BYTE_FACTOR).build()
		);
		assertEquals(7.5, networkPowerAndEnergyEstimator.estimatePower());

		// linkStatus is up, bandwidthUtilization is null and linkSpeed = 5.0 MegaBit
		// estimated power consumption is 2.0
		monitor.addMetric(
			NETWORK_LINK_SPEED_ATTRIBUTE,
			NumberMetric.builder().value(5.0 * MappingProcessor.MEGABIT_2_BYTE_FACTOR).build()
		);
		assertEquals(2.0, networkPowerAndEnergyEstimator.estimatePower());

		// linkStatus is up, bandwidthUtilization = 0.5, linkSpeed = 100.0 MegaBit
		// estimated power consumption is 7.5
		monitor.addMetric(NETWORK_TRANSMITTED_BANDWIDTH_UTILIZATION_METRIC, NumberMetric.builder().value(0.5).build());
		monitor.addMetric(
			NETWORK_LINK_SPEED_ATTRIBUTE,
			NumberMetric.builder().value(100.0 * MappingProcessor.MEGABIT_2_BYTE_FACTOR).build()
		);
		assertEquals(7.5, networkPowerAndEnergyEstimator.estimatePower());

		// linkStatus is up, bandwidthUtilization = 0.5, linkSpeed = 5.0 MegaBit
		// estimated power consumption is 2.75
		monitor.addMetric(
			NETWORK_LINK_SPEED_ATTRIBUTE,
			NumberMetric.builder().value(5.0 * MappingProcessor.MEGABIT_2_BYTE_FACTOR).build()
		);
		assertEquals(3.75, networkPowerAndEnergyEstimator.estimatePower());
	}

	@Test
	void testEstimateEnergy() {
		Monitor monitor = Monitor
			.builder()
			.attributes(new HashMap<>(Map.of("name", "real_network_card")))
			.metrics(
				new HashMap<>(
					Map.of(
						NETWORK_LINK_STATUS_METRIC,
						NumberMetric.builder().value(1.0).build(),
						NETWORK_TRANSMITTED_BANDWIDTH_UTILIZATION_METRIC,
						NumberMetric.builder().value(0.1).build()
					)
				)
			)
			.build();
		TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(1696597422644L)
			.hostConfiguration(HostConfiguration.builder().hostname("localhost").build())
			.build();
		NetworkPowerAndEnergyEstimator networkPowerAndEnergyEstimator = new NetworkPowerAndEnergyEstimator(
			monitor,
			telemetryManager
		);

		// Estimate energy consumption, no previous collect time
		assertNull(networkPowerAndEnergyEstimator.estimateEnergy());

		// Estimate power consumption
		Double estimatedPower = networkPowerAndEnergyEstimator.estimatePower();
		Double estimatedEnergy = networkPowerAndEnergyEstimator.estimateEnergy();
		assertNull(estimatedEnergy);
		// Create metricFactory and collect power
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		final NumberMetric collectedPowerMetric = metricFactory.collectNumberMetric(
			monitor,
			NETWORK_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Save the collected power metric
		collectedPowerMetric.save();
		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = networkPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(
			monitor,
			NETWORK_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Estimate the energy
		estimatedEnergy = networkPowerAndEnergyEstimator.estimateEnergy();
		assertEquals(330.0, estimatedEnergy);
		final NumberMetric collectedEnergyMetric = metricFactory.collectNumberMetric(
			monitor,
			NETWORK_ENERGY_METRIC,
			estimatedEnergy,
			telemetryManager.getStrategyTime()
		);
		collectedEnergyMetric.save();
		collectedPowerMetric.save();

		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = networkPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(
			monitor,
			NETWORK_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Estimate the energy
		assertEquals(660.0, networkPowerAndEnergyEstimator.estimateEnergy());
	}
}
