import * as React from "react";
import {
	Button,
	Stack,
	TextField,
	Box,
	Alert,
	Container,
	CssBaseline,
	Link,
	Paper,
	Typography,
} from "@mui/material";
import LoginRounded from "@mui/icons-material/LoginRounded";
import MenuBookOutlinedIcon from "@mui/icons-material/MenuBookOutlined";
import { useTheme } from "@mui/material/styles";
import { useAuth } from "../hooks/use-auth";
import { useNavigate, useSearchParams } from "react-router-dom";
import { paths } from "../paths";
import * as Yup from "yup";
import { useFormik } from "formik";
import { authApi } from "../api/auth";
import LoginBackground from "../components/common/LoginBackground";
import logoDark from "../assets/logo-dark.svg";
import logoLight from "../assets/logo-light.svg";

/**
 * Authentication layout component.
 *
 * @param {object} props children wrapped by the layout.
 * @returns {JSX.Element} The AuthLayout component.
 */
const AuthLayoutComponent = (props) => {
	const { children, agentHostname } = props;
	const theme = useTheme();
	const logo = theme.palette.mode === "dark" ? logoDark : logoLight;

	return (
		<React.Fragment>
			<CssBaseline />
			<LoginBackground />
			<Box
				sx={{
					minHeight: "100vh",
					display: "flex",
					alignItems: "center",
					justifyContent: "center",
					position: "relative",
					zIndex: 1,
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
							justifyContent: "center",
							minHeight: 400,
							backgroundColor:
								theme.palette.mode === "dark"
									? theme.palette.background.paperDarker
									: theme.palette.background.paper,
						}}
					>
						<Box
							sx={{
								display: "flex",
								justifyContent: "space-between",
								alignItems: "flex-start",
								mb: 2,
								width: "100%",
							}}
						>
							<Box>
								<Typography variant="h5" component="h1" fontWeight={600}>
									Connect to MetricsHub
								</Typography>
								{agentHostname && (
									<Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
										On{" "}
										<Box component="span" sx={{ fontWeight: 600 }}>
											{agentHostname}
										</Box>
									</Typography>
								)}
							</Box>
							<Box
								component="img"
								src={logo}
								alt="MetricsHub"
								sx={{ width: 86, height: "auto", mt: -3 }}
							/>
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
 * Authentication layout component memoized.
 *
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
	const [agentHostname, setAgentHostname] = React.useState("");

	React.useEffect(() => {
		authApi
			.getAgentHostname()
			.then(setAgentHostname)
			.catch((error) => {
				console.error("Failed to load agent hostname", error);
			});
	}, []);

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
		<AuthLayout agentHostname={agentHostname}>
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

					<Button
						type="submit"
						variant="contained"
						size="large"
						fullWidth
						disabled={formik.isSubmitting}
						startIcon={<LoginRounded />}
						sx={{ borderRadius: 1 }}
					>
						Sign in
					</Button>

					<Link
						href="https://metricshub.com/docs/latest/operating-web-ui"
						target="_blank"
						rel="noopener noreferrer"
						underline="always"
						variant="body2"
						aria-label="Getting Started (opens in a new tab)"
						sx={{ display: "inline-flex", alignItems: "center", gap: 0.5, width: "fit-content" }}
					>
						<MenuBookOutlinedIcon fontSize="small" sx={{ mb: 0.4 }} />
						<Typography variant="body1">Getting Started</Typography>
					</Link>
				</Stack>
			</Box>
		</AuthLayout>
	);
};

// Wrap LoginPage with AuthLayout
export default LoginPage;
