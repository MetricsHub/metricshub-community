import * as React from "react";
import { Button, Stack, TextField, Box, Alert } from "@mui/material";
import { useAuth } from "../hooks/use-auth";
import { useNavigate, useSearchParams } from "react-router-dom";
import { paths } from "../paths";
import { withLayout } from "../hocs/with-layout";
import { AuthLayout } from "../layouts/auth/layout";
import * as Yup from "yup";
import { useFormik } from "formik";

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
					navigate(paths.index ?? "/", { replace: true });
				}
			} catch (err) {
				console.error(err);
				helpers.setStatus({ success: false });
				helpers.setErrors({
					submit: "Sign-in failed: " + err.response?.data?.message || "Unknown error",
				});
			} finally {
				// Always re-enable the button
				helpers.setSubmitting(false);
			}
		},
	});

	return (
		<Box sx={{ position: "relative", width: "100%", height: "100%" }}>
			{/* ADDED: top-right label */}
			<Box
				sx={{
					position: "absolute",
					top: 16,
					right: 16,
					fontSize: "0.9rem",
					fontWeight: "bold",
					color: "text.secondary",
				}}
			>
				Lancelot&apos;s version
			</Box>

			{/* Existing form */}
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

					{formik.errors.submit && <Alert severity="error">{formik.errors.submit}</Alert>}

					<Button type="submit" variant="contained" size="large" disabled={formik.isSubmitting}>
						Sign in
					</Button>
				</Stack>
			</Box>
		</Box>
	);
};

// Wrap LoginPage with AuthLayout
// eslint-disable-next-line react-refresh/only-export-components
export default withLayout(AuthLayout)(LoginPage);
