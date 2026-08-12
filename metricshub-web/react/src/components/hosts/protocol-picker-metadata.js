import CableIcon from "@mui/icons-material/Cable";
import CodeIcon from "@mui/icons-material/Code";
import HttpIcon from "@mui/icons-material/Http";
import HubIcon from "@mui/icons-material/Hub";
import MemoryIcon from "@mui/icons-material/Memory";
import NetworkPingIcon from "@mui/icons-material/NetworkPing";
import RouterIcon from "@mui/icons-material/Router";
import SensorsIcon from "@mui/icons-material/Sensors";
import TerminalIcon from "@mui/icons-material/Terminal";
import WindowIcon from "@mui/icons-material/Window";
import DatabaseIcon from "./DatabaseIcon";

/** @type {Record<string, { Icon: import("@mui/material").SvgIconComponent; summary: string }>} */
export const PROTOCOL_PICKER_METADATA = {
	http: {
		Icon: HttpIcon,
		summary: "REST and web endpoints over HTTP or HTTPS",
	},
	ipmi: {
		Icon: SensorsIcon,
		summary: "Out-of-band hardware management (BMC)",
	},
	jdbc: {
		Icon: DatabaseIcon,
		summary: "Database metrics through JDBC",
	},
	jmx: {
		Icon: MemoryIcon,
		summary: "Java application metrics via JMX",
	},
	oscommand: {
		Icon: CodeIcon,
		summary: "Local or remote shell commands",
	},
	ping: {
		Icon: NetworkPingIcon,
		summary: "ICMP reachability checks",
	},
	snmp: {
		Icon: RouterIcon,
		summary: "SNMP v1 and v2 community polling",
	},
	snmpv3: {
		Icon: RouterIcon,
		summary: "SNMP v3 with authentication and privacy",
	},
	ssh: {
		Icon: TerminalIcon,
		summary: "Secure shell commands and file transfer",
	},
	wbem: {
		Icon: HubIcon,
		summary: "Web-Based Enterprise Management (CIM)",
	},
	winrm: {
		Icon: WindowIcon,
		summary: "Windows Remote Management over HTTP(S)",
	},
	wmi: {
		Icon: WindowIcon,
		summary: "Windows Management Instrumentation queries",
	},
};

/**
 * @param {string} protocolId
 * @returns {{ Icon: import("@mui/material").SvgIconComponent; summary: string }}
 */
export const getProtocolPickerMetadata = (protocolId) =>
	PROTOCOL_PICKER_METADATA[protocolId] || {
		Icon: CableIcon,
		summary: "Protocol configuration",
	};
