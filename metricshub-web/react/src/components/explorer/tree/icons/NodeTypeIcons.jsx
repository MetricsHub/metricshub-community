import * as React from "react";
import { Box } from "@mui/material";
import DeviceHubIcon from "@mui/icons-material/DeviceHub";
import DomainIcon from "@mui/icons-material/Domain";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import MonitorTypeIcon from "../../views/monitors/icons/MonitorTypeIcon";

const ICONS = {
	agent: DeviceHubIcon,
	"resource-group": DomainIcon,
	resource: DnsOutlinedIcon,
};

const COLOR_GETTERS = {
	// agent renders full-color logo; do not force a color
	agent: null,
	// folders should be neutral/grey, not warning/orange
	"resource-group": (t) => t.palette.text.secondary,
	resource: (t) => t.palette.info.main,
	"monitor-type": (t) => t.palette.warning.main,
	instance: (t) => t.palette.success.main,
};

/**
 * Icon component for explorer node types.
 * Accepts only: agent | resource-group | resource.
 * Unknown types fall back to resource icon & neutral color.
 *
 * @param {object} props - Component props
 * @param {string} props.type - The type of node (agent, resource-group, resource, monitor-type)
 * @param {string} [props.name] - The specific name of the node (e.g. for monitor types)
 * @param {"small" | "medium" | "large"} [props.fontSize="small"] - The size of the icon
 */
const NodeTypeIconsComponent = ({ type, name, fontSize = "small" }) => {
	const key = ICONS[type] ? type : "resource"; // fallback
	const IconEl = ICONS[key];
	const getColor = COLOR_GETTERS[key];
	const colorSx =
		key === "agent" ? undefined : (t) => (getColor ? getColor(t) : t.palette.text.secondary);

	// Map fontSize to pixel size for the image (agent icon)
	let imgSize = 18;
	if (fontSize === "medium") imgSize = 24;
	if (fontSize === "large") imgSize = 32;

	return (
		<Box
			component="span"
			sx={{
				display: "inline-flex",
				alignItems: "center",
				transition: "color 0.4s ease",
				...(colorSx ? { color: colorSx } : {}),
			}}
		>
			{key === "agent" ? (
				<Box
					component="img"
					src="/favicon-32x32.png"
					alt="MetricsHub Agent"
					sx={{ width: imgSize, height: imgSize, display: "block" }}
				/>
			) : (
				<MonitorTypeIcon
					type={name || type}
					fontSize={fontSize}
					fallback={<IconEl fontSize={fontSize} />}
				/>
			)}
		</Box>
	);
};

// Memoize to prevent re-renders when parent tree updates.
const NodeTypeIcons = React.memo(NodeTypeIconsComponent);

export default NodeTypeIcons;
