import * as React from "react";
import { Typography } from "@mui/material";
import NodeTypeIcons from "../explorer/tree/icons/NodeTypeIcons";

/** Matches {@link EntityHeader} title row (Explorer resource / resource-group pages). */
const entityTitleSx = {
	display: "flex",
	alignItems: "center",
	gap: 0.5,
	minWidth: 0,
	transition: "color 0.4s ease",
};

/**
 * Page title with the same node icon and sizing as Explorer {@link EntityHeader}.
 *
 * @param {object} props
 * @param {string} props.iconType agent | resource-group | resource | multi-host-resource
 * @param {React.ReactNode} props.children title text
 * @param {boolean} [props.compact] when true, no bottom gutter (title on same row as chips/actions)
 * @param {import("@mui/material").TypographyProps} [props.typographyProps]
 */
const HostsEntityTitle = ({ iconType, children, compact = false, typographyProps = {} }) => {
	const { sx, ...restTypography } = typographyProps;
	return (
		<Typography
			variant="h4"
			gutterBottom={!compact}
			sx={{ ...entityTitleSx, ...sx }}
			{...restTypography}
		>
			{iconType ? <NodeTypeIcons type={iconType} fontSize="large" /> : null}
			{children}
		</Typography>
	);
};

export default React.memo(HostsEntityTitle);
