import * as React from "react";
import { Box, Container, CssBaseline, Paper, Typography } from "@mui/material";

export const AuthLayout = (props) => {
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
					bgcolor: (theme) => (theme.palette.mode === "light" ? "grey.100" : "background.default"),
					px: 2,
				}}
			>
				<Container maxWidth="sm" disableGutters>
					<Paper elevation={3} sx={{ p: 4, borderRadius: 3 }}>
						<Box sx={{ mb: 2, textAlign: "center" }}>
							<Typography variant="h5" component="h1" fontWeight={600}>
								MetricsHub • Sign in
							</Typography>
						</Box>
						{children}
					</Paper>
				</Container>
			</Box>
		</React.Fragment>
	);
};
