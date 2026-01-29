import React, { useEffect, useCallback } from "react";
import logoDark from "../../assets/logo-dark.svg";
import logoLight from "../../assets/logo-light.svg";
import { useTheme } from "@mui/material/styles";

import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";

import { NavLink } from "react-router-dom";
import {
	AppBar,
	Box,
	CssBaseline,
	Toolbar,
	Button,
	IconButton,
	Tooltip,
	Menu,
	MenuItem,
	ListItemIcon,
	ListItemText,
	useMediaQuery,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import MenuBookOutlinedIcon from "@mui/icons-material/MenuBookOutlined";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import ExploreIcon from "@mui/icons-material/Explore";
import SettingsIcon from "@mui/icons-material/Settings";
import ChatIcon from "@mui/icons-material/Chat";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";

import { useAppDispatch, useAppSelector } from "../../hooks/store";
import { fetchApplicationStatus } from "../../store/thunks/application-status-thunks";
import { selectLastVisitedPath } from "../../store/slices/explorer-slice";

import StatusText from "./status/StatusText";
import OtelStatusIcon from "./status/OtelStatusIcon";
import ProfileMenu from "./ProfileMenu";
import ToggleTheme from "./ToggleTheme";

// Refresh status every 30 seconds
const STATUS_REFRESH_MS = 30000;

const NavBar = ({ onToggleTheme }) => {
	const { signOut, user } = useAuth();
	const theme = useTheme();
	const isSmallScreen = useMediaQuery("(max-width:900px)");
	const [mobileMenuAnchor, setMobileMenuAnchor] = React.useState(null);
	const mobileMenuOpen = Boolean(mobileMenuAnchor);

	const handleMobileMenuOpen = useCallback((event) => {
		setMobileMenuAnchor(event.currentTarget);
	}, []);

	const handleMobileMenuClose = useCallback(() => {
		setMobileMenuAnchor(null);
	}, []);

	const handleSignOut = useCallback(async () => {
		await signOut();
		// AuthGuard will automatically redirect to login with the correct returnTo
	}, [signOut]);

	const dispatch = useAppDispatch();
	const lastVisitedPath = useAppSelector(selectLastVisitedPath);

	// Config dirty/error status for navbar dot indicator
	const dirtyByName = useAppSelector((s) => s.config?.dirtyByName) || {};
	const filesByName = useAppSelector((s) => s.config?.filesByName) || {};
	const hasDirty = Object.entries(dirtyByName).some(
		([name, isDirty]) => isDirty && !name.endsWith(".draft"),
	);
	const hasError = Object.entries(filesByName).some(([name, v]) => {
		if (name.endsWith(".draft")) return false;
		const val = v?.validation;
		return val && val.valid === false;
	});
	useEffect(() => {
		dispatch(fetchApplicationStatus());
		const id = setInterval(() => dispatch(fetchApplicationStatus()), STATUS_REFRESH_MS);
		return () => clearInterval(id);
	}, [dispatch]);

	const handleToggleTheme = React.useCallback(() => {
		onToggleTheme?.();
	}, [onToggleTheme]);

	const navBtnSx = {
		alignSelf: "stretch",
		height: "100%",
		minHeight: "auto",
		borderRadius: 0,
		px: 2.25,
		py: 0,
		lineHeight: 1,
		minWidth: 90,
		color: "text.primary",
		borderBottom: "2px solid transparent",
		transition: "background-color 0.4s ease, color 0.4s ease, border-color 0.4s ease",
		"&:hover": { bgcolor: "action.hover", color: "text.primary" },
		"&.active": (t) => ({
			bgcolor: t.palette.action.selected,
			borderBottomColor: t.palette.primary.main,
		}),
	};

	return (
		<>
			<CssBaseline />

			<AppBar
				position="sticky"
				elevation={1}
				sx={(t) => ({
					bgcolor: t.palette.background.default,
					color: t.palette.text.primary,
					borderBottom: 1,
					borderColor: t.palette.mode === "light" ? t.palette.neutral[400] : t.palette.divider,
					boxShadow: "none",
					transition: "background-color 0.4s ease, border-color 0.4s ease, color 0.4s ease",
				})}
			>
				<Toolbar sx={{ gap: 1.5, minHeight: 64, height: 64 }}>
					{/* ================= LEFT SIDE ================= */}
					<Box sx={{ display: "flex", gap: 2.5, height: "100%", alignItems: "stretch" }}>
						{/* Logo + Status */}
						<Box sx={{ display: "flex", alignItems: "center", gap: 2.5 }}>
							<Box
								component="a"
								href="https://metricshub.com/"
								target="_blank"
								rel="noopener noreferrer"
								aria-label="Open MetricsHub website in a new tab"
								sx={{
									display: "inline-flex",
									alignItems: "center",
									textDecoration: "none",
									position: "relative",
									width: 80,
									height: 24,
								}}
							>
								<img
									src={logoDark}
									alt="MetricsHub"
									style={{
										width: 80,
										height: "auto",
										position: "absolute",
										opacity: theme.palette.mode === "dark" ? 1 : 0,
										transition: "opacity 0.4s ease",
									}}
								/>
								<img
									src={logoLight}
									alt="MetricsHub"
									style={{
										width: 80,
										height: "auto",
										position: "absolute",
										opacity: theme.palette.mode === "light" ? 1 : 0,
										transition: "opacity 0.4s ease",
									}}
								/>
								<Box
									sx={{
										position: "absolute",
										top: -6,
										right: -18,
										bgcolor: "primary.main",
										color: "#fff",
										fontSize: "0.55rem",
										fontWeight: 800,
										px: 0.5,
										py: 0.25,
										borderRadius: 0.5,
										lineHeight: 1,
										letterSpacing: 0.5,
										boxShadow: 1,
										pointerEvents: "none",
										zIndex: 1,
									}}
								>
									BETA
								</Box>
							</Box>
							<StatusText sx={{ ml: 0.5, display: { xs: "none", md: "flex" } }} />
							<OtelStatusIcon />
						</Box>

						{/* Navigation Buttons - Desktop */}
						{!isSmallScreen && (
							<Box sx={{ display: "flex", gap: 0, ml: 1, alignSelf: "stretch" }}>
								<Button
									component={NavLink}
									size="large"
									to={lastVisitedPath || paths.explorer}
									sx={navBtnSx}
								>
									Explorer
								</Button>
								<Button component={NavLink} size="large" to={paths.configuration} sx={navBtnSx}>
									<Box sx={{ display: "inline-flex", alignItems: "center", gap: 0.75 }}>
										<span>Configuration</span>
										{(hasDirty || hasError) && (
											<Box
												component="span"
												aria-label={
													hasError ? "Configuration has errors" : "Unsaved configuration changes"
												}
												title={
													hasError ? "Configuration has errors" : "Unsaved configuration changes"
												}
												sx={{
													ml: 0.25,
													width: 8,
													height: 8,
													borderRadius: "50%",
													bgcolor: (t) =>
														hasError ? t.palette.error.main : t.palette.warning.main,
													flexShrink: 0,
												}}
											/>
										)}
									</Box>
								</Button>
								<Button component={NavLink} size="large" to={paths.chat} sx={navBtnSx}>
									<Box
										sx={{ display: "inline-flex", alignItems: "baseline", position: "relative" }}
									>
										<span>M8B</span>
										<AutoAwesomeIcon
											sx={{
												fontSize: "0.8rem",
												color: "#FFD700",
												filter: "drop-shadow(0 1px 1px rgba(0,0,0,0.2))",
												position: "relative",
												top: "-0.5em",
												ml: 0.25,
												lineHeight: 0,
											}}
										/>
									</Box>
								</Button>
							</Box>
						)}

					</Box>

					{/* ================= RIGHT SIDE ================= */}
					<Box sx={{ display: "flex", alignItems: "center", gap: 1, ml: "auto" }}>
						{/* Docs link button - Desktop only */}
						{!isSmallScreen && (
							<Tooltip title="Documentation" arrow enterDelay={200}>
								<IconButton
									component="a"
									href="https://metricshub.com/docs/latest/"
									target="_blank"
									rel="noopener noreferrer"
									aria-label="Open MetricsHub documentation in a new tab"
								>
									<MenuBookOutlinedIcon />
								</IconButton>
							</Tooltip>
						)}
						{/* Theme toggle - Desktop only */}
						{!isSmallScreen && <ToggleTheme onClick={handleToggleTheme} />}
						{/* Mobile menu with all options */}
						{isSmallScreen && (
							<>
								<ProfileMenu username={user?.username} onSignOut={handleSignOut} />
								<IconButton
									aria-label="Menu"
									aria-controls="mobile-menu"
									aria-haspopup="true"
									onClick={handleMobileMenuOpen}
								>
									<MenuIcon />
								</IconButton>
								<Menu
									id="mobile-menu"
									anchorEl={mobileMenuAnchor}
									open={mobileMenuOpen}
									onClose={handleMobileMenuClose}
									anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
									transformOrigin={{ vertical: "top", horizontal: "right" }}
								>
									<MenuItem
										component={NavLink}
										to={lastVisitedPath || paths.explorer}
										onClick={handleMobileMenuClose}
									>
										<ListItemIcon>
											<ExploreIcon fontSize="small" />
										</ListItemIcon>
										<ListItemText>Explorer</ListItemText>
									</MenuItem>
									<MenuItem
										component={NavLink}
										to={paths.configuration}
										onClick={handleMobileMenuClose}
									>
										<ListItemIcon>
											<SettingsIcon fontSize="small" />
											{(hasDirty || hasError) && (
												<Box
													component="span"
													sx={{
														position: "absolute",
														top: 8,
														left: 28,
														width: 8,
														height: 8,
														borderRadius: "50%",
														bgcolor: (t) =>
															hasError ? t.palette.error.main : t.palette.warning.main,
													}}
												/>
											)}
										</ListItemIcon>
										<ListItemText>Configuration</ListItemText>
									</MenuItem>
									<MenuItem component={NavLink} to={paths.chat} onClick={handleMobileMenuClose}>
										<ListItemIcon>
											<ChatIcon fontSize="small" />
										</ListItemIcon>
										<ListItemText>
											M8B{" "}
											<AutoAwesomeIcon
												sx={{
													fontSize: "0.7rem",
													color: "#FFD700",
													ml: 0.5,
												}}
											/>
										</ListItemText>
									</MenuItem>
									<MenuItem
										component="a"
										href="https://metricshub.com/docs/latest/"
										target="_blank"
										rel="noopener noreferrer"
										onClick={handleMobileMenuClose}
									>
										<ListItemIcon>
											<MenuBookOutlinedIcon fontSize="small" />
										</ListItemIcon>
										<ListItemText>Documentation</ListItemText>
									</MenuItem>
									<MenuItem
										onClick={() => {
											handleToggleTheme();
											handleMobileMenuClose();
										}}
									>
										<ListItemIcon>
											{theme.palette.mode === "dark" ? (
												<LightModeIcon fontSize="small" />
											) : (
												<DarkModeIcon fontSize="small" />
											)}
										</ListItemIcon>
										<ListItemText>
											{theme.palette.mode === "dark" ? "Light Mode" : "Dark Mode"}
										</ListItemText>
									</MenuItem>
								</Menu>
							</>
						)}

					</Box>
				</Toolbar>
			</AppBar>
		</>
	);
};

export default NavBar;
