import * as React from "react";
import { Typography } from "@mui/material";
import { withLayout } from "../hocs/with-layout";
import { DashboardLayout } from "../layouts/dashboard/layout";

/**
 * Home page component
 * @returns JSX.Element
 */
// eslint-disable-next-line react-refresh/only-export-components
const HomePage = () => {
	return <Typography variant="h4">Dashboard Overview</Typography>;
};

// Wrap HomePage with DashboardLayout
// eslint-disable-next-line react-refresh/only-export-components
export default withLayout(DashboardLayout)(HomePage);
