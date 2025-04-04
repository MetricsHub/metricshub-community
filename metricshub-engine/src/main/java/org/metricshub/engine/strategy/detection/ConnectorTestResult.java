package org.metricshub.engine.strategy.detection;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.Connector;

/**
 * The {@code ConnectorTestResult} class represents the result of testing criteria for a specific connector during the detection process.
 * It contains information about the tested connector, a list of {@link CriterionTestResult} objects, and a method to check if the connector's criteria are successful.
 *
 * <p>
 * The class is designed to be used as a result container during the connector detection process.
 * It includes information about the tested connector and the results of testing individual criteria associated with the connector.
 * The success of the connector's criteria is determined based on the results of individual criterion tests.
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConnectorTestResult {

	private Connector connector;

	@Default
	private List<CriterionTestResult> criterionTestResults = new ArrayList<>();

	/**
	 * Whether the connector's criteria are successfully executed or not
	 *
	 * @return boolean value
	 */
	public boolean isSuccess() {
		return !criterionTestResults.isEmpty() && criterionTestResults.stream().allMatch(CriterionTestResult::isSuccess);
	}
}
