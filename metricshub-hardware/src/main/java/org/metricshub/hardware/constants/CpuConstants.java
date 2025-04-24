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

public class CpuConstants {

	public static final String HW_ENERGY_CPU_METRIC = "hw.energy{hw.type=\"cpu\"}";
	public static final String HW_POWER_CPU_METRIC = "hw.power{hw.type=\"cpu\"}";
	public static final String HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX = "hw.cpu.speed.limit{limit_type=\"max\"}";
	public static final String HW_HOST_CPU_THERMAL_DISSIPATION_RATE = "__hw.host.cpu.thermal_dissipation_rate";
	public static final String CPU_MAXIMUM_SPEED = "hw.cpu.speed.limit{limit_type=\"max\"}";
	public static final Pattern CPU_TRIM_PATTERN = Pattern.compile("cpu|proc(essor)*", Pattern.CASE_INSENSITIVE);
}
