import * as React from "react";
import { useMemo, useState } from "react";
import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom";
import { ThemeProvider, createTheme, CssBaseline, Box, CircularProgress } from "@mui/material";
import { AuthProvider, AuthConsumer } from "./contexts/jwt-context";
import logoDark from "./assets/logo-dark.svg";
import logoLight from "./assets/logo-light.svg";
import { Provider as ReduxProvider } from "react-redux";
import { store } from "./store";
import { useTheme } from "@mui/material/styles";

const LoginPage = React.lazy(() => import("./pages/login")); // already wrapped with AuthLayout
const Monitor = React.lazy(() => import("./pages/monitor"));
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

export default function App() {
	// light or dark
	const [mode, setMode] = useState("dark");

	// Create theme dynamically
	const theme = useMemo(
		() =>
			createTheme({
				palette: {
					mode,
					background: {
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
												<Route path="/monitor" element={<Monitor />} />
												{/* Fallback */}
												<Route path="*" element={<Navigate to="/monitor" replace />} />
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
		</ThemeProvider>
	);
}
