import * as React from "react";
import { useLocation, Link as RouterLink, matchPath } from "react-router-dom";
import { Breadcrumbs, Link, Typography, Box } from "@mui/material";
import NavigateNextIcon from "@mui/icons-material/NavigateNext";
import { paths } from "../../paths";

/**
 * Configuration for Explorer routes to generate breadcrumbs.
 * @type {Array<{pattern: string, getBreadcrumbs: (params: Record<string, string>) => Array<{label: string, to: string|null}>}>}
 */
const EXPLORER_ROUTES = [
	{
		pattern:
			"/explorer/resource-groups/:group/resources/:resource/connectors/:connectorId/monitors/:monitorType",
		getBreadcrumbs: (params) => {
			const group = decodeURIComponent(params.group);
			const resource = decodeURIComponent(params.resource);
			const monitorType = decodeURIComponent(params.monitorType);
			return [
				{ label: group, to: paths.explorerResourceGroup(group) },
				{ label: resource, to: paths.explorerResource(group, resource) },
				{ label: monitorType, to: null },
			];
		},
	},
	{
		pattern: "/explorer/resources/:resource/connectors/:connectorId/monitors/:monitorType",
		getBreadcrumbs: (params) => {
			const resource = decodeURIComponent(params.resource);
			const monitorType = decodeURIComponent(params.monitorType);
			return [
				{ label: resource, to: paths.explorerResource(null, resource) },
				{ label: monitorType, to: null },
			];
		},
	},
	{
		pattern: "/explorer/resource-groups/:group/resources/:resource",
		getBreadcrumbs: (params) => {
			const group = decodeURIComponent(params.group);
			const resource = decodeURIComponent(params.resource);
			return [
				{ label: group, to: paths.explorerResourceGroup(group) },
				{ label: resource, to: null },
			];
		},
	},
	{
		pattern: "/explorer/resource-groups/:name",
		getBreadcrumbs: (params) => {
			const name = decodeURIComponent(params.name);
			return [{ label: name, to: null }];
		},
	},
	{
		pattern: "/explorer/resources/:resource",
		getBreadcrumbs: (params) => {
			const resource = decodeURIComponent(params.resource);
			return [{ label: resource, to: null }];
		},
	},
];

/**
 * Component to render breadcrumbs based on the current location.
 * Currently supports Explorer routes.
 *
 * @param {{ sx?: import("@mui/material").SxProps, action?: React.ReactNode }} props
 * @returns {React.ReactElement|null}
 */
const AppBreadcrumbs = ({ sx, action }) => {
	const location = useLocation();
	const currentPath = location.pathname;

	const crumbs = React.useMemo(() => {
		const items = [];

		if (currentPath.startsWith("/explorer")) {
			items.push({ label: "Explorer", to: paths.explorerWelcome });

			for (const route of EXPLORER_ROUTES) {
				const match = matchPath({ path: route.pattern, end: true }, currentPath);
				if (match && match.params) {
					items.push(...route.getBreadcrumbs(match.params));
					break; // Stop after first match
				}
			}
		}

		return items;
	}, [currentPath]);

	if (crumbs.length <= 1) return null;

	return (
		<Box
			sx={{
				px: 2,
				py: 1,
				display: "flex",
				justifyContent: "space-between",
				alignItems: "center",
				...sx,
			}}
		>
			<Breadcrumbs separator={<NavigateNextIcon fontSize="small" />} aria-label="breadcrumb">
				{crumbs.map((crumb, index) => {
					const isLast = index === crumbs.length - 1;
					return isLast ? (
						<Typography key={crumb.label} color="text.primary" variant="body2">
							{crumb.label}
						</Typography>
					) : (
						<Link
							key={`${crumb.label}-${index}`}
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
			{action && <Box>{action}</Box>}
		</Box>
	);
};

export default React.memo(AppBreadcrumbs);
