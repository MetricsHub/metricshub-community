import React from "react";
import { useTheme } from "@mui/material/styles";
import {
    IconButton,
    Menu,
    MenuItem,
    ListItemIcon,
    ListItemText,
    Tooltip,
    Divider,
    Typography,
    Box,
} from "@mui/material";
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import LogoutIcon from "@mui/icons-material/Logout";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";

/**
 * Profile menu with username, theme toggle, and logout.
 *
 * @param {Object} props
 * @param {string=} props.username - Display name for the current user.
 * @param {() => void} props.onToggleTheme - Callback to toggle the theme.
 * @param {() => void} props.onSignOut - Callback to sign out.
 * @returns {JSX.Element}
 */
const ProfileMenu = ({ username, onToggleTheme, onSignOut }) => {
    const theme = useTheme();
    const [anchorEl, setAnchorEl] = React.useState(null);
    const open = Boolean(anchorEl);

    const handleOpen = React.useCallback((event) => {
        setAnchorEl(event.currentTarget);
    }, []);
    const handleClose = React.useCallback(() => setAnchorEl(null), []);

    const handleToggleTheme = React.useCallback(() => {
        // Keep menu open when toggling theme for a faster multi-toggle UX.
        onToggleTheme?.();
    }, [onToggleTheme]);

    const handleLogout = React.useCallback(() => {
        handleClose();
        onSignOut?.();
    }, [handleClose, onSignOut]);

    const isDark = React.useMemo(() => theme.palette.mode === "dark", [theme.palette.mode]);
    const themeLabel = React.useMemo(
        () => (isDark ? "Switch to light mode" : "Switch to dark mode"),
        [isDark]
    );

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
            >
                <Box sx={{ px: 2, pt: 1, pb: 1, maxWidth: 260 }}>
                    <Typography variant="caption" color="text.secondary">
                        Signed in as
                    </Typography>
                    <Typography
                        variant="body2"
                        sx={{ fontWeight: 600, color: "text.primary" }}
                        noWrap
                        title={username || "Unknown user"}
                    >
                        {username || "Unknown user"}
                    </Typography>
                </Box>
                <Divider />

                <MenuItem onClick={handleToggleTheme}>
                    <ListItemIcon
                        sx={{ color: theme.palette.mode === "light" ? theme.palette.warning.main : undefined }}
                    >
                        {isDark ? <DarkModeIcon fontSize="small" /> : <LightModeIcon fontSize="small" />}
                    </ListItemIcon>
                    <ListItemText>{themeLabel}</ListItemText>
                </MenuItem>

                <MenuItem onClick={handleLogout}>
                    <ListItemIcon>
                        <LogoutIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>Log out</ListItemText>
                </MenuItem>
            </Menu>
        </>
    );
};

export default React.memo(ProfileMenu);
