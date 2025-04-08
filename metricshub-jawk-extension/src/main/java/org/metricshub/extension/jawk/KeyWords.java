package org.metricshub.extension.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Jawk Extension
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
import lombok.AllArgsConstructor;

/**
 * This class contains the keywords used in the Jawk extension.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KeyWords {

	// Protocol commands and requests
	public static final String EXECUTE_HTTP_REQUEST = "executeHttpRequest";
	public static final String EXECUTE_IPMI_REQUEST = "executeIpmiRequest";
	public static final String EXECUTE_SNMP_GET = "executeSnmpGet";
	public static final String EXECUTE_SNMP_TABLE = "executeSnmpTable";
	public static final String EXECUTE_WBEM_REQUEST = "executeWbemRequest";
	public static final String EXECUTE_WMI_REQUEST = "executeWmiRequest";

	// Computes
	public static final String JSON_2CSV = "json2csv";

	// General functions
	public static final String GET_VARIABLE = "getVariable";
}
