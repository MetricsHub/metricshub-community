import * as React from "react";
import { Box } from "@mui/material";
import HoverInfo from "../monitors/components/HoverInfo";

/**
 * A circular badge to display a count or status.
 *
 * @param {object} props - Component props
 * @param {number | string} props.count - The count or status to display
 * @param {string} [props.title] - Tooltip title
 * @param {string} [props.bgcolor="action.disabledBackground"] - Background color
 * @param {string} [props.color="text.primary"] - Text color
 * @param {import("@mui/material").SxProps} [props.sx={}] - Custom styles
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
				transition: "background-color 0.4s ease, color 0.4s ease",
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
