import * as React from "react";
import { useLocation, Link as RouterLink, matchPath } from "react-router-dom";
import { Breadcrumbs, Link, Typography, Box } from "@mui/material";
import NavigateNextIcon from "@mui/icons-material/NavigateNext";
import { paths } from "../../paths";
import NodeTypeIcons from "../explorer/tree/icons/NodeTypeIcons";
import MonitorTypeIcon from "../explorer/views/monitors/icons/MonitorTypeIcon";
import { NO_RESOURCE_GROUP } from "../hosts/hosts-labels";

/**
 * Configuration for Explorer routes to generate breadcrumbs.
 * @type {Array<{pattern: string, getBreadcrumbs: (params: Record<string, string>) => Array<{label: string, to: string|null, iconType?: string, monitorType?: string}>}>}
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
				{ label: group, to: paths.explorerResourceGroup(group), iconType: "resource-group" },
				{ label: resource, to: paths.explorerResource(group, resource), iconType: "resource" },
				{ label: monitorType, to: null, monitorType },
			];
		},
	},
	{
		pattern: "/explorer/resources/:resource/connectors/:connectorId/monitors/:monitorType",
		getBreadcrumbs: (params) => {
			const resource = decodeURIComponent(params.resource);
			const monitorType = decodeURIComponent(params.monitorType);
			return [
				{ label: resource, to: paths.explorerResource(null, resource), iconType: "resource" },
				{ label: monitorType, to: null, monitorType },
			];
		},
	},
	{
		pattern: "/explorer/resource-groups/:group/resources/:resource/connectors/:connectorId",
		getBreadcrumbs: (params) => {
			const group = decodeURIComponent(params.group);
			const resource = decodeURIComponent(params.resource);
			const connectorId = decodeURIComponent(params.connectorId);
			return [
				{ label: group, to: paths.explorerResourceGroup(group), iconType: "resource-group" },
				{ label: resource, to: paths.explorerResource(group, resource), iconType: "resource" },
				{ label: connectorId, to: null },
			];
		},
	},
	{
		pattern: "/explorer/resources/:resource/connectors/:connectorId",
		getBreadcrumbs: (params) => {
			const resource = decodeURIComponent(params.resource);
			const connectorId = decodeURIComponent(params.connectorId);
			return [
				{ label: resource, to: paths.explorerResource(null, resource), iconType: "resource" },
				{ label: connectorId, to: null },
			];
		},
	},
	{
		pattern: "/explorer/resource-groups/:group/resources/:resource",
		getBreadcrumbs: (params) => {
			const group = decodeURIComponent(params.group);
			const resource = decodeURIComponent(params.resource);
			return [
				{ label: group, to: paths.explorerResourceGroup(group), iconType: "resource-group" },
				{ label: resource, to: null, iconType: "resource" },
			];
		},
	},
	{
		pattern: "/explorer/resource-groups/:name",
		getBreadcrumbs: (params) => {
			const name = decodeURIComponent(params.name);
			return [{ label: name, to: null, iconType: "resource-group" }];
		},
	},
	{
		pattern: "/explorer/resources/:resource",
		getBreadcrumbs: (params) => {
			const resource = decodeURIComponent(params.resource);
			return [{ label: resource, to: null, iconType: "resource" }];
		},
	},
];

/**
 * Configuration for Guided Config (Hosts) routes to generate breadcrumbs.
 * @type {Array<{pattern: string, getBreadcrumbs: (params: Record<string, string>) => Array<{label: string, to: string|null, iconType?: string}>}>}
 */
const guidedConfigResourceGroupsCrumb = () => ({
	label: "Resource Groups",
	to: paths.hostsResourceGroups(),
	iconType: "agent",
});

const GUIDED_CONFIG_ROUTES = [
	{
		pattern: "/configuration/guided-config/resource-groups/:group/resources/:resource/edit",
		getBreadcrumbs: (params) => {
			const group = decodeURIComponent(params.group);
			const resource = decodeURIComponent(params.resource);
			return [
				guidedConfigResourceGroupsCrumb(),
				{ label: group, to: paths.hostsResourceGroup(group), iconType: "resource-group" },
				{ label: resource, to: paths.hostsGroupedResource(group, resource), iconType: "resource" },
				{ label: "Edit", to: null },
			];
		},
	},
	{
		pattern: "/configuration/guided-config/no-resource-group/resources/:resource/edit",
		getBreadcrumbs: (params) => {
			const resource = decodeURIComponent(params.resource);
			return [
				guidedConfigResourceGroupsCrumb(),
				{
					label: NO_RESOURCE_GROUP,
					to: paths.hostsStandaloneSection(),
					iconType: "resource-group",
				},
				{ label: resource, to: paths.hostsStandaloneResource(resource), iconType: "resource" },
				{ label: "Edit", to: null },
			];
		},
	},
	{
		pattern: "/configuration/guided-config/resource-groups/:group/resources/:resource",
		getBreadcrumbs: (params) => {
			const group = decodeURIComponent(params.group);
			const resource = decodeURIComponent(params.resource);
			return [
				guidedConfigResourceGroupsCrumb(),
				{ label: group, to: paths.hostsResourceGroup(group), iconType: "resource-group" },
				{ label: resource, to: null, iconType: "resource" },
			];
		},
	},
	{
		pattern: "/configuration/guided-config/no-resource-group/resources/:resource",
		getBreadcrumbs: (params) => {
			const resource = decodeURIComponent(params.resource);
			return [
				guidedConfigResourceGroupsCrumb(),
				{
					label: NO_RESOURCE_GROUP,
					to: paths.hostsStandaloneSection(),
					iconType: "resource-group",
				},
				{ label: resource, to: null, iconType: "resource" },
			];
		},
	},
	{
		pattern: "/configuration/guided-config/resource-groups",
		getBreadcrumbs: () => [guidedConfigResourceGroupsCrumb()],
	},
	{
		pattern: "/configuration/guided-config/resource-groups/new",
		getBreadcrumbs: () => [guidedConfigResourceGroupsCrumb()],
	},
	{
		pattern: "/configuration/guided-config/resource-groups/:name",
		getBreadcrumbs: (params) => {
			const name = decodeURIComponent(params.name);
			return [
				guidedConfigResourceGroupsCrumb(),
				{ label: name, to: null, iconType: "resource-group" },
			];
		},
	},
	{
		pattern: "/configuration/guided-config/no-resource-group",
		getBreadcrumbs: () => [
			guidedConfigResourceGroupsCrumb(),
			{ label: NO_RESOURCE_GROUP, to: null, iconType: "resource-group" },
		],
	},
];

/**
 * Component to render breadcrumbs based on the current location.
 * Currently supports Explorer routes.
 *
 * @param {{ sx?: import("@mui/material").SxProps, action?: React.ReactNode, pathname?: string, embedded?: boolean }} props
 * @returns {React.ReactElement|null}
 */
const AppBreadcrumbs = ({ sx, action, pathname, embedded = false }) => {
	const location = useLocation();
	const currentPath = pathname || location.pathname;

	const crumbs = React.useMemo(() => {
		const items = [];

		if (currentPath.startsWith("/explorer")) {
			items.push({ label: "Explorer", to: paths.explorerWelcome, iconType: "agent" });

			for (const route of EXPLORER_ROUTES) {
				const match = matchPath({ path: route.pattern, end: true }, currentPath);
				if (match && match.params) {
					items.push(...route.getBreadcrumbs(match.params));
					break; // Stop after first match
				}
			}
		} else if (currentPath.startsWith("/configuration/guided-config")) {
			for (const route of GUIDED_CONFIG_ROUTES) {
				const match = matchPath({ path: route.pattern, end: true }, currentPath);
				if (match && match.params) {
					items.push(...route.getBreadcrumbs(match.params));
					break;
				}
			}
		}

		return items;
	}, [currentPath]);

	if (crumbs.length === 0 && !action) {
		return null;
	}
	// Guided-config create pages render a single breadcrumb crumb for parent context.
	// Elsewhere a lone crumb (e.g. Explorer welcome) is still hidden.
	const allowSingleCrumb =
		crumbs.length === 1 && currentPath.startsWith("/configuration/guided-config");
	if (crumbs.length <= 1 && !action && !allowSingleCrumb) {
		return null;
	}

	const breadcrumbsEl = (
		<Breadcrumbs
			separator={<NavigateNextIcon fontSize="small" />}
			aria-label="breadcrumb"
			sx={{
				"& .MuiBreadcrumbs-separator": {
					transition: "color 0.4s ease",
				},
				"& .MuiBreadcrumbs-li": {
					transition: "color 0.4s ease",
				},
			}}
		>
			{crumbs.map((crumb, index) => {
				const isLast = index === crumbs.length - 1;
				const to = isLast ? null : crumb.to;
				const icon = crumb.monitorType ? (
					<MonitorTypeIcon type={crumb.monitorType} />
				) : crumb.iconType ? (
					<NodeTypeIcons type={crumb.iconType} />
				) : null;

				const content = (
					<Box
						component="span"
						sx={{
							display: "inline-flex",
							alignItems: "center",
							gap: 0.75,
							verticalAlign: "middle",
							transition: "color 0.4s ease",
						}}
					>
						{icon && (
							<Box component="span" sx={{ display: "flex", alignItems: "center" }}>
								{icon}
							</Box>
						)}
						<Box component="span" sx={{ mt: "1px", transition: "color 0.4s ease" }}>
							{crumb.label}
						</Box>
					</Box>
				);

				return isLast && !to ? (
					<Typography
						key={crumb.label}
						color="text.primary"
						variant="body2"
						sx={{ transition: "color 0.4s ease" }}
					>
						{content}
					</Typography>
				) : (
					<Link
						key={`${crumb.label}-${index}`}
						underline="hover"
						color="text.secondary"
						component={RouterLink}
						to={to}
						variant="body2"
						sx={{ transition: "color 0.4s ease" }}
					>
						{content}
					</Link>
				);
			})}
		</Breadcrumbs>
	);

	if (embedded) {
		return breadcrumbsEl;
	}

	return (
		<Box
			sx={{
				px: 2,
				py: 1,
				display: "flex",
				justifyContent: "space-between",
				alignItems: "center",
				gap: 1,
				...sx,
			}}
		>
			{breadcrumbsEl}
			{action && (
				<Box sx={{ flexShrink: 0, ml: "auto", display: "flex", alignItems: "flex-start" }}>
					{action}
				</Box>
			)}
		</Box>
	);
};

export default React.memo(AppBreadcrumbs);
