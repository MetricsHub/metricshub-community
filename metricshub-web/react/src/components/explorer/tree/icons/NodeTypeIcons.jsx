import * as React from "react";
import { Box } from "@mui/material";
import FolderOutlinedIcon from "@mui/icons-material/FolderOutlined";
import FolderSpecialOutlinedIcon from "@mui/icons-material/FolderSpecialOutlined";
import DeviceHubIcon from "@mui/icons-material/DeviceHub";
import StorageOutlinedIcon from "@mui/icons-material/StorageOutlined";
import TtyOutlinedIcon from "@mui/icons-material/TtyOutlined";
import LanOutlinedIcon from "@mui/icons-material/LanOutlined";
import InsertDriveFileOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import ComputerOutlinedIcon from "@mui/icons-material/ComputerOutlined";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";

/**
 * Mapping of explorer node types to Material UI icons.
 * Fallback defaults to a generic file/document icon.
 */
const ICONS = {
	folder: FolderOutlinedIcon,
	group: FolderOutlinedIcon,
	service: DeviceHubIcon,
	database: StorageOutlinedIcon,
	instance: TtyOutlinedIcon,
	network: LanOutlinedIcon,
	agent: DeviceHubIcon,
	"resource-group": FolderSpecialOutlinedIcon,
	resource: DnsOutlinedIcon,
	leaf: InsertDriveFileOutlinedIcon,
};

// Soft color accents to help differentiate similar shapes
const COLOR_GETTERS = {
	agent: (t) => t.palette.primary.main,
	"resource-group": (t) => t.palette.warning.main,
	resource: (t) => t.palette.info.main,
	folder: (t) => t.palette.text.secondary,
	group: (t) => t.palette.text.secondary,
	leaf: (t) => t.palette.text.secondary,
};

/**
 * Icon component for explorer node types.
 * @param {{ type?: string, isFolder?: boolean }} props
 */
const NodeTypeIconsComponent = ({ type, isFolder = false }) => {
	const key = isFolder ? "folder" : type || "leaf";
	const IconEl = ICONS[key] || ICONS.leaf;
	const getColor = COLOR_GETTERS[key] || ((t) => t.palette.text.secondary);
	return (
		<Box
			component="span"
			sx={{ display: "inline-flex", alignItems: "center", mr: 1, color: (t) => getColor(t) }}
		>
			<IconEl fontSize="small" />
		</Box>
	);
};

// Memoize to prevent re-renders when parent tree updates.
const NodeTypeIcons = React.memo(NodeTypeIconsComponent);

export default NodeTypeIcons;
