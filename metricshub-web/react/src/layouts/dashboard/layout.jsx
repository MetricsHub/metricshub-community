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
	IconButton,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import CloseIcon from "@mui/icons-material/Close";

import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";
import { withAuthGuard } from "../../hocs/with-auth-guard";
import { useAppDispatch } from "../../hooks/store";
import { fetchApplicationStatus } from "../../store/thunks/applicationStatusThunks";

import StatusText from "../../components/status/status-text";
import StatusDetailsMenu from "../../components/status/status-details-menu";
import OtelStatusIcon from "../../components/status/otel-status-icon";
import TestButton from "../../components/test-button";
// import ErrorBoundary from "../../components/error-boundary";

import Sidebar from "../../components/sidebar/sidebar";

// For initial main-content offset before first width report
const DEFAULT_WIDTH = 320;

export const DashboardLayout = withAuthGuard(({ children }) => {
	const navigate = useNavigate();
	const { signOut, user } = useAuth();
	const dispatch = useAppDispatch();
	const isSmall = useMediaQuery("(max-width:900px)");

	const [sidebarOpen, setSidebarOpen] = useState(!isSmall);
	const [sidebarWidth, setSidebarWidth] = useState(DEFAULT_WIDTH);

	useEffect(() => {
		dispatch(fetchApplicationStatus());
		const id = setInterval(() => dispatch(fetchApplicationStatus()), 30000);
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
	const activeTree = "machines";

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
					<IconButton
						size="small"
						edge="start"
						aria-label="Toggle sidebar"
						onClick={() => setSidebarOpen((v) => !v)}
					>
						{sidebarOpen ? <CloseIcon /> : <MenuIcon />}
					</IconButton>

					<Box sx={{ display: "flex", alignItems: "center", gap: 1, flexGrow: 1 }}>
						<Typography variant="h6">MetricsHub</Typography>
						<StatusText sx={{ ml: 0.5 }} />
						<OtelStatusIcon />
					</Box>

					{/*<TestButton />*/}

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
						...(isSmall ? {} : sidebarOpen ? { ml: `${sidebarWidth}px` } : {}),
					};
				}}
			>
				{/* <Container maxWidth="lg" sx={{ py: 3 }}>
          <ErrorBoundary>{children}</ErrorBoundary>
        </Container> */}
			</Box>
		</>
	);
});
