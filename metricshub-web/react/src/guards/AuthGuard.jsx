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
	 * capture the first URL that required authentication
	 */
	const returnToRef = React.useRef(null);
	if (!returnToRef.current) {
		returnToRef.current = `${location.pathname}${location.search}${location.hash}`;
	}

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
			const returnTo = returnToRef.current || "/";
			const searchParams = new URLSearchParams({ returnTo }).toString();
			navigate(`${paths.login}?${searchParams}`, { replace: true });
			setChecked(false);
			return;
		}

		// authenticated, allow render
		setChecked(true);
	}, [isAuthenticated, isInitialized, navigate, location.pathname]);

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
