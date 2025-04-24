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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonConstants {

	public static final String HW_HOST_MEASURED_POWER = "hw.host.power{quality=\"measured\"}";
	public static final String HW_HOST_MEASURED_ENERGY = "hw.host.energy{quality=\"measured\"}";
	public static final String HW_HOST_ESTIMATED_POWER = "hw.host.power{quality=\"estimated\"}";
	public static final String HW_HOST_ESTIMATED_ENERGY = "hw.host.energy{quality=\"estimated\"}";
	public static final String CONNECTOR = "connector";
	public static final String ENCLOSURE = "enclosure";
	public static final String PRESENT_STATUS = "hw.status{hw.type=\"%s\", state=\"present\"}";
	public static final String WHITE_SPACE_REPEAT_REGEX = "[ \t]+";
	public static final String LOCALHOST = "localhost";
	public static final String MODEL = "model";
	public static final String VENDOR = "vendor";
	public static final String DEVICE_ID = "id";
	public static final String ID_COUNT = "idCount";
	public static final String DISPLAY_ID = "__display_id";
	public static final String LOCATION = "location";
	public static final String ADDITIONAL_LABEL = "additionalLabel";
	public static final int ID_MAX_LENGTH = 10;
}
