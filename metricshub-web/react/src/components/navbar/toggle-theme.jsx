import { useTheme } from "@mui/material/styles";

import { IconButton, Tooltip } from "@mui/material";

import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";

/**
 * Toggle between light and dark theme
 *
 * @param {*} param0  onClick - function to call on click
 * @returns JSX.Element
 */
const ToggleTheme = ({ onClick }) => {
	const theme = useTheme();
	return (
		<Tooltip
			title={theme.palette.mode === "dark" ? "Switch to light mode" : "Switch to dark mode"}
			arrow
		>
			<IconButton
				aria-label="Toggle theme"
				onClick={onClick}
				sx={{
					mr: 0.5,
					color: theme.palette.mode == "light" ? theme.palette.warning.main : "default",
				}}
			>
				{theme.palette.mode === "dark" ? <DarkModeIcon /> : <LightModeIcon />}
			</IconButton>
		</Tooltip>
	);
};

export default ToggleTheme;
