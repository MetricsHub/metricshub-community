import * as React from "react";
import {
	Box,
	Chip,
	FormControl,
	FormHelperText,
	FormLabel,
	Tooltip,
	Typography,
} from "@mui/material";
import { getProtocolOptionsForHostType } from "./protocol-definitions";
import { guidedConfigOutlinedPrimaryHoverSx } from "./guided-config-ui-tokens";

/** Uniform protocol picker chip — equal width/height for every option. */
const PROTOCOL_CHIP_WIDTH = 152;
const PROTOCOL_CHIP_HEIGHT = 40;

const protocolChipSx = (selected) => (theme) => ({
	width: PROTOCOL_CHIP_WIDTH,
	minWidth: PROTOCOL_CHIP_WIDTH,
	maxWidth: PROTOCOL_CHIP_WIDTH,
	height: PROTOCOL_CHIP_HEIGHT,
	borderRadius: 1,
	fontWeight: selected ? 600 : 500,
	...(!selected && {
		borderColor: theme.palette.divider,
		color: theme.palette.text.secondary,
		bgcolor: "transparent",
		...guidedConfigOutlinedPrimaryHoverSx,
	}),
	"& .MuiChip-label": {
		width: "100%",
		px: 1.25,
		fontSize: "1rem",
		lineHeight: 1.25,
		textAlign: "center",
	},
	"&:focus-visible": {
		outline: `2px solid ${theme.palette.primary.main}`,
		outlineOffset: 1,
	},
});

/**
 * Multi-select protocol picker (compact chips; selected state uses primary blue like wizard actions).
 *
 * @param {object} props
 * @param {string[]} props.value selected protocol ids
 * @param {(ids: string[]) => void} props.onChange
 * @param {boolean} [props.error]
 * @param {string} [props.helperText]
 * @param {string} [props.label]
 * @param {boolean} [props.showLabel] when false, hide the fieldset legend (parent section titles the block)
 * @param {string} [props.hostType]
 */
const ProtocolSelectionGrid = ({
	value = [],
	onChange,
	error = false,
	helperText,
	label = "Protocols",
	showLabel = true,
	hostType = "",
}) => {
	const protocolOptions = React.useMemo(() => getProtocolOptionsForHostType(hostType), [hostType]);
	const selectedSet = React.useMemo(() => new Set(value || []), [value]);

	const toggle = (protocolId) => {
		const next = new Set(selectedSet);
		if (next.has(protocolId)) {
			next.delete(protocolId);
		} else {
			next.add(protocolId);
		}
		onChange([...next]);
	};

	const selectedCount = selectedSet.size;

	const labelRow = (
		<Box
			sx={{
				display: "flex",
				alignItems: "baseline",
				justifyContent: "space-between",
				gap: 1,
				mb: showLabel ? 0.5 : 0.75,
			}}
		>
			{showLabel ? (
				<FormLabel component="legend" required sx={{ m: 0, typography: "body2", fontWeight: 600 }}>
					{label}
				</FormLabel>
			) : (
				<Typography component="label" variant="body2" fontWeight={600} sx={{ display: "block" }}>
					{label}
					<Box component="span" sx={{ color: "error.main", ml: 0.25 }}>
						*
					</Box>
				</Typography>
			)}
			{selectedCount > 0 && (
				<FormHelperText component="span" sx={{ m: 0, typography: "caption" }}>
					{selectedCount} selected
				</FormHelperText>
			)}
		</Box>
	);

	return (
		<FormControl component="fieldset" variant="standard" fullWidth error={error} required>
			{labelRow}
			<FormHelperText sx={{ mt: 0, mb: 1 }}>
				{helperText ||
					(showLabel
						? "Select one or more protocols (at least one). Each adds a configuration step."
						: "Select at least one protocol. Each adds a configuration step.")}
			</FormHelperText>
			<Box
				role="group"
				aria-label={label}
				sx={{
					display: "flex",
					flexWrap: "wrap",
					gap: 1,
					alignItems: "center",
				}}
			>
				{protocolOptions.map((protocol) => {
					const selected = selectedSet.has(protocol.id);
					return (
						<Tooltip key={protocol.id} title={protocol.id} placement="top" enterDelay={400}>
							<Chip
								label={protocol.label}
								size="medium"
								clickable
								color={selected ? "primary" : "default"}
								aria-pressed={selected}
								onClick={() => toggle(protocol.id)}
								variant={selected ? "filled" : "outlined"}
								sx={protocolChipSx(selected)}
							/>
						</Tooltip>
					);
				})}
			</Box>
		</FormControl>
	);
};

export default ProtocolSelectionGrid;
