import * as React from "react";
import { authApi } from "../api/auth";
import { Issuer } from "../utils/auth";

/**
 * Action types for the auth reducer.
 */
var ActionType;
(function (ActionType) {
	ActionType["INITIALIZE"] = "INITIALIZE";
	ActionType["SIGN_IN"] = "SIGN_IN";
	ActionType["SIGN_OUT"] = "SIGN_OUT";
})(ActionType || (ActionType = {}));

/**
 * Initial state for the auth context.
 */
const initialState = {
	isAuthenticated: false,
	isInitialized: false,
	user: null,
};

/**
 * Reducer handlers for the auth context.
 */
const handlers = {
	INITIALIZE: (state, action) => {
		const { isAuthenticated, user } = action.payload;

		return {
			...state,
			isAuthenticated,
			isInitialized: true,
			user,
		};
	},
	SIGN_IN: (state, action) => {
		const { user } = action.payload;

		return {
			...state,
			isAuthenticated: true,
			user,
		};
	},
	SIGN_OUT: (state) => ({
		...state,
		isAuthenticated: false,
		user: null,
	}),
};

/**
 * Reducer function for the auth context.
 * @param {*} state  The current state
 * @param {*} action The action to process
 * @returns The new state
 */
const reducer = (state, action) =>
	handlers[action.type] ? handlers[action.type](state, action) : state;

/**
 * Authentication context.
 */
export const AuthContext = React.createContext({
	...initialState,
	issuer: Issuer.JWT,
	signIn: async () => {},
	signOut: async () => {},
});

/**
 * Auth provider component.
 * @param {*} param0 The component props
 * @returns The auth provider component
 */
export const AuthProvider = ({ children }) => {
	const [state, dispatch] = React.useReducer(reducer, initialState);

	// On load: Fetch the user (access cookie or refresh via interceptor)
	const initialize = React.useCallback(async () => {
		try {
			const user = await authApi.me();
			dispatch({
				type: ActionType.INITIALIZE,
				payload: {
					isAuthenticated: true,
					user,
				},
			});
		} catch {
			dispatch({
				type: ActionType.INITIALIZE,
				payload: {
					isAuthenticated: false,
					user: null,
				},
			});
		}
	}, [dispatch]);

	/**
	 * Initialize auth state on component mount.
	 */
	React.useEffect(() => {
		initialize();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	/**
	 * Listen for forced logout (e.g. from axios interceptor)
	 * and sign out the user.
	 */
	React.useEffect(() => {
		const onForcedLogout = () => dispatch({ type: ActionType.SIGN_OUT });
		window.addEventListener("auth:logout", onForcedLogout);
		return () => window.removeEventListener("auth:logout", onForcedLogout);
	}, [dispatch]);

	/**
	 * Sign in function.
	 * @param {string} username The username
	 * @param {string} password The password
	 */
	const signIn = React.useCallback(
		async (username, password) => {
			// Backend sets HttpOnly cookies; body token (if any) is ignored
			await authApi.signIn({ username, password });
			const user = await authApi.me();
			dispatch({
				type: ActionType.SIGN_IN,
				payload: { user },
			});
		},
		[dispatch],
	);

	/**
	 * Sign out function.
	 * @returns {Promise<void>}
	 */
	const signOut = React.useCallback(async () => {
		try {
			await authApi.signOut();
		} catch {
			// ignore network errors on logout
		}
		dispatch({ type: ActionType.SIGN_OUT });
	}, [dispatch]);

	return (
		<AuthContext.Provider
			value={{
				...state,
				issuer: Issuer.JWT,
				signIn,
				signOut,
			}}
		>
			{children}
		</AuthContext.Provider>
	);
};

export const AuthConsumer = AuthContext.Consumer;
