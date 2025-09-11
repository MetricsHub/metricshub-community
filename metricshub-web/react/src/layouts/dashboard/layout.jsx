import * as React from "react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
	AppBar,
	Box,
	Button,
	Container,
	CssBaseline,
	Toolbar,
	Typography,
	Drawer,
	useMediaQuery,
	IconButton,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";

import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";
import { withAuthGuard } from "../../hocs/with-auth-guard";

import { useAppDispatch } from "../../hooks/store";
import { fetchApplicationStatus } from "../../store/thunks/applicationStatusThunks";

import StatusText from "../../components/status-text";
import StatusDetailsMenu from "../../components/status-details-menu";
import OtelStatusIcon from "../../components/otel-status-icon";
import TestButton from "../../components/TestButton";
import ErrorBoundary from "../../components/ErrorBoundary";
import MachineTree from "../../components/sidebar/MachineTree";

const DRAWER_WIDTH = 260;

export const DashboardLayout = withAuthGuard(({ children }) => {
	const navigate = useNavigate();
	const { signOut, user } = useAuth();
	const dispatch = useAppDispatch();
	const isSmall = useMediaQuery("(max-width:900px)");

	const [sidebarOpen, setSidebarOpen] = useState(!isSmall);

	const handleSignOut = useCallback(async () => {
		try {
			await signOut();
		} finally {
			navigate(paths?.auth?.login ?? "/login", { replace: true });
		}
	}, [signOut, navigate]);

	useEffect(() => {
		dispatch(fetchApplicationStatus());
		const id = setInterval(() => dispatch(fetchApplicationStatus()), 30000);
		return () => clearInterval(id);
	}, [dispatch]);

	useEffect(() => {
		setSidebarOpen(!isSmall);
	}, [isSmall]);

	return (
		<>
			<CssBaseline />

			{/* AppBar: always full-width, never pushed by the drawer */}
			<AppBar
				position="fixed"
				elevation={1}
				color="default"
				sx={{
					zIndex: (t) => t.zIndex.drawer + 1, // stay above the drawer
				}}
			>
				<Toolbar sx={{ gap: 2 }}>
					<IconButton
						size="small"
						edge="start"
						aria-label={sidebarOpen ? "Hide sidebar" : "Show sidebar"}
						onClick={() => setSidebarOpen((v) => !v)}
					>
						<MenuIcon />
					</IconButton>

					<Box sx={{ display: "flex", alignItems: "center", gap: 1, flexGrow: 1 }}>
						<Typography variant="h6" component="div">
							MetricsHub
						</Typography>
						<StatusText sx={{ ml: 0.5 }} />
						<OtelStatusIcon />
					</Box>

					<TestButton />

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

			{/* Drawer: starts below the AppBar (never pushes/resizes it) */}
			<Drawer
				variant={isSmall ? "temporary" : "persistent"}
				open={sidebarOpen}
				onClose={() => setSidebarOpen(false)}
				ModalProps={{ keepMounted: true }}
				sx={{ width: DRAWER_WIDTH, flexShrink: 0 }}
				slotProps={{
					paper: {
						sx: (t) => {
							const hSmUp = t.mixins.toolbar?.minHeight ?? 64; // desktop AppBar height
							const hXs = 56;                                   // mobile AppBar height
							return {
								width: DRAWER_WIDTH,
								boxSizing: "border-box",
								top: { xs: hXs, sm: hSmUp },                    // start right under AppBar
								height: { xs: `calc(100% - ${hXs}px)`, sm: `calc(100% - ${hSmUp}px)` },
								p: 0,
								m: 0,
								overflow: "hidden",
								display: "flex",
								flexDirection: "column",
								borderRight: 1,
								borderColor: "divider",
							};
						},
					},
				}}
			>
				<MachineTree />
			</Drawer>

			{/* Main content: sits under AppBar; shifts right on desktop when drawer is open */}
			<Box
				component="main"
				sx={(t) => {
					const hSmUp = t.mixins.toolbar?.minHeight ?? 64;
					const hXs = 56;
					return {
						minHeight: "100vh",
						bgcolor: "background.default",
						mt: { xs: `${hXs}px`, sm: `${hSmUp}px` }, // keep under AppBar
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
