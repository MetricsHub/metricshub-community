import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../hooks/use-auth";
import { paths } from "../paths";
import { useNavigate } from "react-router-dom";

/**
 * AuthGuard component to protect routes
 *
 * @param {*} param0  { children }
 * @returns children if authenticated, otherwise redirects to login
 */
export const AuthGuard = ({ children }) => {
	const { isAuthenticated } = useAuth();
	const [checked, setChecked] = useState(false);
	const navigate = useNavigate();

	const check = useCallback(() => {
		if (!isAuthenticated) {
			const searchParams = new URLSearchParams({
				returnTo: globalThis.location.href,
			}).toString();
			const href = paths.login + `?${searchParams}`;
			navigate(href, { replace: true });
		} else {
			setChecked(true);
		}
	}, [isAuthenticated, navigate]);

	// Only check on mount
	useEffect(() => {
		check();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	if (!checked) {
		return null;
	}

	// User is authenticated
	return <>{children}</>;
};
