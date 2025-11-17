import * as React from "react";
import {
	Button,
	Stack,
	TextField,
	Box,
	Alert,
	Container,
	CssBaseline,
	Paper,
	Typography,
} from "@mui/material";
import { useAuth } from "../hooks/use-auth";
import { useNavigate, useSearchParams } from "react-router-dom";
import { paths } from "../paths";
import * as Yup from "yup";
import { useFormik } from "formik";

/**
 * Authentication layout component.
 *
 * @param {object} props children wrapped by the layout.
 * @returns
 */
const AuthLayoutComponent = (props) => {
	const { children } = props;

	return (
		<React.Fragment>
			<CssBaseline />
			<Box
				sx={{
					minHeight: "100vh",
					display: "flex",
					alignItems: "center",
					justifyContent: "center",
					bgcolor: (theme) => theme.palette.background.default,
					px: 2,
				}}
			>
				<Container maxWidth="sm" disableGutters>
					<Paper
						elevation={3}
						sx={{
							p: 4,
							borderRadius: 3,
							display: "flex",
							flexDirection: "column",
							alignItems: "center",
							justifyContent: "center",
							minHeight: 400, // optional: ensures some vertical space
						}}
					>
						<Box sx={{ mb: 2, textAlign: "center" }}>
							<Typography variant="h5" component="h1" fontWeight={600}>
								Connect to MetricsHub
							</Typography>
						</Box>
						<Box
							sx={{
								width: "100%",
								display: "flex",
								alignItems: "center",
								justifyContent: "center",
							}}
						>
							{children}
						</Box>
					</Paper>
				</Container>
			</Box>
		</React.Fragment>
	);
};

/**
 * Authentication layout component.
 * @param {*} props Component props.
 * @returns {JSX.Element} The AuthLayout component.
 */
const AuthLayout = React.memo(AuthLayoutComponent);

/**
 * Initial form values.
 */
const initialValues = {
	username: "",
	password: "",
	submit: null,
};

/**
 * Validation schema for the login form.
 */
const validationSchema = Yup.object({
	username: Yup.string().required("Username is required"),
	password: Yup.string().required("Password is required"),
});

/**
 * Login page component.
 * @returns The login form.
 */
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
					navigate(paths.monitor ?? "/", { replace: true });
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
		<AuthLayout>
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
		</AuthLayout>
	);
};

// Wrap LoginPage with AuthLayout
export default LoginPage;
