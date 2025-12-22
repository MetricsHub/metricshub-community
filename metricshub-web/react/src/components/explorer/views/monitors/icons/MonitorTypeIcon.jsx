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
    "CPU": DeveloperBoardIcon,
    "File System": FolderIcon,
    "Filesystem": FolderIcon,
    "Memory": MemoryIcon,
    "Network": LanIcon,
    "Paging": SwapHorizIcon,
    "Paging Activity": SwapVertIcon,
    "Physical Disk": StorageIcon,
    "System": ComputerIcon,
    "Fan": WindPowerIcon,
    "GPU": DeveloperBoardIcon,
    "Temperature": DeviceThermostatIcon,
    "Voltage": BoltIcon,
    "Disk Controller": SettingsInputComponentIcon,
    "Logical Disk": PieChartIcon,
};

/**
 * Icon component for monitor types.
 *
 * @param {{ type: string, fontSize?: 'small'|'medium'|'large' }} props
 */
const MonitorTypeIcon = ({ type, fontSize = "small" }) => {
    const IconEl = ICONS[type] || HelpOutlineIcon;

    // If the type is not in our map, we might want to return null or a default icon.
    // For now, let's return null if not found to avoid cluttering unknown types, 
    // or use a default if requested. 
    // The user asked for icons for "all these", implying specific ones.
    if (!ICONS[type]) {
        return null;
    }

    return <IconEl fontSize={fontSize} sx={{ color: "text.secondary" }} />;
};

export default React.memo(MonitorTypeIcon);
