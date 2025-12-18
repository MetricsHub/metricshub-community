import * as React from "react";
import { Box } from "@mui/material";
import HoverInfo from "../monitors/components/HoverInfo";

/**
 * A circular badge to display a count or status.
 *
 * @param {{
 *   count: number | string,
 *   title?: string,
 *   bgcolor?: string,
 *   color?: string,
 *   sx?: object
 * }} props
 */
const CountBadge = ({
	count,
	title,
	bgcolor = "action.disabledBackground",
	color = "text.primary",
	sx = {},
}) => {
	const badge = (
		<Box
			component="span"
			sx={{
				minWidth: 24,
				px: 1,
				height: 24,
				display: "flex",
				justifyContent: "center",
				alignItems: "center",
				borderRadius: 999,
				fontSize: 12,
				fontWeight: 700,
				bgcolor,
				color,
				...sx,
			}}
		>
			{count}
		</Box>
	);

	if (title) {
		return (
			<HoverInfo title={title} sx={{ display: "flex", alignItems: "center" }}>
				{badge}
			</HoverInfo>
		);
	}

	return badge;
};

export default React.memo(CountBadge);
