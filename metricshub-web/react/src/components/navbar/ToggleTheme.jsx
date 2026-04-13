import { useTheme } from "@mui/material/styles";
import { IconButton, Tooltip } from "@mui/material";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";
import React from "react";

/**
 * Toggle between light and dark theme
 *
 * @param {{ onClick: () => void }} props onClick - callback executed on click
 * @returns {JSX.Element}
 */
const ToggleTheme = ({ onClick }) => {
	const theme = useTheme();
	const isDark = theme.palette.mode === "dark";

	const tooltipTitle = isDark ? "Switch to light mode" : "Switch to dark mode";
	const iconColor = isDark ? "default" : theme.palette.warning.main;
	const Icon = isDark ? DarkModeIcon : LightModeIcon;

	return (
		<Tooltip title={tooltipTitle} arrow>
			<IconButton aria-label="Toggle theme" onClick={onClick} sx={{ color: iconColor }}>
				<Icon />
			</IconButton>
		</Tooltip>
	);
};

export default React.memo(ToggleTheme);
