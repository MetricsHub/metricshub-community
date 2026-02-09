import * as React from "react";
import { Box } from "@mui/material";
import PropTypes from "prop-types";

/**
 * Memory icon component
 */
export const MemoryIcon = ({ sx = {} }) => (
	<Box
		component="svg"
		sx={{ width: 24, height: 24, ...sx }}
		viewBox="0 0 24 24"
		fill="currentColor"
	>
		<path d="M15,9H9V15H15M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 12,2M12,4A8,8 0 0,0 4,12A8,8 0 0,0 12,20A8,8 0 0,0 20,12A8,8 0 0,0 12,4Z" />
	</Box>
);

/**
 * CPU icon component
 */
export const CpuIcon = ({ sx = {} }) => (
	<Box
		component="svg"
		sx={{ width: 24, height: 24, ...sx }}
		viewBox="0 0 24 24"
		fill="currentColor"
	>
		<path d="M17,17H7V7H17M21,11V9H19V7C19,5.89 18.1,5 17,5H15V3H13V5H11V3H9V5H7C5.89,5 5,5.89 5,7V9H3V11H5V13H3V15H5V17A2,2 0 0,0 7,19H9V21H11V19H13V21H15V19H17A2,2 0 0,0 19,17V15H21V13H19V11M13,13H11V11H13V13Z" />
	</Box>
);

/**
 * Monitor/Dashboard icon component
 */
export const MonitorIcon = ({ sx = {} }) => (
	<Box
		component="svg"
		sx={{ width: 24, height: 24, ...sx }}
		viewBox="0 0 24 24"
		fill="currentColor"
	>
		<path d="M21,16H3V4H21M21,2H3C1.89,2 1,2.89 1,4V16A2,2 0 0,0 3,18H10V20H8V22H16V20H14V18H21A2,2 0 0,0 23,16V4C23,2.89 22.1,2 21,2Z" />
	</Box>
);

/**
 * Resources/Devices icon component
 */
export const ResourcesIcon = ({ sx = {} }) => (
	<Box
		component="svg"
		sx={{ width: 24, height: 24, ...sx }}
		viewBox="0 0 24 24"
		fill="currentColor"
	>
		<path d="M4,6H20V16H4M20,18A2,2 0 0,0 22,16V6C22,4.89 21.1,4 20,4H4C2.89,4 2,4.89 2,6V16A2,2 0 0,0 4,18H0V20H24V18H20Z" />
	</Box>
);

MemoryIcon.propTypes = {
	sx: PropTypes.object,
};

CpuIcon.propTypes = {
	sx: PropTypes.object,
};

MonitorIcon.propTypes = {
	sx: PropTypes.object,
};

ResourcesIcon.propTypes = {
	sx: PropTypes.object,
};
