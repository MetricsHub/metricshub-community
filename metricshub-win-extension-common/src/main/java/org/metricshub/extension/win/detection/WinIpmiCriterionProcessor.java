package org.metricshub.extension.win.detection;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.util.List;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.metricshub.extension.win.IWinConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * A class responsible for processing IPMI criteria to evaluate IPMI result against specified criteria.
 * It provides a method to retrieve hardware informations through WMI or WinRm , evaluates the results against expected outcomes,
 * and generates criterion test results accordingly.
 */
@RequiredArgsConstructor
public class WinIpmiCriterionProcessor {

	@NonNull
	private WmiDetectionService wmiDetectionService;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	/**
	 * Processes an {@link IpmiCriterion} using the telemetry manager to perform a detection test
	 * based on the Windows management protocol configuration. This method retrieves the Windows
	 * configuration for the telemetry context, constructs a WMI query, and executes a detection test
	 * using a new WMI criterion.
	 *
	 * @param ipmiCriterion    The IPMI criterion to be tested.
	 * @param telemetryManager Provides host configuration and properties.
	 * @return {@link CriterionTestResult} indicating the result of the detection test, including success or error information.
	 */
	public CriterionTestResult process(final IpmiCriterion ipmiCriterion, TelemetryManager telemetryManager) {
		// Find the configured Windows protocol (WMI or WinRM)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);
		if (winConfiguration == null) {
			return CriterionTestResult.error(
				ipmiCriterion,
				"Neither WMI nor WinRM credentials are configured for this host."
			);
		}

		final WmiCriterion ipmiWmiCriterion = WmiCriterion
			.builder()
			.query("SELECT Description FROM ComputerSystem")
			.namespace("root\\hardware")
			.build();

		return wmiDetectionService.performDetectionTest(
			telemetryManager.getHostname(List.of(winConfiguration.getClass())),
			winConfiguration,
			ipmiWmiCriterion
		);
	}
}
