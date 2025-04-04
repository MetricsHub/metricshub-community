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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a condition for triggering alerts based on monitored data.
 * An alert condition includes an operator and a threshold value.
 * Provides a method to create a copy of the condition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertCondition {

	private AlertOperator operator;
	private Double threshold;

	/**
	 * Creates a copy of the alert condition.
	 *
	 * @return A new {@code AlertCondition} instance with the same operator and threshold.
	 */
	public AlertCondition copy() {
		return AlertCondition.builder().operator(operator).threshold(threshold).build();
	}
}
