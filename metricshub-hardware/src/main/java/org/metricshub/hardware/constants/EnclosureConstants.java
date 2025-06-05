package org.metricshub.hardware.constants;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;

/**
 * Constants for enclosure-related metrics.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EnclosureConstants {

	public static final String HW_ENCLOSURE_POWER = MetricsHubConstants.HW_ENCLOSURE_POWER_METRIC;
	public static final String HW_ENCLOSURE_ENERGY = MetricsHubConstants.HW_ENCLOSURE_ENERGY_METRIC;
	public static final String BLADE_ENCLOSURE = "Blade Enclosure";
	public static final String COMPUTER = "Computer";
	public static final String STORAGE = "Storage";
	public static final String SWITCH = "Switch";
	public static final String ENCLOSURE_TYPE = "type";
}
