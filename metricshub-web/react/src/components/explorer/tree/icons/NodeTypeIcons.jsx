import * as React from "react";
import { Box } from "@mui/material";
import DeviceHubIcon from "@mui/icons-material/DeviceHub";
import FolderSpecialOutlinedIcon from "@mui/icons-material/FolderSpecialOutlined";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";

const ICONS = {
	agent: DeviceHubIcon,
	"resource-group": FolderSpecialOutlinedIcon,
	resource: DnsOutlinedIcon,
};

const COLOR_GETTERS = {
	// agent renders full-color logo; do not force a color
	agent: null,
	// folders should be neutral/grey, not warning/orange
	"resource-group": (t) => t.palette.text.secondary,
	resource: (t) => t.palette.info.main,
};

/**
 * Icon component for explorer node types.
 * Accepts only: agent | resource-group | resource.
 * Unknown types fall back to resource icon & neutral color.
 */
const NodeTypeIconsComponent = ({ type }) => {
	const key = ICONS[type] ? type : "resource"; // fallback
	const IconEl = ICONS[key];
	const getColor = COLOR_GETTERS[key];
	const colorSx =
		key === "agent" ? undefined : (t) => (getColor ? getColor(t) : t.palette.text.secondary);

	return (
		<Box
			component="span"
			sx={{
				display: "inline-flex",
				alignItems: "center",
				mr: 1,
				...(colorSx ? { color: colorSx } : {}),
			}}
		>
			{key === "agent" ? (
				<Box
					component="img"
					src="/favicon-32x32.png"
					alt="MetricsHub Agent"
					sx={{ width: 18, height: 18, display: "block" }}
				/>
			) : (
				<IconEl fontSize="small" />
			)}
		</Box>
	);
};

// Memoize to prevent re-renders when parent tree updates.
const NodeTypeIcons = React.memo(NodeTypeIconsComponent);

export default NodeTypeIcons;
