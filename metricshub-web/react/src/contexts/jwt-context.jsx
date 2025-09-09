import { createContext, useCallback, useEffect, useReducer } from "react";
import { authApi } from "../api/auth";
import { Issuer } from "../utils/auth";

const STORAGE_KEY = "jwt";

var ActionType;
(function (ActionType) {
	ActionType["INITIALIZE"] = "INITIALIZE";
	ActionType["SIGN_IN"] = "SIGN_IN";
	ActionType["SIGN_OUT"] = "SIGN_OUT";
})(ActionType || (ActionType = {}));

const initialState = {
	isAuthenticated: false,
	isInitialized: false,
	user: null,
};

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

const reducer = (state, action) => (handlers[action.type] ? handlers[action.type](state, action) : state);

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext({
	...initialState,
	issuer: Issuer.JWT,
	signIn: () => Promise.resolve(),
	signOut: () => Promise.resolve(),
});

/**
 * Component to provide authentication context to its children
 *
 * @param {*} props  { children }
 * @returns  { AuthContext.Provider }
 */
export const AuthProvider = (props) => {
	const { children } = props;
	const [state, dispatch] = useReducer(reducer, initialState);

	/**
	 * Initialize authentication state by checking for existing JWT in local storage
	 * and fetching user information if JWT is present.
	 */
	const initialize = useCallback(async () => {
		try {
			const jwt = globalThis.localStorage.getItem(STORAGE_KEY);

			if (jwt) {
				const user = await authApi.me();

				dispatch({
					type: ActionType.INITIALIZE,
					payload: {
						isAuthenticated: true,
						isConfirmed: user.confirmed,
						user,
					},
				});
			} else {
				dispatch({
					type: ActionType.INITIALIZE,
					payload: {
						isAuthenticated: false,
						isConfirmed: false,
						user: null,
					},
				});
			}
		} catch (err) {
			console.error(err);
			dispatch({
				type: ActionType.INITIALIZE,
				payload: {
					isAuthenticated: false,
					isConfirmed: false,
					user: null,
				},
			});
		}
	}, [dispatch]);

	/**
	 * Run the initialize function once when the component is mounted to set up the auth state.
	 */
	useEffect(
		() => {
			initialize();
		},
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	/**
	 * Sign in a user with the provided username and password.
	 * On successful sign-in, store the JWT in local storage and update the auth state.
	 */
	const signIn = useCallback(
		async (username, password) => {
			const { jwt } = await authApi.signIn({ username, password });

			localStorage.setItem(STORAGE_KEY, jwt);

			const user = await authApi.me();

			dispatch({
				type: ActionType.SIGN_IN,
				payload: {
					user,
				},
			});
		},
		[dispatch],
	);

	/**
	 * Sign out the current user by removing the JWT from local storage and updating the auth state.
	 */
	const signOut = useCallback(async () => {
		localStorage.removeItem(STORAGE_KEY);
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
