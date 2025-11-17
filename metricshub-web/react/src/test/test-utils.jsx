import { render } from "@testing-library/react";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "../store/slices/application-status-slice";
import { configReducer } from "../store/slices/config-slice";
import { AuthProvider } from "../contexts/JwtContext";

/**
 * Create a test store with optional initial state
 * @param {Object} initialState - Initial state for the store
 * @returns {Object} Configured Redux store
 */
export function createTestStore(initialState = {}) {
	return configureStore({
		reducer: {
			applicationStatus: applicationStatusReducer,
			config: configReducer,
		},
		preloadedState: initialState,
	});
}

/**
 * Render a component with Redux Provider
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @param {Object} options.store - Redux store (optional, creates default if not provided)
 * @param {Object} options.initialState - Initial state for store (optional)
 * @returns {Object} Render result and store
 */
export function renderWithRedux(ui, { store, initialState, ...renderOptions } = {}) {
	const testStore = store || createTestStore(initialState);
	const Wrapper = ({ children }) => (
		<Provider store={testStore}>{children}</Provider>
	);
	const result = render(ui, { wrapper: Wrapper, ...renderOptions });
	return { ...result, store: testStore };
}

/**
 * Render a component with React Router
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @returns {Object} Render result
 */
export function renderWithRouter(ui, options = {}) {
	const Wrapper = ({ children }) => <BrowserRouter>{children}</BrowserRouter>;
	return render(ui, { wrapper: Wrapper, ...options });
}

/**
 * Render a component with Redux Provider and React Router
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @param {Object} options.store - Redux store (optional)
 * @param {Object} options.initialState - Initial state for store (optional)
 * @returns {Object} Render result and store
 */
export function renderWithReduxAndRouter(
	ui,
	{ store, initialState, ...renderOptions } = {},
) {
	const testStore = store || createTestStore(initialState);
	const Wrapper = ({ children }) => (
		<Provider store={testStore}>
			<BrowserRouter>{children}</BrowserRouter>
		</Provider>
	);
	const result = render(ui, { wrapper: Wrapper, ...renderOptions });
	return { ...result, store: testStore };
}

/**
 * Render a component with Auth Provider
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @returns {Object} Render result
 */
export function renderWithAuth(ui, options = {}) {
	const Wrapper = ({ children }) => <AuthProvider>{children}</AuthProvider>;
	return render(ui, { wrapper: Wrapper, ...options });
}

/**
 * Render a component with all providers (Redux, Router, Auth)
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @param {Object} options.store - Redux store (optional)
 * @param {Object} options.initialState - Initial state for store (optional)
 * @returns {Object} Render result and store
 */
export function renderWithAllProviders(
	ui,
	{ store, initialState, ...renderOptions } = {},
) {
	const testStore = store || createTestStore(initialState);
	const Wrapper = ({ children }) => (
		<Provider store={testStore}>
			<BrowserRouter>
				<AuthProvider>{children}</AuthProvider>
			</BrowserRouter>
		</Provider>
	);
	const result = render(ui, { wrapper: Wrapper, ...renderOptions });
	return { ...result, store: testStore };
}
