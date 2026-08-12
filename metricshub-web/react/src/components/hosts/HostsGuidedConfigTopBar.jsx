import * as React from "react";
import { Box } from "@mui/material";
import AppBreadcrumbs from "../common/AppBreadcrumbs";
import { HOST_CONFIG_BREADCRUMB_ROW_MIN_HEIGHT } from "./host-config-form-layout";
import HostsEntityTitle from "./HostsEntityTitle";

/**
 * Top band for guided configuration browse mode: title or breadcrumbs (New group/resource live in the tree sidebar).
 *
 * @param {object} props
 * @param {boolean} props.isResourceGroupsView - true when on the default resource-groups landing page
 * @param {string} [props.breadcrumbPathname]
 */
const HostsGuidedConfigTopBar = ({ isResourceGroupsView, breadcrumbPathname }) => (
	<Box
		sx={{
			boxSizing: "border-box",
			px: 2,
			py: 1,
			minHeight: HOST_CONFIG_BREADCRUMB_ROW_MIN_HEIGHT,
			display: "flex",
			alignItems: isResourceGroupsView ? "flex-start" : "center",
			flexShrink: 0,
		}}
	>
		<Box sx={{ flex: 1, minWidth: 0 }}>
			{isResourceGroupsView ? (
				<HostsEntityTitle iconType="resource-group" compact typographyProps={{ sx: { mb: 0 } }}>
					Resource Groups
				</HostsEntityTitle>
			) : (
				<AppBreadcrumbs embedded pathname={breadcrumbPathname} />
			)}
		</Box>
	</Box>
);

export default React.memo(HostsGuidedConfigTopBar);
