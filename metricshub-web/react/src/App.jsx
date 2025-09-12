import * as React from "react";
import { useMemo, useState } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { ThemeProvider, createTheme, CssBaseline, Box, CircularProgress } from "@mui/material";
import { AuthProvider, AuthConsumer } from "./contexts/jwt-context";
import metricshubLogo from "./assets/metricshub.svg";
import { Provider as ReduxProvider } from "react-redux";
import { store } from "./store";

const LoginPage = React.lazy(() => import("./pages/login")); // already wrapped with AuthLayout
const HomePage = React.lazy(() => import("./pages/home"));

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
		<img
			src={metricshubLogo}
			alt="MetricsHub"
			style={{ width: 120, height: "auto", marginBottom: "1rem" }}
		/>
		<CircularProgress />
	</Box>
);

export default function App() {
	// Track theme mode (light or dark)
	const [mode, setMode] = useState("light");

	// Create theme dynamically
	const theme = useMemo(
		() =>
			createTheme({
				palette: {
					mode,
					background: {
						// AppBar color for each mode
						appbar: mode === "light" ? "#f5f5f7" : "#1e1e1e",
					},
				},
			}),
		[mode],
	);

	return (
		<ThemeProvider theme={theme}>
			<CssBaseline />
			<ReduxProvider store={store}>
				<AuthProvider>
					<AuthConsumer>
						{(auth) =>
							auth.isInitialized ? (
								<BrowserRouter>
									<React.Suspense fallback={<SplashScreen />}>
										<Routes>
											<Route path="/login" element={<LoginPage />} />
											<Route
												path="/"
												element={
													<HomePage
														toggleTheme={() =>
															setMode((prev) => (prev === "light" ? "dark" : "light"))
														}
													/>
												}
											/>
											{/* Fallback */}
											<Route path="*" element={<Navigate to="/" replace />} />
										</Routes>
									</React.Suspense>
								</BrowserRouter>
							) : (
								<SplashScreen />
							)
						}
					</AuthConsumer>
				</AuthProvider>
			</ReduxProvider>
		</ThemeProvider>
	);
}
