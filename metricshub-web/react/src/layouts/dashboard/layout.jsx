import * as React from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
	AppBar,
	Box,
	Button,
	CssBaseline,
	Toolbar,
	Typography,
	useMediaQuery,
	Container,
	IconButton,
	Tooltip
} from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";
import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";
import { withAuthGuard } from "../../hocs/with-auth-guard";
import { useAppDispatch } from "../../hooks/store";
import { fetchApplicationStatus } from "../../store/thunks/applicationStatusThunks";
import StatusText from "../../components/status/status-text";
import StatusDetailsMenu from "../../components/status/status-details-menu";
import OtelStatusIcon from "../../components/status/otel-status-icon";
import ErrorBoundary from "../../components/error-boundary";
import Sidebar from "../../components/sidebar/sidebar";
import logoDark from "../../assets/logo-dark.svg";
import logoLight from "../../assets/logo-light.svg";
import { useTheme } from "@mui/material/styles";
import { NavLink } from "react-router-dom";

// For initial main-content offset before first width report
const DEFAULT_WIDTH = 320;
// Refresh status every 30 seconds
const STATUS_REFRESH_MS = 30000;

/**
 * Dashboard layout component
 */

import { Outlet } from "react-router-dom";

export const DashboardLayout = withAuthGuard(() => {
	const navigate = useNavigate();
	const { signOut, user } = useAuth();
	const dispatch = useAppDispatch();
	const isSmall = useMediaQuery("(max-width:900px)");

	const [sidebarOpen, setSidebarOpen] = useState(!isSmall);
	const [sidebarWidth, setSidebarWidth] = useState(DEFAULT_WIDTH);

	useEffect(() => {
		dispatch(fetchApplicationStatus());
		const id = setInterval(() => dispatch(fetchApplicationStatus()), STATUS_REFRESH_MS);
		return () => clearInterval(id);
	}, [dispatch]);

	useEffect(() => {
		setSidebarOpen(!isSmall);
	}, [isSmall]);

	const handleSignOut = useCallback(async () => {
		try {
			await signOut();
		} finally {
			navigate(paths?.auth?.login ?? "/login", { replace: true });
		}
	}, [signOut, navigate]);

	const appBarHeights = useMemo(() => ({ xs: 56, sm: 64 }), []);

	// Choose which tree to display (can be driven by route or Redux later)
	const activeTree = "none";

	const theme = useTheme();
	const metricshubLogo = theme.palette.mode === "dark" ? logoDark : logoLight;
	const navBtnSx = {
		alignSelf: "stretch",          // ensure each button fills the group's height
		height: "100%",
		minHeight: "auto",             // override MUI’s default 36px minHeight
		borderRadius: 0,
		px: 2.25,
		py: 0,                         // remove vertical padding
		lineHeight: 1,
		minWidth: 90,
		color: "text.primary",
		borderBottom: "2px solid transparent",     // reserve underline space
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
				position="fixed"
				elevation={1}
				sx={(t) => ({
					zIndex: t.zIndex.drawer + 1,
					bgcolor: t.palette.background.paper,
					color: t.palette.text.primary,
					borderBottom: `1px solid ${t.palette.divider}`,
					boxShadow: "none",
				})}
			>
				<Toolbar disableGutters sx={{ gap: 1.5, minHeight: { xs: 56, sm: 64 }, px: 2 }}>
					{/* LEFT SIDE: */}
					<Box sx={{ display: "flex", gap: 2.5, height: "100%" }}>
						<Box sx={{ display: "flex", alignItems: "center", gap: 2.5 }}>
							<img src={metricshubLogo} alt="MetricsHub" style={{ width: 80, height: "auto" }} />
							<StatusText sx={{ ml: 0.5 }} />
							<OtelStatusIcon />
						</Box>
						<Box sx={{ display: "flex", gap: 0, ml: 1, alignSelf: "stretch" }}>
							<Button component={NavLink} to="/" end sx={navBtnSx}>Dashboard</Button>
							<Button component={NavLink} to="/config" sx={navBtnSx}>Config</Button>
						</Box>
					</Box>

					{/* RIGHT SIDE: */}
					<Box sx={{ display: "flex", alignItems: "center", gap: 1.5, ml: "auto" }}>
						{user && (
							<Typography variant="body2" sx={{ opacity: 0.75 }}>
								{`Signed in as ${user.username}`}
							</Typography>
						)}
						<StatusDetailsMenu />
						<Tooltip title="Sign out">
							<IconButton onClick={handleSignOut} color="default">
								<LogoutIcon />
							</IconButton>
						</Tooltip>
					</Box>
				</Toolbar>
			</AppBar >

			<Sidebar
				open={sidebarOpen}
				onOpenChange={setSidebarOpen}
				onWidthChange={setSidebarWidth}
				appBarHeights={appBarHeights}
				activeTree={activeTree}
			/>

			{/* Main content */}
			<Box
				component="main"
				sx={(t) => {
					const hSmUp = t.mixins.toolbar?.minHeight ?? appBarHeights.sm;
					const hXs = appBarHeights.xs;
					return {
						minHeight: "100vh",
						bgcolor: "background.default",
						mt: { xs: `${hXs}px`, sm: `${hSmUp}px` },
						...(isSmall ? {} : { ml: `${sidebarWidth}px` }),
					};
				}}
			>
				<Box sx={{ py: 3, px: 3, width: "100%" }}>
					<ErrorBoundary><Outlet /></ErrorBoundary>
				</Box>
			</Box>
		</>
	);
});
