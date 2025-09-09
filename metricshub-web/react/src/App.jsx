import * as React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { ThemeProvider, createTheme, CssBaseline, Box, CircularProgress } from "@mui/material";
import { AuthProvider, AuthConsumer } from "./contexts/jwt-context";
import LoginPage from "./pages/login";
import HomePage from "./pages/home";
import metricshubLogo from "./assets/metricshub.svg";

const SplashScreen = () => (
	<Box
		sx={{
			minHeight: "100vh",
			display: "flex",
			flexDirection: "column",
			alignItems: "center",
			justifyContent: "center",
			gap: 2,
		}}
	>
		<img src={metricshubLogo} alt="MetricsHub" style={{ width: 120, height: "auto", marginBottom: "1rem" }} />
		<CircularProgress />
	</Box>
);

export default function App() {
	const theme = createTheme({
		palette: { mode: "light" },
	});

	return (
		<ThemeProvider theme={theme}>
			<CssBaseline />
			<AuthProvider>
				<AuthConsumer>
					{(auth) =>
						auth.isInitialized ? (
							<BrowserRouter>
								<Routes>
									<Route path="/login" element={<LoginPage />} />
									<Route path="/" element={<HomePage />} />
									{/* Fallback */}
									<Route path="*" element={<Navigate to="/" replace />} />
								</Routes>
							</BrowserRouter>
						) : (
							<SplashScreen />
						)
					}
				</AuthConsumer>
			</AuthProvider>
		</ThemeProvider>
	);
}
