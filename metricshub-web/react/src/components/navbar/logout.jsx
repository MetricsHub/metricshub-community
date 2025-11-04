import React from "react";
import { MenuItem, ListItemIcon, ListItemText } from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";

/**
 * Menu item to log out the current user.
 *
 * @param {{ onClick: () => void }} props onClick - callback executed on click
 * @returns {JSX.Element}
 */
const Logout = ({ onClick }) => {
	return (
		<MenuItem onClick={onClick}>
			<ListItemIcon>
				<LogoutIcon fontSize="small" />
			</ListItemIcon>
			<ListItemText>Log out</ListItemText>
		</MenuItem>
	);
};

export default React.memo(Logout);
