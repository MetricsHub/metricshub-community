import * as React from "react";
import { useMemo, useState, useEffect, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { ThemeProvider, CssBaseline, Box, CircularProgress } from "@mui/material";
import { Provider as ReduxProvider } from "react-redux";
import { useTheme } from "@mui/material/styles";
import { store } from "./store";
import { createTheme as createMetricsHubTheme } from "./theme";
import { paths } from "./paths";
import UnsavedChangesGuard from "./components/common/UnsavedChangesGuard";
import GlobalSnackbarProvider from "./contexts/GlobalSnackbarContext";
import { AuthProvider } from "./contexts/JwtContext";
import { useAuth } from "./hooks/use-auth";
import { withAuthGuard } from "./hocs/WithAuthGuard";

import logoDark from "./assets/logo-dark.svg";
import logoLight from "./assets/logo-light.svg";

// Lazy pages
const LoginPage = React.lazy(() => import("./pages/LoginPage")); // already wrapped with AuthLayout
const Explorer = React.lazy(() => import("./pages/ExplorerPage"));
const Configuration = React.lazy(() => import("./pages/ConfigurationPage"));
const NavBar = React.lazy(() => import("./components/navbar/Navbar"));

/**
 * Splash screen while loading
 * @returns {React.ReactNode} The SplashScreen component
 */
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
 * Layout for authenticated app
 * @param {Function} onToggleTheme - The function to toggle the theme
 * @returns {React.ReactNode} The AppLayout component
 */
const AppLayout = ({ onToggleTheme }) => {
	return (
		<>
			<NavBar onToggleTheme={onToggleTheme} />
			<UnsavedChangesGuard />
			<Outlet />
		</>
	);
};

// Wrap AppLayout with authentication guard
const GuardedAppLayout = withAuthGuard(AppLayout);

const STORAGE_KEY = "metricshub.paletteMode"; // 'light' | 'dark'

/**
 * Theme settings
 * @type {Object}
 * @property {string} direction - The direction of the theme
 * @property {string} defaultMode - The default mode of the theme
 * @property {boolean} responsiveFontSizes - Whether to responsive font sizes
 */
const themeSettings = {
	direction: "ltr",
	defaultMode: "dark",
	responsiveFontSizes: true,
};

// Retrieve saved theme
const getInitialMode = (defaultMode = "dark") => {
	const saved = localStorage.getItem(STORAGE_KEY);
	if (saved === "light" || saved === "dark") return saved;
	return defaultMode;
};

/**
 * Main App component
 * @returns {React.ReactNode} The App component
 */
export default function App() {
	const [mode, setMode] = useState(() => getInitialMode(themeSettings.defaultMode));

	// Persist theme preference
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
			<ReduxProvider store={store}>
				<GlobalSnackbarProvider>
					<AuthProvider>
						<AppContent
							onToggleTheme={() => setMode((prev) => (prev === "light" ? "dark" : "light"))}
						/>
					</AuthProvider>
				</GlobalSnackbarProvider>
			</ReduxProvider>
		</ThemeProvider>
	);
}

/**
 * AppContent component
 * @param {Function} onToggleTheme - The function to toggle the theme
 * @returns {React.ReactNode} The AppContent component
 */
const AppContent = ({ onToggleTheme }) => {
	const { isInitialized } = useAuth();

	if (!isInitialized) return <SplashScreen />;

	return (
		<BrowserRouter>
			<Suspense fallback={<SplashScreen />}>
				<Routes>
					{/* Public routes */}
					<Route path={paths.login} element={<LoginPage />} />

					{/* Private routes */}
					<Route element={<GuardedAppLayout onToggleTheme={onToggleTheme} />}>
						<Route
							path={paths.explorer}
							element={<Navigate to={paths.explorerWelcome} replace />}
						/>
						<Route path={paths.explorerWelcome} element={<Explorer />} />
						<Route path="/explorer/resource-groups/:name" element={<Explorer />} />
						<Route
							path="/explorer/resource-groups/:group/resources/:resource"
							element={<Explorer />}
						/>
						<Route
							path="/explorer/resource-groups/:group/resources/:resource/monitors/:monitorType"
							element={<Explorer />}
						/>
						<Route path="/explorer/resources/:resource" element={<Explorer />} />
						<Route
							path="/explorer/resources/:resource/monitors/:monitorType"
							element={<Explorer />}
						/>
						<Route path={paths.configuration} element={<Configuration />} />
						<Route path={`${paths.configuration}/:name`} element={<Configuration />} />
						{/* Catch-all */}
						<Route path="*" element={<Navigate to={paths.explorer} replace />} />
					</Route>
				</Routes>
			</Suspense>
		</BrowserRouter>
	);
};
