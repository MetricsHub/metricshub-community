package org.metricshub.engine.alert;

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

import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import java.util.StringJoiner;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;

/**
 * Represents details associated with an alert, including the problem,
 * consequence, and recommended action.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertDetails {

	private String problem;
	private String consequence;
	private String recommendedAction;

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(MetricsHubConstants.NEW_LINE);

		addNonNull(stringJoiner, "Problem           : ", problem);
		addNonNull(stringJoiner, "Consequence       : ", consequence);
		addNonNull(stringJoiner, "Recommended Action: ", recommendedAction);

		return stringJoiner.toString();
	}
}
