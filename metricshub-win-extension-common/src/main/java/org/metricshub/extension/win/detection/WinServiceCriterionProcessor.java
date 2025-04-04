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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.List;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;

/**
 * A class responsible for processing Service criteria to evaluate service states.
 * It provides a method to retrieve service informations through WMI or WinRm , evaluates the results against expected outcomes,
 * and generates criterion test results accordingly.
 */
@RequiredArgsConstructor
public class WinServiceCriterionProcessor {

	@NonNull
	private WmiDetectionService wmiDetectionService;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	/**
	 * Processes a Windows Service criterion by executing a WMI request to get the windows service then evaluating its state.
	 * The method retrieves Win configuration, executes the WMI request to get the service name and its state,
	 * and checks the result against WMI result. It then returns a
	 * {@link CriterionTestResult} indicating the success or failure of the criterion evaluation.
	 *
	 * @param serviceCriterion The service criterion including the service name to check.
	 * @param telemetryManager The telemetry manager providing access to host configuration and WMI/WinRM credentials.
	 * @return A {@link CriterionTestResult} representing the outcome of the criterion evaluation.
	 */
	public CriterionTestResult process(final ServiceCriterion serviceCriterion, final TelemetryManager telemetryManager) {
		// Sanity checks
		if (serviceCriterion == null) {
			return CriterionTestResult.error(serviceCriterion, "Malformed Service criterion.");
		}

		// Find the configured protocol (WinRM or WMI)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);

		if (winConfiguration == null) {
			return CriterionTestResult.error(
				serviceCriterion,
				"Neither WMI nor WinRM credentials are configured for this host."
			);
		}

		// The host system must be Windows
		if (!DeviceKind.WINDOWS.equals(telemetryManager.getHostConfiguration().getHostType())) {
			return CriterionTestResult.error(serviceCriterion, "Host OS is not Windows. Skipping this test.");
		}

		// Our local system must be Windows
		if (!LocalOsHandler.isWindows()) {
			return CriterionTestResult.error(serviceCriterion, "Local OS is not Windows. Skipping this test.");
		}

		// Check the service name
		final String serviceName = serviceCriterion.getName();
		if (serviceName.isBlank()) {
			return CriterionTestResult.success(serviceCriterion, "Service name is not specified. Skipping this test.");
		}

		// Retrieve the hostname from the IWinConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(winConfiguration.getClass()));

		// Build a new WMI criterion to check the service existence
		final WmiCriterion serviceWmiCriterion = WmiCriterion
			.builder()
			.query(String.format("SELECT Name, State FROM Win32_Service WHERE Name = '%s'", serviceName))
			.namespace("root\\cimv2")
			.build();

		// Perform this WMI test
		final CriterionTestResult wmiTestResult = wmiDetectionService.performDetectionTest(
			hostname,
			winConfiguration,
			serviceWmiCriterion
		);
		if (!wmiTestResult.isSuccess()) {
			return wmiTestResult;
		}

		// The result contains ServiceName;State
		final String result = wmiTestResult.getResult();

		// Check whether the reported state is "Running"
		if (result != null && result.toLowerCase().contains(TABLE_SEP + "running")) {
			return CriterionTestResult.success(
				serviceCriterion,
				String.format("The %s Windows Service is currently running.", serviceName)
			);
		}

		// We're here: no good!
		return CriterionTestResult.failure(
			serviceWmiCriterion,
			String.format("The %s Windows Service is not reported as running:\n%s", serviceName, result) //NOSONAR
		);
	}
}
