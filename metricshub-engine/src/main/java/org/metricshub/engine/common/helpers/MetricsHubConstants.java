package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static org.metricshub.engine.connector.model.common.DeviceKind.AIX;
import static org.metricshub.engine.connector.model.common.DeviceKind.HPUX;
import static org.metricshub.engine.connector.model.common.DeviceKind.LINUX;
import static org.metricshub.engine.connector.model.common.DeviceKind.OOB;
import static org.metricshub.engine.connector.model.common.DeviceKind.SOLARIS;
import static org.metricshub.engine.connector.model.common.DeviceKind.TRU64;
import static org.metricshub.engine.connector.model.common.DeviceKind.VMS;
import static org.metricshub.engine.connector.model.common.DeviceKind.WINDOWS;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.metric.StateSet;

/**
 * The MetricsHubConstants class provides constants used in the MetricsHub engine.
 * It includes separators, monitor information, macros, thread and timeout settings, mappings,
 * and various other constants related to MetricsHub operations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MetricsHubConstants {

	// Table separators, tabulations, new line characters

	/**
	 * Semicolon
	 */
	public static final String SEMICOLON = ";";
	/**
	 * Separator: Comma
	 */
	public static final String COMMA = ",";
	/**
	 * Separator: Table Separator (Uses SEMICOLON)
	 */
	public static final String TABLE_SEP = SEMICOLON;
	/**
	 * Empty String
	 */
	public static final String EMPTY = "";
	/**
	 * White Space
	 */
	public static final String WHITE_SPACE = " ";
	/**
	 * New Line Character
	 */
	public static final String NEW_LINE = "\n";
	/**
	 * Tab Character
	 */
	public static final String TAB = "\t";
	/**
	 * Underscore Character
	 */
	public static final String UNDERSCORE = "_";
	/**
	 * Vertical Bar
	 */
	public static final String VERTICAL_BAR = "|";

	// Monitors informations
	/**
	 * Monitor Attribute ID
	 **/
	public static final String MONITOR_ATTRIBUTE_ID = "id";
	/**
	 * Monitor Attribute: Name
	 */
	public static final String MONITOR_ATTRIBUTE_NAME = "name";
	/**
	 * Monitor Attribute: Connector ID
	 */
	public static final String MONITOR_ATTRIBUTE_CONNECTOR_ID = "connector_id";
	/**
	 * Monitor Attribute: Applies to OS
	 */
	public static final String MONITOR_ATTRIBUTE_APPLIES_TO_OS = "applies_to_os";
	/**
	 * Is Endpoint
	 */
	public static final String IS_ENDPOINT = "is_endpoint";
	/**
	 * Monitor Attribute: Parent ID
	 */
	public static final String MONITOR_ATTRIBUTE_PARENT_ID = "parent.id";

	/**
	 * Other Monitor Job Types
	 */
	public static final String OTHER_MONITOR_JOB_TYPES = "otherMonitorJobTypes";

	/**
	 * Map of Monitor Job Types to Priorities
	 */
	// @formatter:off
	public static final Map<String, Integer> MONITOR_JOBS_PRIORITY = Map.of(
		KnownMonitorType.HOST.getKey(), 1,
		KnownMonitorType.ENCLOSURE.getKey(), 2,
		KnownMonitorType.BLADE.getKey(), 3,
		KnownMonitorType.DISK_CONTROLLER.getKey(), 4,
		KnownMonitorType.CPU.getKey(), 5,
		OTHER_MONITOR_JOB_TYPES, 6
	);
	// @formatter:on

	/**
	 * Log Compute Key Suffix Template
	 */
	public static final String LOG_COMPUTE_KEY_SUFFIX_TEMPLATE = "%s -> computes[%d]";

	/**
	 * Threads and timeout
	 **/

	/**
	 * Default Job Timeout
	 */
	public static final long DEFAULT_JOB_TIMEOUT = 5 * 60L;
	/**
	 * Default Lock timeout
	 */
	public static final int DEFAULT_LOCK_TIMEOUT = 2 * 60; // 2 minutes
	/**
	 * Max Thread Count
	 */
	public static final int MAX_THREADS_COUNT = 50;
	/**
	 * Thread Timeout
	 */
	public static final long THREAD_TIMEOUT = 2 * 60L; // 2 minutes

	// MetricsHub / OpenTelemetry mappings

	/**
	 * Storage
	 */
	public static final String STORAGE = "storage";

	/**
	 * Host Type Compute
	 */
	public static final String HOST_TYPE_COMPUTE = "compute";

	/**
	 * Network
	 */
	public static final String NETWORK = "network";

	/**
	 * Map of Host Types to OpenTelemetry Host Types
	 */
	// @formatter:off
	public static final Map<DeviceKind, String> HOST_TYPE_TO_OTEL_HOST_TYPE = Map.of(
		VMS, HOST_TYPE_COMPUTE,
		TRU64, HOST_TYPE_COMPUTE,
		HPUX, HOST_TYPE_COMPUTE,
		AIX, HOST_TYPE_COMPUTE,
		LINUX, HOST_TYPE_COMPUTE,
		OOB, HOST_TYPE_COMPUTE,
		WINDOWS, HOST_TYPE_COMPUTE,
		DeviceKind.NETWORK, NETWORK,
		DeviceKind.STORAGE, STORAGE,
		SOLARIS, HOST_TYPE_COMPUTE
	);
	// @formatter:on

	/**
	 * OpenTelemetry OS Type: HP-UX
	 */
	public static final String OTEL_HPUX_OS_TYPE = "hpux";
	/**
	 * OpenTelemetry OS Type: Tru64
	 */
	public static final String OTEL_TRUE64_OS_TYPE = "true64";
	/**
	 * OpenTelemetry OS Type: OpenVMS
	 */
	public static final String OTEL_OPENVMS_OS_TYPE = "openvms";
	/**
	 * OpenTelemetry OS Type: Network
	 */
	public static final String OTEL_NETWORK_OS_TYPE = NETWORK;
	/**
	 * OpenTelemetry OS Type: Storage
	 */
	public static final String OTEL_STORAGE_OS_TYPE = STORAGE;
	/**
	 * OpenTelemetry OS Type: Solaris
	 */
	public static final String OTEL_SOLARIS_OS_TYPE = "solaris";
	/**
	 * OpenTelemetry OS Type: Windows
	 */
	public static final String OTEL_WINDOWS_OS_TYPE = "windows";
	/**
	 * OpenTelemetry OS Type: Management
	 */
	public static final String OTEL_MANAGEMENT_OS_TYPE = "management";
	/**
	 * OpenTelemetry OS Type: Linux
	 */
	public static final String OTEL_LINUX_OS_TYPE = "linux";
	/**
	 * OpenTelemetry OS Type: AIX
	 */
	public static final String OTEL_AIX_OS_TYPE = "aix";
	/**
	 * OpenTelemetry OS Type: macOS X
	 */
	public static final String OTEL_MAC_OS_X_OS_TYPE = "macosx";
	/**
	 * OpenTelemetry OS Type: OpenBSD
	 */
	public static final String OTEL_OPEN_BSD_OS_TYPE = "openbsd";
	/**
	 * OpenTelemetry OS Type: NetBSD
	 */
	public static final String OTEL_NET_BSD_OS_TYPE = "netbsd";
	/**
	 * OpenTelemetry OS Type: FreeBSD
	 */
	public static final String OTEL_FREE_BSD_OS_TYPE = "freebsd";
	/**
	 * OpenTelemetry OS Type: Sun
	 */
	public static final String OTEL_SUN_OS_TYPE = "sun";

	/**
	 * Map of Host Types to OS Types
	 */
	// @formatter:off
	public static final Map<DeviceKind, String> HOST_TYPE_TO_OTEL_OS_TYPE = Map.of(
		VMS, OTEL_OPENVMS_OS_TYPE,
		TRU64, OTEL_TRUE64_OS_TYPE,
		HPUX, OTEL_HPUX_OS_TYPE,
		AIX, OTEL_AIX_OS_TYPE,
		LINUX, OTEL_LINUX_OS_TYPE,
		OOB, OTEL_MANAGEMENT_OS_TYPE,
		WINDOWS, OTEL_WINDOWS_OS_TYPE,
		DeviceKind.NETWORK, OTEL_NETWORK_OS_TYPE,
		DeviceKind.STORAGE, OTEL_STORAGE_OS_TYPE,
		SOLARIS, OTEL_SOLARIS_OS_TYPE
	);
	// @formatter:on

	/**
	 * Connector Status Metric Key
	 **/
	public static final String CONNECTOR_STATUS_METRIC_KEY = "metricshub.connector.status";

	/**
	 * StateSet Metric OK
	 */
	public static final String STATE_SET_METRIC_OK = "ok";

	/**
	 * StateSet Metric Failed
	 */
	public static final String STATE_SET_METRIC_FAILED = "failed";

	/**
	 * Connector Status Metric Definition
	 **/
	public static final MetricDefinition CONNECTOR_STATUS_METRIC_DEFINITION = MetricDefinition
		.builder()
		.description("Connector operational status.")
		.type(StateSet.builder().set(Set.of(STATE_SET_METRIC_OK, STATE_SET_METRIC_FAILED)).build())
		.build();

	/**
	 * LocalHost
	 **/
	public static final String LOCALHOST = "localhost";

	/**
	 * Host Name
	 */
	public static final String HOST_NAME = "host.name";

	/**
	 * Hostname Exception Message
	 */
	public static final String HOSTNAME_EXCEPTION_MESSAGE = "Hostname {} - Exception: ";

	/**
	 * Engine properties file name
	 **/
	public static final String ENGINE_PROPERTIES_FILE_NAME = "engine.properties";

	/**
	 * Engine version property
	 */
	public static final String ENGINE_VERSION_PROPERTY = "engine.version";

	/**
	 * Successful OS Detection Message
	 **/
	public static final String SUCCESSFUL_OS_DETECTION_MESSAGE = "Successful OS detection operation";

	/**
	 * WMI Process Query
	 */
	public static final String WMI_PROCESS_QUERY = "SELECT ProcessId,Name,ParentProcessId,CommandLine FROM Win32_Process";

	/**
	 * WMI Default Namespace
	 */
	public static final String WMI_DEFAULT_NAMESPACE = "root\\cimv2";

	/**
	 * Automatic Namespace
	 */
	public static final String AUTOMATIC_NAMESPACE = "automatic";

	/**
	 * Can't Find Embedded File
	 **/
	public static final String CANT_FIND_EMBEDDED_FILE = "Can't find embedded file: ";
	public static final String ZIP = "zip";
	public static final String CONNECTORS = "connectors";

	//A compiled representation of a file converter. We attempt to match input like ${file::path} // NOSONAR on comment

	/**
	 * File Pattern
	 */
	public static final Pattern FILE_PATTERN = Pattern.compile("\\$\\{file::(.*?)\\}", Pattern.CASE_INSENSITIVE);

	/**
	 * Source Reference Pattern
	 */
	public static final Pattern SOURCE_REF_PATTERN = Pattern.compile("\\$\\{source::([^\\s\\}]+)\\}");

	/**
	 * Column pattern
	 */
	public static final Pattern COLUMN_PATTERN = Pattern.compile("^\\s*\\$(\\d+)\\s*$");

	/**
	 * Column Reference pattern
	 */
	public static final Pattern COLUMN_REFERENCE_PATTERN = Pattern.compile("(?<!\\$)\\$([1-9]\\d*)");

	/**
	 * Double Pattern
	 */
	public static final Pattern DOUBLE_PATTERN = Pattern.compile("\\d+(\\.\\d+)?");

	/**
	 * Translation Reference Pattern
	 */
	public static final Pattern TRANSLATION_REF_PATTERN = Pattern.compile("\\$\\{translation::([^\\s]+)\\}");

	/**
	 * Hexadecimal Pattern
	 */
	public static final Pattern HEXA_PATTERN = Pattern.compile("^[0-9A-Fa-f]+$");

	/**
	 * Default
	 **/
	public static final String DEFAULT = "default";

	/**
	 * Default keys for monitor jobs
	 */
	public static final Set<String> DEFAULT_KEYS = Set.of(MetricsHubConstants.MONITOR_ATTRIBUTE_ID);

	/**
	 * MetricsHub multi value attribute separator
	 */
	public static final String MULTI_VALUE_SEPARATOR = "_@M8B_MULTI_VALUE_SEP@_";
}
