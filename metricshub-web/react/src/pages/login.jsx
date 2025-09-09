import * as React from "react";
import { Button, Stack, TextField } from "@mui/material";
import { useAuth } from "../hooks/use-auth";
import { useNavigate, useSearchParams } from "react-router-dom";
import { paths } from "../paths";
import { withLayout } from "../hocs/with-layout";
import { AuthLayout } from "../layouts/auth/layout";

/**
 * Login page component
 * @returns JSX.Element
 */
// eslint-disable-next-line react-refresh/only-export-components
const LoginPage = () => {
	const { signIn } = useAuth();
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();

	const handleSubmit = async (e) => {
		e.preventDefault();
		// grab form values
		const data = new FormData(e.currentTarget);
		const username = data.get("username");
		const password = data.get("password");

		await signIn(username, password);

		const returnTo = searchParams.get("returnTo");
		if (returnTo) {
			globalThis.location.assign(returnTo);
		} else {
			navigate(paths.index, { replace: true });
		}
	};

	return (
		<form onSubmit={handleSubmit}>
			<Stack spacing={2}>
				<TextField name="username" label="Username" fullWidth />
				<TextField name="password" label="Password" type="password" fullWidth />
				<Button type="submit" variant="contained">
					Sign in
				</Button>
			</Stack>
		</form>
	);
};

// Wrap LoginPage with AuthLayout
// eslint-disable-next-line react-refresh/only-export-components
export default withLayout(AuthLayout)(LoginPage);
