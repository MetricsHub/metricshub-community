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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Constants for network-related metrics.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NetworkConstants {

	public static final String HW_ENERGY_NETWORK_METRIC = "hw.energy{hw.type=\"network\"}";
	public static final String HW_POWER_NETWORK_METRIC = "hw.power{hw.type=\"network\"}";
	public static final String DEVICE_TYPE = "device_type";
	// Network card vendor/model words to be trimmed
	public static final Pattern NETWORK_VENDOR_MODEL_TRIM_PATTERN = Pattern.compile(
		"network|ndis|client|server|adapter|ethernet|interface|controller|miniport|scheduler|packet|connection|multifunction|(1([0]+[/]*))*(base[\\-tx]*)*",
		Pattern.CASE_INSENSITIVE
	);
	public static final Pattern NETWORK_CARD_TRIM_PATTERN = Pattern.compile("network", Pattern.CASE_INSENSITIVE);
	public static final String NETWORK_DEVICE_TYPE = "device_type";
}
