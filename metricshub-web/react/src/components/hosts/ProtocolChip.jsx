import * as React from "react";
import { Box, Chip, Stack, Tooltip, Typography } from "@mui/material";
import { protocolChipSx } from "./protocol-chip-sx";

/**
 * @param {0 | 1 | null | undefined} up
 * @returns {string}
 */
const healthColor = (up) => {
	if (up === 1) {
		return "success.main";
	}
	if (up === 0) {
		return "error.main";
	}
	return "text.disabled";
};

/**
 * @param {0 | 1 | null | undefined} up
 * @returns {string}
 */
const healthTitle = (up) => {
	if (up === 1) {
		return "Protocol up";
	}
	if (up === 0) {
		return "Protocol down";
	}
	return "Health unknown";
};

/**
 * Protocol badge with optional metricshub.host.up status (1 = up, 0 = down).
 *
 * @param {object} props
 * @param {string} props.protocol
 * @param {0 | 1 | null} [props.up]
 */
const ProtocolChip = ({ protocol, up }) => (
	<Tooltip title={`${protocol}: ${healthTitle(up)}`}>
		<Chip
			size="small"
			label={
				<Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.75 }}>
					<Box
						component="span"
						sx={{
							width: 10,
							height: 10,
							borderRadius: "50%",
							bgcolor: healthColor(up),
							flexShrink: 0,
							boxShadow: (theme) =>
								up === 1 || up === 0 ? `0 0 0 1px ${theme.palette.background.paper}` : "none",
						}}
					/>
					<span>{protocol}</span>
				</Box>
			}
			variant="outlined"
			sx={{
				...protocolChipSx(protocol),
				"&.MuiChip-outlined": {
					bgcolor: "transparent",
					backgroundColor: "transparent",
				},
			}}
		/>
	</Tooltip>
);

/**
 * @param {object} props
 * @param {string[]} [props.protocols]
 * @param {Record<string, 0 | 1 | null>} [props.healthByProtocol]
 */
export const ProtocolChips = ({ protocols, healthByProtocol }) => {
	if (!protocols?.length) {
		return null;
	}
	return (
		<>
			{protocols.map((p) => (
				<ProtocolChip key={p} protocol={p} up={healthByProtocol?.[p]} />
			))}
		</>
	);
};

/**
 * Right-aligned protocol chips with metricshub.host.up health (resource-level only).
 *
 * @param {object} props
 * @param {string[]} [props.protocols]
 * @param {Record<string, 0 | 1 | null>} [props.healthByProtocol]
 */
export const ProtocolChipsRow = ({ protocols, healthByProtocol }) => {
	if (!protocols?.length) {
		return (
			<Typography variant="caption" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
				No protocols
			</Typography>
		);
	}
	return (
		<Box
			sx={{
				display: "flex",
				flexWrap: "wrap",
				gap: 0.5,
				justifyContent: "flex-end",
				maxWidth: "100%",
			}}
		>
			<ProtocolChips protocols={protocols} healthByProtocol={healthByProtocol} />
		</Box>
	);
};

/**
 * Compact status dots (one per protocol) for host rows.
 *
 * @param {object} props
 * @param {string[]} [props.protocols]
 * @param {Record<string, 0 | 1 | null>} [props.healthByProtocol]
 */
export const ProtocolHealthDots = ({ protocols, healthByProtocol }) => {
	if (!protocols?.length) {
		return null;
	}
	return (
		<Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
			{protocols.map((protocol) => {
				const up = healthByProtocol?.[protocol];
				return (
					<Tooltip key={protocol} title={`${protocol}: ${healthTitle(up)}`}>
						<Box
							component="span"
							aria-label={`${protocol} ${healthTitle(up).toLowerCase()}`}
							sx={{
								width: 10,
								height: 10,
								borderRadius: "50%",
								bgcolor: healthColor(up),
								flexShrink: 0,
								display: "inline-block",
								boxShadow: (theme) => `0 0 0 1px ${theme.palette.divider}`,
							}}
						/>
					</Tooltip>
				);
			})}
		</Stack>
	);
};

export default ProtocolChip;
