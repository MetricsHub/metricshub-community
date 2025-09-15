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
} from "@mui/material";
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

// For initial main-content offset before first width report
const DEFAULT_WIDTH = 320;
// Refresh status every 30 seconds
const STATUS_REFRESH_MS = 30000;

/**
 * Dashboard layout component
 */
export const DashboardLayout = withAuthGuard(({ children }) => {
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
					<Button onClick={handleSignOut} variant="outlined" size="small">
						Sign out
					</Button>
				</Toolbar>
			</AppBar>

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
				<Container maxWidth="lg" sx={{ py: 3 }}>
					<ErrorBoundary>{children}</ErrorBoundary>
				</Container>
			</Box>
		</>
	);
});
