// src/pages/home.jsx
import React, { useEffect, useState } from "react";
import { Box, Typography, CircularProgress, Alert } from "@mui/material";
import { withLayout } from "../hocs/with-layout";
import { DashboardLayout } from "../layouts/dashboard/layout";
import { useMounted } from "../hooks/use-mounted";
import { statusApi } from "../api/auth";

const HomePage = () => {
	const mounted = useMounted();
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState(null);

	// Optional: still ping backend to show loading/error; remove this whole effect if not needed
	useEffect(() => {
		(async () => {
			try {
				await statusApi.getStatus();
			} catch (err) {
				if (mounted()) setError(err?.message || "Failed to load status");
			} finally {
				if (mounted()) setLoading(false);
			}
		})();
	}, [mounted]);

	return (
		<Box sx={{ p: 3 }}>
			<Typography variant="h4" gutterBottom>
				Dashboard Overview
			</Typography>

			{loading ? (
				<Box
					sx={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 200 }}
				>
					<CircularProgress />
				</Box>
			) : error ? (
				<Alert severity="error">{error}</Alert>
			) : null}
		</Box>
	);
};

const HomePageWithLayout = withLayout(DashboardLayout)(HomePage);
HomePageWithLayout.displayName = "HomePageWithLayout";

export default HomePageWithLayout;
