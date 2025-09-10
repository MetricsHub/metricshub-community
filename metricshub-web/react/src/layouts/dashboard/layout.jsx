import * as React from "react";
import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { AppBar, Box, Button, Container, CssBaseline, Toolbar, Typography } from "@mui/material";
import { useAuth } from "../../hooks/use-auth";
import { paths } from "../../paths";
import { withAuthGuard } from "../../hocs/with-auth-guard";
import StatusText from "../../components/status-text";
import StatusDetailsMenu from "../../components/status-details-menu";
import OtelStatusIcon from "../../components/otel-status-icon";

export const DashboardLayout = withAuthGuard(({ children }) => {
	const navigate = useNavigate();
	const { signOut, user } = useAuth();

	const handleSignOut = useCallback(async () => {
		try {
			await signOut();
		} finally {
			navigate(paths?.auth?.login ?? "/login", { replace: true });
		}
	}, [signOut, navigate]);

	return (
		<React.Fragment>
			<CssBaseline />
			<AppBar position="fixed" elevation={1} color="default">
				<Toolbar sx={{ gap: 2 }}>
					{/* Left side: Logo + Status */}
					<Box sx={{ display: "flex", alignItems: "center", gap: 1, flexGrow: 1 }}>
						<Typography variant="h6" component="div">
							MetricsHub
						</Typography>
						<StatusText sx={{ ml: 0.5 }} />
						<OtelStatusIcon />
					</Box>

					{/* Right side: Username + Dropdown + Sign out */}
					{user && (
						<Typography variant="body2" sx={{ mr: 1, opacity: 0.75 }}>
							{`Signed in as ${user.username}`}
						</Typography>
					)}

					{/* The dropdown menu with backend details */}
					<StatusDetailsMenu />

					<Button onClick={handleSignOut} variant="outlined" size="small">
						Sign out
					</Button>
				</Toolbar>
			</AppBar>

			{/* Spacer to offset fixed AppBar */}
			<Toolbar />

			<Box component="main" sx={{ minHeight: "100vh", bgcolor: "background.default" }}>
				<Container maxWidth="lg" sx={{ py: 3 }}>
					{children}
				</Container>
			</Box>
		</React.Fragment>
	);
});
