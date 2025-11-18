import * as React from "react";
import { useAuth } from "../hooks/use-auth";
import { paths } from "../paths";
import { useNavigate, useLocation } from "react-router-dom";

/**
 * Auth guard component that protects routes that require authentication.
 *
 * @param {*} param0  The component props
 * @returns The auth guard component
 */
export const AuthGuard = ({ children }) => {
	const { isAuthenticated, isInitialized } = useAuth();
	const [checked, setChecked] = React.useState(false);
	const navigate = useNavigate();
	const location = useLocation();

	/**
	 * Check authentication status and redirect if necessary.
	 */
	const check = React.useCallback(() => {
		if (!isInitialized) return; // wait for initialize to complete once
		// if we're already on the login page, don't redirect again
		if (location.pathname === paths.login) {
			setChecked(true);
			return;
		}

		// Not authenticated, redirect to login
		if (!isAuthenticated) {
			// Use current location for returnTo to ensure we capture where the user was when they logged out
			const currentPath = `${location.pathname}${location.search}${location.hash}`;
			let searchParams = "";

			// Never set returnTo to "/" or login page
			if (currentPath !== "/" && currentPath !== paths.login) {
				searchParams = new URLSearchParams({
					returnTo: currentPath,
				}).toString();
			}

			navigate(`${paths.login}${searchParams ? `?${searchParams}` : ""}`, { replace: true });
			setChecked(false);
			return;
		}

		// authenticated, allow render
		setChecked(true);
	}, [isAuthenticated, isInitialized, navigate, location]);

	/**
	 * Run check on mount and when auth state changes.
	 */
	React.useEffect(() => {
		check();
	}, [check]);

	if (!checked) {
		return null;
	}

	return <>{children}</>;
};
