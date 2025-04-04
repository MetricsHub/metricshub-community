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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.LOCALHOST;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.extension.win.IWinConfiguration;

/**
 * A class responsible for processing Process criteria to evaluate local process existence.
 * It provides a method to retrieve process informations through WMI or WinRm , evaluates the results against expected outcomes,
 * and generates criterion test results accordingly.
 */
@RequiredArgsConstructor
public class WinProcessCriterionProcessor {

	@NonNull
	private WmiDetectionService wmiDetectionService;

	/**
	 * Processes the given {@link ProcessCriterion} using the specified Windows configuration to evaluate
	 * if a process is running based on the command line provided. This method constructs a WMI query criterion,
	 * then executes a detection test against the localhost machine.
	 *
	 * @param processCriterion      The process criterion that specifies the command line to look for in the running processes.
	 * @param localWinConfiguration The Windows configuration to be used for the WMI query execution.
	 * @return A {@link CriterionTestResult} indicating the result of the detection test.
	 */
	public CriterionTestResult process(
		final ProcessCriterion processCriterion,
		final IWinConfiguration localWinConfiguration
	) {
		final WmiCriterion criterion = WmiCriterion
			.builder()
			.query("SELECT ProcessId,Name,ParentProcessId,CommandLine FROM Win32_Process")
			.namespace("root\\cimv2")
			.expectedResult(processCriterion.getCommandLine())
			.build();

		return wmiDetectionService.performDetectionTest(LOCALHOST, localWinConfiguration, criterion);
	}
}
