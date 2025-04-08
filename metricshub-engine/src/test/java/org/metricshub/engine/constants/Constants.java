package org.metricshub.engine.constants;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.metricshub.engine.common.helpers.StringHelper;

public class Constants {

	// Global constants
	public static final Long STRATEGY_TIMEOUT = 100L;
	public static final Long STRATEGY_TIME = 100L;

	public static final Long RETRY_DELAY = 30L;
	public static final String DETECTION_FOLDER = "src/test/resources/test-files/connector/detection";
	public static final String MY_CONNECTOR_1_NAME = "myConnector1";
	public static final String NO_TEST_WILL_BE_PERFORMED_MESSAGE = "Process presence check: No test will be performed.";
	public static final String NO_TEST_WILL_BE_PERFORMED_UNKNOWN_OS_MESSAGE =
		"Process presence check: OS unknown, no test will be performed.";
	public static final String NO_TEST_WILL_BE_PERFORMED_AIX_MESSAGE =
		"Process presence check: No tests will be performed for OS: aix.";
	public static final String NO_TEST_WILL_BE_PERFORMED_REMOTELY_MESSAGE =
		"Process presence check: No test will be performed remotely.";
	public static final String PROCESS_CRITERION_COMMAND_LINE = "MBM[5-9]\\.exe";
	public static final String WBEM_QUERY = "SELECT ProcessId,Name,ParentProcessId,CommandLine FROM Win32_Process";
	public static final String WEBM_CRITERION_SUCCESS_EXPECTED_RESULT = "^Some Res[aeiouy]lt";
	public static final String RUNNING_PROCESS_MATCH_REGEX_MESSAGE =
		"One or more currently running processes match the following regular expression:\n- " +
		"Regexp (should match with the command-line): MBM[5-9]\\.exe";
	public static final String NO_RUNNING_PROCESS_MATCH_REGEX_MESSAGE =
		"No currently running processes match the following regular expression:\n" +
		"- Regexp (should match with the command-line): MBM[5-9]\\.exe\n" +
		"- Currently running process list:\n" +
		"1;ps;root;0;ps -A -o pid,comm,ruser,ppid,args\n" +
		"10564;eclipse.exe;user;11068;\"C:\\Users\\huan\\eclipse\\eclipse.exe\"";

	public static final List<List<String>> EXECUTE_WMI_RESULT = List.of(
		List.of("0", "System Idle Process", "0", ""),
		List.of("2", "MBM6.exe", "0", "MBM6.exe arg1 arg2"),
		List.of("10564", "eclipse.exe", "11068", "\"C:\\Users\\huan\\eclipse\\eclipse.exe\"")
	);
	public static final List<List<String>> LIST_ALL_LINUX_PROCESSES_RESULT = List.of(
		List.of("1", "ps", "root", "0", "ps -A -o pid,comm,ruser,ppid,args"),
		List.of("10564", "eclipse.exe", "user", "11068", "\"C:\\Users\\huan\\eclipse\\eclipse.exe\"")
	);

	public static final String EMPTY = "";
	public static final String SINGLE_SPACE = " ";
	public static final String AUTOMATIC = "Automatic";
	public static final String ID = "id";
	public static final String ECS1_01 = "ecs1-01";
	public static final String VALUE_VAL1 = "val1";
	public static final String VALUE_VAL2 = "val2";
	public static final String VALUE_VAL3 = "val3";
	public static final String VALUE_A1 = "a1";
	public static final String VALUE_B1 = "b1";
	public static final String VALUE_C1 = "c1";
	public static final String DOLLAR_3 = "$3";
	public static final String DOLLAR_4 = "$4";
	public static final String URL = "my/url";
	public static final String ENCLOSURE_COLLECT_SOURCE_1 = "${source::monitors.enclosure.collect.sources.source1}";
	public static final String EXPECTED_VAL_1 = "expectedVal1";
	public static final String EXPECTED_VAL_2 = "expectedVal2";
	public static final String EXPECTED_VAL_1_AND_2 = "expectedVal1\nexpectedVal2";
	public static final String EXPECTED_VAL_1_AND_2_ARRAY = "[expectedVal1,\nexpectedVal2]";
	public static final String EXPECTED_RESULT =
		"[{\n" +
		"\"Entry\":{\n" +
		"\"Full\":\"val1,val2,val3\",\n" +
		"\"Column(1)\":\"val1\",\n" +
		"\"Column(2)\":\"val2\",\n" +
		"\"Column(3)\":\"val3\",\n" +
		"\"Value\":expectedVal1\n" +
		"}\n" +
		"},\n" +
		"{\n" +
		"\"Entry\":{\n" +
		"\"Full\":\"a1,b1,c1\",\n" +
		"\"Column(1)\":\"a1\",\n" +
		"\"Column(2)\":\"b1\",\n" +
		"\"Column(3)\":\"c1\",\n" +
		"\"Value\":expectedVal2\n" +
		"}\n" +
		"}]";
	public static final String OID = "1.3.6.1.4.1.674.10893.1.20";
	public static final List<List<String>> EXPECTED_SNMP_TABLE_DATA = Arrays.asList(
		Arrays.asList("1", "PowerEdge R630", "FSJR3N2", "34377965102")
	);
	public static final String SNMP_SELECTED_COLUMNS = "ID, 9, 11, 49";
	public static final List<String> SNMP_SELECTED_COLUMNS_LIST = Arrays.asList("ID", "9", "11", "49");
	public static final String TAB1_REF = "${source::monitors.cpu.discovery.sources.tab1}";
	public static final String TABLE_SEP = ";";

	public static final String MONITOR_ID_ATTRIBUTE_VALUE = "anyMonitorId";

	public static final String UNKNOWN = "unknown";
	public static final String AGENT_HOSTNAME_VALUE = StringHelper.getValue(
		() -> InetAddress.getLocalHost().getCanonicalHostName(),
		UNKNOWN
	);
	public static final String LOCATION = "location";
	public static final String COMPUTE = "compute";
	public static final String HOST_ID_ATTRIBUTE = "host.id";
	public static final String HOST_TYPE = "host.type";
	public static final String OS_TYPE = "os.type";
	public static final String HOST_NAME = "host.name";
	public static final String[] STATE_SET = { "ok", "degraded", "failed" };
	public static final String HARDCODED_SOURCE = "Hardcoded Source";
	public static final String DISK_CONTROLLER = "disk_controller";
	public static final String PHYSICAL_DISK = "physical_disk";
	public static final String CONNECTOR = "connector";
	public static final String ENCLOSURE = "enclosure";
	public static final String DISCOVERY_MAPPING_NAME = "name";
	public static final String DISCOVERY_MAPPING_VENDOR = "vendor";
	public static final String DISCOVERY_MAPPING_MODEL = "model";

	// Yaml test files
	public static final String AAC_CONNECTOR_ID = "AAC";
	public static final String AAC_CONNECTOR_NAME = "Adaptec Storage Manager Web Edition (AAC)";
	public static final String TEST_CONNECTOR_ID = "TestConnector";

	// Host information
	public static final String LOCALHOST = "localhost";
	public static final String HOST_ID = "PC-120";
	public static final String MANAGEMENT_CARD_HOST = "management-card-host";
	public static final String HOST = "host";
	public static final String HOSTNAME = "hostname";
	public static final String HOST_CAMEL_CASE = "Host";

	// IPMI message
	public static final String IPMI_CONNECTION_SUCCESS_WITH_IMPI_OVER_LAN_MESSAGE =
		"Successfully connected to the IPMI BMC chip with the IPMI-over-LAN " + "interface.";

	// Exception messages
	public static final String SYSTEM_POWER_UP_MESSAGE = "System power state is up";
	public static final String INVALID_PROTOCOL_EXCEPTION_MESSAGE = "Invalid protocol value: ";

	// Protocols
	public static final String INVALID_PROTOCOL = "SFTPST";
	public static final String HTTP = "HTTP";
	public static final String HTTPS = "HTTPS";
	public static final String SNMP_TABLE = "snmpTable";

	public static final String DUPLICATE_COLUMN = "duplicateColumn";
	public static final String TRANSLATE = "translate";
	public static final String SNMP_CRITERION_TYPE = "snmpGetNext";
	public static final String HW_PARENT_TYPE = "hw.parent.type";
	public static final String DISK_CONTROLLER_AWK_COMMAND = "${awk::sprintf(\"Disk Controller: %s (%s)\", $2, $3)}";
	public static final String PHYSICAL_DISK_AWK_COMMAND =
		"${awk::sprintf(\"%s (%s - %s)\", $1, $4, bytes2HumanFormatBase10($6))}";
	public static final String SOURCE = "source(1)";

	// OS
	public static final String LINUX = "LINUX";
	public static final String RESULT = "result";
	public static final String TEST = "test";
	public static final String TEST_BODY = "test_body";
	public static final String ERROR = "error";
	public static final String SUCCESSFUL_OS_DETECTION = "Successful OS detection operation";
	public static final String FAILED_OS_DETECTION = "Failed OS detection operation";
	public static final String CONFIGURED_OS_NT_MESSAGE = "Configured OS type : NETWORK";
	public static final String CONFIGURED_OS_SOLARIS_MESSAGE = "Configured OS type : SOLARIS";

	// Version Number
	public static final String LOW_VERSION_NUMBER = "0.0.1";
	public static final String HIGH_VERSION_NUMBER = "1000.0.1";

	public static final String HEALTHY = "healthy";
	public static final String STATUS_INFORMATION = "StatusInformation";
}
