import React from "react";
import { MenuItem, ListItemIcon, ListItemText } from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";

/**
 * Menu item to log out the current user.
 *
 * @param {{ onClick: () => void }} props onClick - callback executed on click
 * @returns {JSX.Element}
 */
const LogoutMenuItem = ({ onClick }) => {
	return (
		<MenuItem onClick={onClick}>
			<ListItemIcon>
				<LogoutIcon fontSize="small" />
			</ListItemIcon>
			<ListItemText>Sign out</ListItemText>
		</MenuItem>
	);
};

export default React.memo(LogoutMenuItem);
