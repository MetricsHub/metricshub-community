import * as React from "react";
import { Button, Stack, TextField, Box, FormHelperText } from "@mui/material";
import { useAuth } from "../hooks/use-auth";
import { useNavigate, useSearchParams } from "react-router-dom";
import { paths } from "../paths";
import { withLayout } from "../hocs/with-layout";
import { AuthLayout } from "../layouts/auth/layout";
import * as Yup from "yup";
import { useFormik } from "formik";
import { useMounted } from "../hooks/use-mounted";

const initialValues = {
	username: "",
	password: "",
	submit: null,
};

const validationSchema = Yup.object({
	username: Yup.string().required("Username is required"),
	password: Yup.string().required("Password is required"),
});

// eslint-disable-next-line react-refresh/only-export-components
const LoginPage = () => {
	const { signIn } = useAuth();
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();

	const formik = useFormik({
		initialValues,
		validationSchema,
		onSubmit: async (values, helpers) => {
			try {
				await signIn(values.username, values.password);

				const returnTo = searchParams.get("returnTo");
				if (returnTo) {
					globalThis.location.assign(returnTo);
				} else {
					// paths.index should be "/" — make sure this exists
					navigate(paths.index ?? "/", { replace: true });
				}
			} catch (err) {
				helpers.setStatus({ success: false });
				helpers.setErrors({ submit: err?.message || "Sign-in failed" });
			} finally {
				// Always re-enable the button
				helpers.setSubmitting(false);
			}
		},
	});

	return (
		<Box
			component="form"
			onSubmit={formik.handleSubmit}
			noValidate
			sx={{ mx: "auto", width: "100%", maxWidth: 640 }}
		>
			<Stack spacing={3}>
				<TextField
					autoFocus
					fullWidth
					label="Username"
					name="username"
					type="text"
					value={formik.values.username}
					onChange={formik.handleChange}
					onBlur={formik.handleBlur}
					error={Boolean(formik.touched.username && formik.errors.username)}
					helperText={formik.touched.username && formik.errors.username}
					autoComplete="username"
				/>

				<TextField
					fullWidth
					label="Password"
					name="password"
					type="password"
					value={formik.values.password}
					onChange={formik.handleChange}
					onBlur={formik.handleBlur}
					error={Boolean(formik.touched.password && formik.errors.password)}
					helperText={formik.touched.password && formik.errors.password}
					autoComplete="current-password"
				/>

				{formik.errors.submit && (
					<FormHelperText error sx={{ mt: -1 }}>
						{formik.errors.submit}
					</FormHelperText>
				)}

				<Button type="submit" variant="contained" size="large" disabled={formik.isSubmitting}>
					Sign in
				</Button>
			</Stack>
		</Box>
	);
};

// Wrap LoginPage with AuthLayout
// eslint-disable-next-line react-refresh/only-export-components
export default withLayout(AuthLayout)(LoginPage);
