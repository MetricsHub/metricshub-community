import React from "react";
import { IconButton, Menu, Tooltip, Divider, Typography, Box } from "@mui/material";
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import ToggleTheme from "./toggle-theme";
import LogoutMenuItem from "./logout";

/**
 * Profile menu with username, theme toggle, and logout.
 *
 * @param {Object} props
 * @param {string=} props.username - Display name for the current user.
 * @param {() => void} props.onSignOut - Callback to sign out.
 * @returns {JSX.Element}
 */
const ProfileMenu = ({ username, onSignOut }) => {
	const [anchorEl, setAnchorEl] = React.useState(null);
	const open = Boolean(anchorEl);

	const handleOpen = React.useCallback((event) => {
		setAnchorEl(event.currentTarget);
	}, []);
	const handleClose = React.useCallback(() => setAnchorEl(null), []);

	const handleLogout = React.useCallback(() => {
		handleClose();
		onSignOut?.();
	}, [handleClose, onSignOut]);

	return (
		<>
			<Tooltip title="Account" arrow enterDelay={200}>
				<IconButton
					aria-label="Open profile menu"
					aria-controls={open ? "profile-menu" : undefined}
					aria-haspopup="true"
					aria-expanded={open ? "true" : undefined}
					onClick={handleOpen}
				>
					<AccountCircleIcon />
				</IconButton>
			</Tooltip>

			<Menu
				id="profile-menu"
				anchorEl={anchorEl}
				open={open}
				onClose={handleClose}
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
				MenuListProps={{ sx: { p: 0 } }}
				PaperProps={{
					sx: {
						minWidth: 200,
					},
				}}
			>
				<Box sx={{ px: 2, pt: 1, pb: 1, maxWidth: 260 }}>
					<Typography variant="caption" color="text.secondary">
						Signed in as
					</Typography>
					<Typography
						variant="body2"
						sx={{ fontWeight: 600, color: "text.primary" }}
						noWrap
						title={username}
					>
						{username}
					</Typography>
				</Box>

				<Divider />

				<LogoutMenuItem onClick={handleLogout} />
			</Menu>
		</>
	);
};

export default React.memo(ProfileMenu);
