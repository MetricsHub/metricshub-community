import * as React from "react";
import { useMemo, useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { ThemeProvider, CssBaseline, Box, CircularProgress } from "@mui/material";
import { AuthProvider, AuthConsumer } from "./contexts/jwt-context";
import logoDark from "./assets/logo-dark.svg";
import logoLight from "./assets/logo-light.svg";
import { Provider as ReduxProvider } from "react-redux";
import { store } from "./store";
import { useTheme } from "@mui/material/styles";
import { createTheme as createMetricsHubTheme } from "./theme";
import { paths } from "./paths";
import GlobalSnackbarProvider from "./components/common/GlobalSnackbar";

const LoginPage = React.lazy(() => import("./pages/login")); // already wrapped with AuthLayout
const Explorer = React.lazy(() => import("./pages/explorer"));
const Configuration = React.lazy(() => import("./pages/configuration"));
const NavBar = React.lazy(() => import("./components/navbar/navbar"));

// Splash screen while loading
const SplashScreen = () => {
	const theme = useTheme();
	return (
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
				src={theme.palette.mode === "dark" ? logoDark : logoLight}
				alt="MetricsHub"
				style={{ width: 120, height: "auto", marginBottom: "1rem" }}
			/>
			<CircularProgress />
		</Box>
	);
};

/**
 * App layout component
 * @returns JSX.Element
 */
const AppLayout = ({ authed, toggleTheme }) => {
	return (
		<>
			{authed && <NavBar toggleTheme={toggleTheme} />}
			{/* Child pages render here */}
			<Outlet />
		</>
	);
};

// Theme settings
const themeSettings = {
	direction: "ltr",
	paletteMode: "dark",
	responsiveFontSizes: true,
};

// Key to store theme preference in localStorage
const STORAGE_KEY = "metricshub.paletteMode"; // 'light' | 'dark'

// Get initial mode from localStorage or default to dark
const getInitialMode = (defaultMode = "dark") => {
	const saved = localStorage.getItem(STORAGE_KEY);
	if (saved === "light" || saved === "dark") return saved;
	return defaultMode;
};

export default function App() {
	// light or dark
	const [mode, setMode] = useState(() => getInitialMode(themeSettings.paletteMode));

	// Persist to localStorage whenever mode changes
	useEffect(() => {
		localStorage.setItem(STORAGE_KEY, mode);
	}, [mode]);

	// Create theme dynamically
	const theme = useMemo(
		() =>
			createMetricsHubTheme({
				direction: themeSettings.direction,
				paletteMode: mode,
				responsiveFontSizes: themeSettings.responsiveFontSizes,
			}),
		[mode],
	);

	return (
		<ThemeProvider theme={theme}>
			<CssBaseline />
			<GlobalSnackbarProvider>
				<ReduxProvider store={store}>
					<AuthProvider>
						<AuthConsumer>
							{(auth) =>
								auth.isInitialized ? (
									<BrowserRouter>
										<React.Suspense fallback={<SplashScreen />}>
											<Routes>
												<Route path={paths.login} element={<LoginPage />} />
												{/* App routes with NavBar */}
												<Route
													element={
														<AppLayout
															authed={auth.isAuthenticated}
															toggleTheme={() =>
																setMode((prev) => (prev === "light" ? "dark" : "light"))
															}
														/>
													}
												>
													<Route path={paths.explorer} element={<Explorer />} />
													<Route path={paths.configuration} element={<Configuration />} />
													<Route
														path={`${paths.configuration}/:name`}
														element={<Configuration />}
													/>
													{/* Fallback */}
													<Route path="*" element={<Navigate to={paths.explorer} replace />} />
												</Route>
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
			</GlobalSnackbarProvider>
		</ThemeProvider>
	);
}
