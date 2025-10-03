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
