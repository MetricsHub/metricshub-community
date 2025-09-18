import React from "react";

import { useEffect, useCallback } from "react";
import logoDark from "../../assets/logo-dark.svg";
import logoLight from "../../assets/logo-light.svg";
import { useTheme } from "@mui/material/styles";

import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";

import { useNavigate } from "react-router-dom";
import { AppBar, Box, IconButton, Tooltip, CssBaseline, Toolbar, Typography } from "@mui/material";

import { useAppDispatch } from "../../hooks/store";
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
			navigate(paths?.auth?.login ?? "/login", { replace: true });
		}
	}, [signOut, navigate]);

	const dispatch = useAppDispatch();
	useEffect(() => {
		dispatch(fetchApplicationStatus());
		const id = setInterval(() => dispatch(fetchApplicationStatus()), STATUS_REFRESH_MS);
		return () => clearInterval(id);
	}, [dispatch]);

	return (
		<>
			<CssBaseline />

			<AppBar
				position="sticky"
				elevation={1}
				sx={(t) => ({
					bgcolor: t.palette.background.paper,
					color: t.palette.text.primary,
					borderBottom: `1px solid ${t.palette.divider}`,
					boxShadow: "none",
				})}
			>
				<Toolbar sx={{ gap: 1.5 }}>
					<Box sx={{ display: "flex", alignItems: "center", gap: 2.5, flexGrow: 1 }}>
						<img src={metricshubLogo} alt="MetricsHub" style={{ width: 80, height: "auto" }} />
						<StatusText sx={{ ml: 0.5 }} />
						<OtelStatusIcon />
					</Box>

					{user && (
						<Typography variant="body2" sx={{ mr: 1, opacity: 0.75 }}>
							{`Signed in as ${user.username}`}
						</Typography>
					)}
					<StatusDetailsMenu />
					<ToggleTheme onClick={toggleTheme} />
					<Logout onClick={handleSignOut} />
				</Toolbar>
			</AppBar>
		</>
	);
};

export default NavBar;
