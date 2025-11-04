import React from "react";
import { useTheme } from "@mui/material/styles";
import { MenuItem, ListItemIcon, ListItemText } from "@mui/material";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";

/**
 * Menu item to toggle between light and dark theme
 *
 * @param {{ onClick: () => void }} props onClick - callback executed on click
 * @returns {JSX.Element}
 */
const ToggleTheme = ({ onClick }) => {
	const theme = useTheme();
	const isDark = theme.palette.mode === "dark";
	const themeLabel = isDark ? "Switch to light mode" : "Switch to dark mode";

	return (
		<MenuItem onClick={onClick}>
			<ListItemIcon
				sx={{ color: theme.palette.mode === "light" ? theme.palette.warning.main : undefined }}
			>
				{isDark ? <DarkModeIcon fontSize="small" /> : <LightModeIcon fontSize="small" />}
			</ListItemIcon>
			<ListItemText>{themeLabel}</ListItemText>
		</MenuItem>
	);
};

export default React.memo(ToggleTheme);
