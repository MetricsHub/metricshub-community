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

import java.util.regex.Pattern;

public class VmConstants {

	public static final String HW_ENERGY_VM_METRIC = "hw.energy{hw.type=\"vm\"}";
	public static final String HW_POWER_VM_METRIC = "hw.power{hw.type=\"vm\"}";
	public static final String POWER_SOURCE_ID_ATTRIBUTE = "__power_source_id";
	public static final String HW_VM_POWER_SHARE_METRIC = "__hw.vm.power_ratio.raw_power_share";
	public static final String HW_VM_POWER_STATE_METRIC = "hw.power_state{hw.type=\"vm\"}";
	public static final String HOSTNAME = "vm.host.name";
	public static final Pattern VM_TRIM_PATTERN = Pattern.compile("vm", Pattern.CASE_INSENSITIVE);
}
