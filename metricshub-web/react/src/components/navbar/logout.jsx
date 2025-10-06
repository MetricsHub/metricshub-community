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
			<IconButton aria-label="Log out" onClick={onClick}>
				<LogoutIcon />
			</IconButton>
		</Tooltip>
	);
};

export default Logout;
