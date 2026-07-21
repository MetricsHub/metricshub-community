import * as React from "react";
import { useMemo, useState, useEffect, useCallback, Suspense } from "react";
import {
	createBrowserRouter,
	createRoutesFromElements,
	RouterProvider,
	Route,
	Navigate,
	Outlet,
	useLocation,
} from "react-router-dom";
import { ThemeProvider, CssBaseline, Box, CircularProgress } from "@mui/material";
import { Provider as ReduxProvider } from "react-redux";
import { useTheme } from "@mui/material/styles";
import { store } from "./store";
import { createTheme as createMetricsHubTheme } from "./theme";
import { paths } from "./paths";
import UnsavedChangesGuard from "./components/common/UnsavedChangesGuard";
import WriteProtectionDialog from "./components/common/WriteProtectionDialog";
import GlobalSnackbarProvider from "./contexts/GlobalSnackbarContext";
import { AuthProvider } from "./contexts/JwtContext";
import { useAuth } from "./hooks/use-auth";
import { withAuthGuard } from "./hocs/WithAuthGuard";

import logoDark from "./assets/logo-dark.svg";
import logoLight from "./assets/logo-light.svg";

/** Redirects removed guided-config global-settings URLs to the new locations. */
const LegacyGlobalSettingsRedirect = () => {
	const { hash } = useLocation();
	if (hash) {
		return <Navigate to={`${paths.agentConfig}${hash}`} replace />;
	}
	return <Navigate to={paths.hostsResourceGroups()} replace />;
};

// Lazy pages
const LoginPage = React.lazy(() => import("./pages/LoginPage")); // already wrapped with AuthLayout
const Explorer = React.lazy(() => import("./pages/ExplorerPage"));
const Configuration = React.lazy(() => import("./pages/ConfigurationPage"));
const HostsPage = React.lazy(() => import("./pages/HostsPage"));
const Chat = React.lazy(() => import("./pages/ChatPage"));
const AgentPage = React.lazy(() => import("./pages/AgentPage"));
const AgentConfigPage = React.lazy(() => import("./pages/AgentConfigPage"));
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
		<Suspense fallback={<SplashScreen />}>
			<NavBar onToggleTheme={onToggleTheme} />
			<UnsavedChangesGuard />
			<Outlet />
		</Suspense>
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

	// Stable identity: the router is memoized on this callback, so a new identity
	// per render would rebuild (and remount) the whole route tree.
	const handleToggleTheme = useCallback(() => {
		setMode((prev) => (prev === "light" ? "dark" : "light"));
	}, []);

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
						<WriteProtectionDialog />
						<AppContent onToggleTheme={handleToggleTheme} />
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

	// Data router (createBrowserRouter) instead of <BrowserRouter> so route
	// components can use useBlocker to guard navigation with unsaved changes.
	const router = useMemo(
		() =>
			createBrowserRouter(
				createRoutesFromElements([
					/* Public routes */
					<Route
						key="login"
						path={paths.login}
						element={
							<Suspense fallback={<SplashScreen />}>
								<LoginPage />
							</Suspense>
						}
					/>,

					/* Private routes */
					<Route key="app" element={<GuardedAppLayout onToggleTheme={onToggleTheme} />}>
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
							path="/explorer/resource-groups/:group/resources/:resource/connectors/:connectorId/monitors/:monitorType"
							element={<Explorer />}
						/>
						<Route path="/explorer/resources/:resource" element={<Explorer />} />
						<Route
							path="/explorer/resources/:resource/connectors/:connectorId/monitors/:monitorType"
							element={<Explorer />}
						/>
						<Route path="/configuration" element={<Navigate to={paths.configuration} replace />} />
						<Route path={paths.configuration} element={<Configuration />} />
						<Route path="/configuration/yaml-editor/config/:name" element={<Configuration />} />
						<Route path="/configuration/yaml-editor/otel/:name" element={<Configuration />} />
						<Route path="/configuration/guided-config/resources/new" element={<HostsPage />} />
						<Route
							path="/configuration/guided-config/resource-groups/:groupName/resources/:hostId/edit"
							element={<HostsPage />}
						/>
						<Route
							path="/configuration/guided-config/resource-groups/:groupName/resources/:resourceId"
							element={<HostsPage />}
						/>
						<Route
							path="/configuration/guided-config/no-resource-group/resources/new"
							element={<HostsPage />}
						/>
						<Route
							path="/configuration/guided-config/no-resource-group/resources/:hostId/edit"
							element={<HostsPage />}
						/>
						<Route
							path="/configuration/guided-config/no-resource-group/resources/:hostId"
							element={<HostsPage />}
						/>
						<Route path="/configuration/guided-config/no-resource-group" element={<HostsPage />} />
						{/* Guided-config: resource-groups is the new default landing page */}
						<Route path="/configuration/guided-config/resource-groups" element={<HostsPage />} />
						<Route
							path="/configuration/guided-config/resource-groups/new"
							element={<HostsPage />}
						/>
						<Route
							path="/configuration/guided-config/resource-groups/:groupName/edit"
							element={<HostsPage />}
						/>
						<Route
							path="/configuration/guided-config/resource-groups/:groupName"
							element={<HostsPage />}
						/>
						<Route
							path="/configuration/guided-config/global-settings"
							element={<LegacyGlobalSettingsRedirect />}
						/>
						<Route
							path={paths.guidedConfig}
							element={<Navigate to={paths.hostsResourceGroups()} replace />}
						/>
						<Route
							path={paths.hosts}
							element={<Navigate to={paths.hostsResourceGroups()} replace />}
						/>
						<Route
							path="/dashboard"
							element={<Navigate to={paths.hostsResourceGroups()} replace />}
						/>
						<Route
							path="/dashboard/*"
							element={<Navigate to={paths.hostsResourceGroups()} replace />}
						/>
						<Route path={paths.agent} element={<AgentPage />} />
						<Route path={paths.agentConfig} element={<AgentConfigPage />} />
						<Route path={paths.chat} element={<Chat />} />
						{/* Catch-all */}
						<Route path="*" element={<Navigate to={paths.explorer} replace />} />
					</Route>,
				]),
			),
		[onToggleTheme],
	);

	if (!isInitialized) return <SplashScreen />;

	return <RouterProvider router={router} />;
};
