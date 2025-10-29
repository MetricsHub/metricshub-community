import React, { useEffect, useCallback } from "react";
import logoDark from "../../assets/logo-dark.svg";
import logoLight from "../../assets/logo-light.svg";
import { useTheme } from "@mui/material/styles";

import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";

import { useNavigate, NavLink } from "react-router-dom";
import { AppBar, Box, CssBaseline, Toolbar, Typography, Button } from "@mui/material";

import { useAppDispatch, useAppSelector } from "../../hooks/store";
import { fetchApplicationStatus } from "../../store/thunks/applicationStatusThunks";

import StatusText from "./status/status-text";
import StatusDetailsMenu from "./status/status-details-menu";
import OtelStatusIcon from "./status/otel-status-icon";
import Logout from "./logout";
import ToggleTheme from "./toggle-theme";

// Refresh status every 30 seconds
const STATUS_REFRESH_MS = 30000;

const NavBar = ({ toggleTheme }) => {
	const navigate = useNavigate();
	const { signOut, user } = useAuth();
	const theme = useTheme();
	const metricshubLogo = theme.palette.mode === "dark" ? logoDark : logoLight;

	const handleSignOut = useCallback(async () => {
		try {
			await signOut();
		} finally {
			navigate(paths.login, { replace: true });
		}
	}, [signOut, navigate]);

	const dispatch = useAppDispatch();

	// Config dirty/error status for navbar dot indicator
	const dirtyByName = useAppSelector((s) => s.config?.dirtyByName) || {};
	const filesByName = useAppSelector((s) => s.config?.filesByName) || {};
	const hasDirty = Object.values(dirtyByName).some(Boolean);
	const hasError = Object.values(filesByName).some((v) => {
		const val = v?.validation;
		return val && val.valid === false;
	});
	useEffect(() => {
		dispatch(fetchApplicationStatus());
		const id = setInterval(() => dispatch(fetchApplicationStatus()), STATUS_REFRESH_MS);
		return () => clearInterval(id);
	}, [dispatch]);

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
					borderBottom: `1px solid ${t.palette.divider}`,
					boxShadow: "none",
				})}
			>
				<Toolbar sx={{ gap: 1.5 }}>
					{/* ================= LEFT SIDE ================= */}
					<Box sx={{ display: "flex", gap: 2.5, height: "100%" }}>
						{/* Logo + Status */}
						<Box sx={{ display: "flex", alignItems: "center", gap: 2.5 }}>
							<Box
								component="a"
								href="https://metricshub.com/"
								target="_blank"
								rel="noopener noreferrer"
								aria-label="Open MetricsHub website in a new tab"
								sx={{ display: "inline-flex", alignItems: "center", textDecoration: "none" }}
							>
								<img src={metricshubLogo} alt="MetricsHub" style={{ width: 80, height: "auto" }} />
							</Box>
							<StatusText sx={{ ml: 0.5 }} />
							<OtelStatusIcon />
						</Box>

						{/* Navigation Buttons */}
						<Box sx={{ display: "flex", gap: 0, ml: 1, alignSelf: "stretch" }}>
							<Button component={NavLink} size="large" to={paths.explorer} sx={navBtnSx}>
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
												bgcolor: (t) => (hasError ? t.palette.error.main : t.palette.warning.main),
												flexShrink: 0,
											}}
										/>
									)}
								</Box>
							</Button>
						</Box>
					</Box>

					{/* ================= RIGHT SIDE ================= */}
					<Box sx={{ display: "flex", alignItems: "center", gap: 1.5, ml: "auto" }}>
						{user && (
							<Typography variant="body1" sx={{ mr: 1 }}>
								{`Signed in as ${user.username}`}
							</Typography>
						)}
						<StatusDetailsMenu />
						<Logout onClick={handleSignOut} />
						<ToggleTheme onClick={toggleTheme} />
					</Box>
				</Toolbar>
			</AppBar>
		</>
	);
};

export default NavBar;
