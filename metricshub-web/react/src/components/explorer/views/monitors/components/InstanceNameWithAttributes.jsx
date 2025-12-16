import * as React from "react";
import { Box, Tooltip, Typography } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import TruncatedText from "../../common/TruncatedText";

/**
 * Renders an instance name along with an info icon that shows attributes in a tooltip.
 *
 * @param {{
 *   displayName: string,
 *   attributes: Record<string, any>,
 *   sx?: import("@mui/material").SxProps,
 *   variant?: import("@mui/material/styles").TypographyVariant,
 *   fontWeight?: number | string
 * }} props
 */
const InstanceNameWithAttributes = ({
	displayName,
	attributes,
	sx,
	variant = "body2",
	fontWeight,
}) => {
	const hasAttributes = attributes && Object.keys(attributes).length > 0;

	return (
		<Box display="flex" alignItems="center" sx={{ minWidth: 0, ...sx }}>
			<Box sx={{ minWidth: 0, flexShrink: 1, mr: 1 }}>
				<TruncatedText text={displayName}>
					<Typography variant={variant} sx={{ fontWeight }} component="span">
						{displayName}
					</Typography>
				</TruncatedText>
			</Box>
			{hasAttributes && (
				<Tooltip
					title={
						<Box>
							<Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 0.5 }}>
								Attributes
							</Typography>
							<Box component="ul" sx={{ m: 0, pl: 2, fontSize: "0.75rem", textAlign: "left" }}>
								{Object.entries(attributes).map(([k, v]) => (
									<li key={k}>
										<strong>{k}:</strong> {String(v)}
									</li>
								))}
							</Box>
						</Box>
					}
				>
					<InfoOutlinedIcon
						fontSize="small"
						color="action"
						sx={{ fontSize: 16, cursor: "help", opacity: 0.7, flexShrink: 0 }}
					/>
				</Tooltip>
			)}
		</Box>
	);
};

export default InstanceNameWithAttributes;
