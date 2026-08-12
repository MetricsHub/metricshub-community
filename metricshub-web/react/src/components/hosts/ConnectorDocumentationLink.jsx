import * as React from "react";
import { Link, Tooltip } from "@mui/material";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { connectorDocumentationUrl } from "./connector-utils";

/**
 * @param {object} props
 * @param {string} props.connectorId
 * @param {boolean} [props.iconOnly] show only the external-link icon (compact table rows)
 * @param {string} [props.label]
 * @param {import("@mui/material").SxProps} [props.sx]
 */
const ConnectorDocumentationLink = ({ connectorId, iconOnly = false, label = "Docs", sx }) => {
	const id = String(connectorId ?? "").trim();
	if (!id) {
		return null;
	}

	const href = connectorDocumentationUrl(id);

	if (iconOnly) {
		return (
			<Tooltip title="Open connector documentation">
				<Link
					href={href}
					target="_blank"
					rel="noopener noreferrer"
					aria-label="Open connector documentation"
					sx={{
						display: "inline-flex",
						alignItems: "center",
						flexShrink: 0,
						color: "primary.main",
						lineHeight: 1,
						mt: 0.15,
						"&:hover": { color: "primary.dark" },
						...sx,
					}}
					onClick={(event) => event.stopPropagation()}
				>
					<OpenInNewIcon sx={{ fontSize: 14, display: "block" }} />
				</Link>
			</Tooltip>
		);
	}

	return (
		<Link
			href={href}
			target="_blank"
			rel="noopener noreferrer"
			variant="caption"
			sx={{
				display: "inline-flex",
				alignItems: "center",
				gap: 0.35,
				flexShrink: 0,
				fontWeight: 600,
				color: "primary.main",
				"&:hover": { color: "primary.dark" },
				...sx,
			}}
			onClick={(event) => event.stopPropagation()}
		>
			{label}
			<OpenInNewIcon sx={{ fontSize: 12 }} />
		</Link>
	);
};

export default ConnectorDocumentationLink;
