import { matchPath } from "react-router-dom";
import { paths } from "../paths";

/**
 * Configuration for Explorer routes to generate breadcrumbs.
 * This is duplicated/extracted from AppBreadcrumbs.jsx to be used in search results.
 * @type {Array<{pattern: string, getBreadcrumbs: (params: Object) => Array<Object>}>}
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
 * Gets breadcrumbs for a given path.
 *
 * @param {string} path - The path to get breadcrumbs for.
 * @returns {Array<{label: string, to: string|null, iconType?: string, monitorType?: string}>}
 */
export const getBreadcrumbsForPath = (path) => {
	const items = [];

	if (path.startsWith("/explorer")) {
		for (const route of EXPLORER_ROUTES) {
			const match = matchPath({ path: route.pattern, end: true }, path);
			if (match && match.params) {
				items.push(...route.getBreadcrumbs(match.params));
				break;
			}
		}
	}

	return items;
};
/**
 * Gets the joined breadcrumb text for a given path.
 *
 * @param {string} path - The path to get breadcrumbs for.
 * @returns {string} The breadcrumb string (e.g., "Explorer > Group > Resource").
 */
export const getBreadcrumbText = (path) => {
	const crumbs = getBreadcrumbsForPath(path);
	return crumbs.map((c) => c.label).join(" > ");
};
