import * as React from "react";
import { useLocation, Link as RouterLink, matchPath } from "react-router-dom";
import { Breadcrumbs, Link, Typography, Box } from "@mui/material";
import NavigateNextIcon from "@mui/icons-material/NavigateNext";
import { paths } from "../../paths";

const AppBreadcrumbs = ({ sx }) => {
	const location = useLocation();
	const currentPath = location.pathname;

	const crumbs = React.useMemo(() => {
		const items = [];

		if (currentPath.startsWith("/explorer")) {
			items.push({ label: "Explorer", to: paths.explorerWelcome });

			const resourceWithGroupMatch = matchPath(
				{ path: "/explorer/resource-groups/:group/resources/:resource", end: true },
				currentPath,
			);
			const groupMatch = matchPath(
				{ path: "/explorer/resource-groups/:name", end: true },
				currentPath,
			);
			const resourceNoGroupMatch = matchPath(
				{ path: "/explorer/resources/:resource", end: true },
				currentPath,
			);

			if (resourceWithGroupMatch) {
				const { group, resource } = resourceWithGroupMatch.params;
				const decodedGroup = decodeURIComponent(group);
				const decodedResource = decodeURIComponent(resource);
				items.push({
					label: decodedGroup,
					to: paths.explorerResourceGroup(decodedGroup),
				});
				items.push({ label: decodedResource, to: null });
			} else if (groupMatch) {
				const { name } = groupMatch.params;
				items.push({ label: decodeURIComponent(name), to: null });
			} else if (resourceNoGroupMatch) {
				const { resource } = resourceNoGroupMatch.params;
				items.push({ label: decodeURIComponent(resource), to: null });
			}
		}

		return items;
	}, [currentPath]);

	if (crumbs.length <= 1) return null;

	return (
		<Box sx={{ px: 2, py: 1, ...sx }}>
			<Breadcrumbs separator={<NavigateNextIcon fontSize="small" />} aria-label="breadcrumb">
				{crumbs.map((crumb, index) => {
					const isLast = index === crumbs.length - 1;
					return isLast ? (
						<Typography key={crumb.label} color="text.primary" variant="body2">
							{crumb.label}
						</Typography>
					) : (
						<Link
							key={crumb.label}
							underline="hover"
							color="inherit"
							component={RouterLink}
							to={crumb.to}
							variant="body2"
						>
							{crumb.label}
						</Link>
					);
				})}
			</Breadcrumbs>
		</Box>
	);
};

export default AppBreadcrumbs;
