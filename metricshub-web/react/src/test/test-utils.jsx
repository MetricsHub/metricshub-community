/**
 * Test Utilities
 *
 * This file provides helper functions for testing React components.
 * These utilities wrap components with necessary providers (Redux, Router, Auth).
 *
 * Why use these utilities?
 * - Many components need Redux store, React Router, or Auth context
 * - Instead of wrapping every test component manually, we use these helpers
 * - Makes tests cleaner and more maintainable
 */

import { render } from "@testing-library/react";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "../store/slices/application-status-slice";
import { configReducer } from "../store/slices/config-slice";
import { explorerReducer } from "../store/slices/explorer-slice";
import { AuthProvider } from "../contexts/JwtContext";

/**
 * Create a test store with optional initial state
 *
 * This creates a Redux store for testing with the same reducers as the real app.
 * You can provide initial state to test specific scenarios.
 *
 * @param {Object} initialState - Initial state for the store (optional)
 * @returns {Object} Configured Redux store
 */
export function createTestStore(initialState = {}) {
	return configureStore({
		reducer: {
			applicationStatus: applicationStatusReducer,
			config: configReducer,
			explorer: explorerReducer,
		},
		preloadedState: initialState,
	});
}

/**
 * Render a component with Redux Provider
 *
 * Use this when your component uses Redux hooks (useSelector, useDispatch).
 * The component will have access to the Redux store.
 *
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @param {Object} options.store - Redux store (optional, creates default if not provided)
 * @param {Object} options.initialState - Initial state for store (optional)
 * @returns {Object} Render result and store
 */
export function renderWithRedux(ui, { store, initialState, ...renderOptions } = {}) {
	const testStore = store || createTestStore(initialState);
	// Wrapper component provides Redux store
	const Wrapper = ({ children }) => <Provider store={testStore}>{children}</Provider>;
	const result = render(ui, { wrapper: Wrapper, ...renderOptions });
	return { ...result, store: testStore };
}

/**
 * Render a component with React Router
 *
 * Use this when your component uses routing hooks (useNavigate, useLocation, etc.).
 * The component will have access to React Router context.
 *
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @returns {Object} Render result
 */
export function renderWithRouter(ui, options = {}) {
	// Wrapper component provides BrowserRouter
	const Wrapper = ({ children }) => <BrowserRouter>{children}</BrowserRouter>;
	return render(ui, { wrapper: Wrapper, ...options });
}

/**
 * Render a component with Redux Provider and React Router
 *
 * Use this when your component needs both Redux and Router.
 * Combines renderWithRedux and renderWithRouter.
 *
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @param {Object} options.store - Redux store (optional)
 * @param {Object} options.initialState - Initial state for store (optional)
 * @returns {Object} Render result and store
 */
export function renderWithReduxAndRouter(ui, { store, initialState, ...renderOptions } = {}) {
	const testStore = store || createTestStore(initialState);
	// Wrapper provides both Redux and Router
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
 *
 * Use this when your component uses the useAuth hook.
 * The component will have access to authentication context.
 *
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @returns {Object} Render result
 */
export function renderWithAuth(ui, options = {}) {
	// Wrapper provides Auth context
	const Wrapper = ({ children }) => <AuthProvider>{children}</AuthProvider>;
	return render(ui, { wrapper: Wrapper, ...options });
}

/**
 * Render a component with all providers (Redux, Router, Auth)
 *
 * Use this when your component needs everything.
 * This is the most common utility for testing real-world components.
 *
 * @param {React.ReactElement} ui - Component to render
 * @param {Object} options - Render options
 * @param {Object} options.store - Redux store (optional)
 * @param {Object} options.initialState - Initial state for store (optional)
 * @returns {Object} Render result and store
 */
export function renderWithAllProviders(ui, { store, initialState, ...renderOptions } = {}) {
	const testStore = store || createTestStore(initialState);
	// Wrapper provides all three: Redux, Router, and Auth
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
