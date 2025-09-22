import React, { useEffect, useCallback } from "react";
import logoDark from "../../assets/logo-dark.svg";
import logoLight from "../../assets/logo-light.svg";
import { useTheme } from "@mui/material/styles";

import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";

import { useNavigate, NavLink } from "react-router-dom";
import {
	AppBar,
	Box,
	IconButton,
	Tooltip,
	CssBaseline,
	Toolbar,
	Typography,
	Button,
} from "@mui/material";

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
		"&:hover": { bgcolor: "action.hover" },
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
					bgcolor: t.palette.background.paper,
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
							<img src={metricshubLogo} alt="MetricsHub" style={{ width: 80, height: "auto" }} />
							<StatusText sx={{ ml: 0.5 }} />
							<OtelStatusIcon />
						</Box>

						{/* Navigation Buttons */}
						<Box sx={{ display: "flex", gap: 0, ml: 1, alignSelf: "stretch" }}>
							<Button component={NavLink} to={paths.explorer} sx={navBtnSx}>
								Explorer
							</Button>
							<Button component={NavLink} to={paths.configuration} sx={navBtnSx}>
								Configuration
							</Button>
						</Box>
					</Box>

					{/* ================= RIGHT SIDE ================= */}
					<Box sx={{ display: "flex", alignItems: "center", gap: 1.5, ml: "auto" }}>
						{user && (
							<Typography variant="body2" sx={{ mr: 1, opacity: 0.75 }}>
								{`Signed in as ${user.username}`}
							</Typography>
						)}
						<StatusDetailsMenu />
						<ToggleTheme onClick={toggleTheme} />
						<Logout onClick={handleSignOut} />
					</Box>
				</Toolbar>
			</AppBar>
		</>
	);
};

export default NavBar;
