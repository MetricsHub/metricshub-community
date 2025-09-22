import React from "react";
import { IconButton, Tooltip } from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";

/**
 * Logout button component
 * @param {*} param0  onClick - function to call on click
 * @returns JSX.Element
 */
const Logout = ({ onClick }) => {
	return (
		<Tooltip title="Log out" arrow enterDelay={200}>
			<IconButton aria-label="Log out" size="small" onClick={onClick} sx={{ cursor: "pointer" }}>
				<LogoutIcon fontSize="inherit" />
			</IconButton>
		</Tooltip>
	);
};

export default Logout;
