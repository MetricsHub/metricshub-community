package org.metricshub.hardware.util;

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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HwConstants {

	// Hardware Metrics

	public static final String HW_ENERGY_DISK_CONTROLLER_METRIC = "hw.energy{hw.type=\"disk_controller\"}";
	public static final String HW_POWER_DISK_CONTROLLER_METRIC = "hw.power{hw.type=\"disk_controller\"}";

	public static final String HW_ENERGY_FAN_METRIC = "hw.energy{hw.type=\"fan\"}";
	public static final String HW_POWER_FAN_METRIC = "hw.power{hw.type=\"fan\"}";

	public static final String HW_ENERGY_ROBOTICS_METRIC = "hw.energy{hw.type=\"robotics\"}";
	public static final String HW_POWER_ROBOTICS_METRIC = "hw.power{hw.type=\"robotics\"}";

	public static final String HW_ENERGY_TAPE_DRIVE_METRIC = "hw.energy{hw.type=\"tape_drive\"}";
	public static final String HW_POWER_TAPE_DRIVE_METRIC = "hw.power{hw.type=\"tape_drive\"}";

	public static final String HW_ENERGY_MEMORY_METRIC = "hw.energy{hw.type=\"memory\"}";
	public static final String HW_POWER_MEMORY_METRIC = "hw.power{hw.type=\"memory\"}";

	public static final String HW_ENERGY_PHYSICAL_DISK_METRIC = "hw.energy{hw.type=\"physical_disk\"}";
	public static final String HW_POWER_PHYSICAL_DISK_METRIC = "hw.power{hw.type=\"physical_disk\"}";

	public static final String HW_ENERGY_NETWORK_METRIC = "hw.energy{hw.type=\"network\"}";
	public static final String HW_POWER_NETWORK_METRIC = "hw.power{hw.type=\"network\"}";

	public static final String HW_HOST_MEASURED_POWER = "hw.host.power{quality=\"measured\"}";
	public static final String HW_HOST_MEASURED_ENERGY = "hw.host.energy{quality=\"measured\"}";
	public static final String HW_HOST_ESTIMATED_POWER = "hw.host.power{quality=\"estimated\"}";
	public static final String HW_HOST_ESTIMATED_ENERGY = "hw.host.energy{quality=\"estimated\"}";

	public static final String HW_ENERGY_VM_METRIC = "hw.energy{hw.type=\"vm\"}";
	public static final String HW_POWER_VM_METRIC = "hw.power{hw.type=\"vm\"}";
	public static final String POWER_SOURCE_ID_ATTRIBUTE = "__power_source_id";
	public static final String HW_VM_POWER_SHARE_METRIC = "__hw.vm.power_ratio.raw_power_share";
	public static final String HW_VM_POWER_STATE_METRIC = "hw.power_state{hw.type=\"vm\"}";

	public static final String HW_ENCLOSURE_POWER = "hw.enclosure.power";
	public static final String HW_ENCLOSURE_ENERGY = "hw.enclosure.energy";
	public static final String HW_ENERGY_CPU_METRIC = "hw.energy{hw.type=\"cpu\"}";
	public static final String HW_POWER_CPU_METRIC = "hw.power{hw.type=\"cpu\"}";
	public static final String HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX = "hw.cpu.speed.limit{limit_type=\"max\"}";
	public static final String HW_HOST_CPU_THERMAL_DISSIPATION_RATE = "__hw.host.cpu.thermal_dissipation_rate";
	public static final String CONNECTOR = "connector";
	public static final String ENCLOSURE = "enclosure";
	public static final String PRESENT_STATUS = "hw.status{hw.type=\"%s\", state=\"present\"}";
}
