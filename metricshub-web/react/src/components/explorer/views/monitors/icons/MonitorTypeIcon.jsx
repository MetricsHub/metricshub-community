import * as React from "react";
import DeveloperBoardIcon from "@mui/icons-material/DeveloperBoard";
import FolderIcon from "@mui/icons-material/Folder";
import MemoryIcon from "@mui/icons-material/Memory";
import LanIcon from "@mui/icons-material/Lan";
import SwapHorizIcon from "@mui/icons-material/SwapHoriz";
import SwapVertIcon from "@mui/icons-material/SwapVert";
import StorageIcon from "@mui/icons-material/Storage";
import ComputerIcon from "@mui/icons-material/Computer";
import WindPowerIcon from "@mui/icons-material/WindPower";
import SettingsInputComponentIcon from "@mui/icons-material/SettingsInputComponent";
import PieChartIcon from "@mui/icons-material/PieChart";
import DeviceThermostatIcon from "@mui/icons-material/DeviceThermostat";
import BoltIcon from "@mui/icons-material/Bolt";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";

const ICONS = {
	CPU: DeveloperBoardIcon,
	"File System": FolderIcon,
	Filesystem: FolderIcon,
	Memory: MemoryIcon,
	Network: LanIcon,
	Paging: SwapHorizIcon,
	"Paging Activity": SwapVertIcon,
	"Physical Disk": StorageIcon,
	System: ComputerIcon,
	Fan: WindPowerIcon,
	GPU: DeveloperBoardIcon,
	Temperature: DeviceThermostatIcon,
	Voltage: BoltIcon,
	"Disk Controller": SettingsInputComponentIcon,
	"Logical Disk": PieChartIcon,
	Enclosure: StorageIcon,
};

/**
 * Icon component for monitor types.
 *
 * @param {{ type: string, fontSize?: 'small'|'medium'|'large', fallback?: React.ReactNode }} props
 */
const MonitorTypeIcon = ({ type, fontSize = "small", fallback = null }) => {
	if (!type) return null;

	const normalizedType = type.toLowerCase();

	// Try to find a match in our icon map
	let IconEl = null;

	// Exact match (case-insensitive)
	const exactKey = Object.keys(ICONS).find((k) => k.toLowerCase() === normalizedType);
	if (exactKey) {
		IconEl = ICONS[exactKey];
	} else {
		// Partial match
		if (normalizedType.includes("cpu") || normalizedType.includes("processor")) {
			IconEl = DeveloperBoardIcon;
		} else if (normalizedType.includes("memory") || normalizedType.includes("ram")) {
			IconEl = MemoryIcon;
		} else if (normalizedType.includes("network")) {
			IconEl = LanIcon;
		} else if (
			normalizedType.includes("disk") ||
			normalizedType.includes("storage") ||
			normalizedType.includes("file") ||
			normalizedType.includes("volume")
		) {
			IconEl = StorageIcon;
		} else if (normalizedType.includes("system")) {
			IconEl = ComputerIcon;
		} else if (normalizedType.includes("fan") || normalizedType.includes("cooling")) {
			IconEl = WindPowerIcon;
		} else if (normalizedType.includes("temp")) {
			IconEl = DeviceThermostatIcon;
		} else if (normalizedType.includes("volt") || normalizedType.includes("power")) {
			IconEl = BoltIcon;
		} else if (normalizedType.includes("page") || normalizedType.includes("swap")) {
			IconEl = SwapHorizIcon;
		}
	}

	if (!IconEl) {
		return fallback;
	}

	return <IconEl fontSize={fontSize} sx={{ color: "inherit" }} />;
};

export default React.memo(MonitorTypeIcon);
