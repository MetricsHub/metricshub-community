import * as React from "react";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import RadioButtonUncheckedIcon from "@mui/icons-material/RadioButtonUnchecked";
import { alpha, Box, Chip, FormControl, FormHelperText, Stack, Typography } from "@mui/material";
import { getProtocolOptionsForHostType, HOST_TYPE_UNSELECTED } from "./protocol-definitions";
import { getProtocolPickerMetadata } from "./protocol-picker-metadata";
import { guidedConfigRowHoverBg } from "./guided-config-ui-tokens";

const DEFAULT_HELPER =
	"Select at least one protocol. Each choice unlocks a dedicated configuration step in this wizard.";

/**
 * @param {boolean} selected
 * @param {boolean} [error]
 */
const protocolCardSx =
	(selected, error = false) =>
	(theme) => ({
		display: "flex",
		alignItems: "flex-start",
		gap: 1.25,
		width: "100%",
		minHeight: 76,
		p: 1.5,
		textAlign: "left",
		borderRadius: 1.5,
		border: 2,
		borderColor: error
			? theme.palette.error.main
			: selected
				? theme.palette.primary.main
				: theme.palette.divider,
		bgcolor: selected ? alpha(theme.palette.primary.main, 0.08) : "transparent",
		color: "text.primary",
		cursor: "pointer",
		transition: theme.transitions.create(["border-color", "background-color", "box-shadow"], {
			duration: theme.transitions.duration.shorter,
		}),
		boxShadow: selected ? `0 0 0 1px ${alpha(theme.palette.primary.main, 0.2)}` : "none",
		"&:hover": {
			borderColor: selected ? theme.palette.primary.main : theme.palette.primary.light,
			bgcolor: selected
				? alpha(theme.palette.primary.main, 0.12)
				: guidedConfigRowHoverBg(theme, true, true),
		},
		"&:focus-visible": {
			outline: `2px solid ${theme.palette.primary.main}`,
			outlineOffset: 2,
		},
	});

const protocolIconBoxSx = (selected) => (theme) => ({
	width: 40,
	height: 40,
	borderRadius: 1.25,
	display: "flex",
	alignItems: "center",
	justifyContent: "center",
	flexShrink: 0,
	bgcolor: selected
		? alpha(theme.palette.primary.main, 0.16)
		: alpha(theme.palette.text.primary, theme.palette.mode === "dark" ? 0.08 : 0.06),
	color: selected ? "primary.main" : "text.secondary",
});

/**
 * Card-based multi-select protocol picker for resource configuration.
 *
 * @param {object} props
 * @param {string[]} props.value
 * @param {(ids: string[]) => void} props.onChange
 * @param {boolean} [props.error]
 * @param {string} [props.helperText]
 * @param {string} [props.hostType]
 */
const ProtocolSelectionPanel = ({
	value = [],
	onChange,
	error = false,
	helperText,
	hostType = "",
}) => {
	const protocolOptions = React.useMemo(() => getProtocolOptionsForHostType(hostType), [hostType]);
	const selectedSet = React.useMemo(() => new Set(value || []), [value]);
	const hostTypeReady = Boolean(hostType) && hostType !== HOST_TYPE_UNSELECTED;

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

	return (
		<FormControl component="fieldset" variant="standard" fullWidth error={error} required>
			<Box
				sx={{
					borderRadius: 2,
					border: 1,
					borderColor: error ? "error.main" : "divider",
					bgcolor: "transparent",
					p: { xs: 1.5, sm: 2 },
					transition: (theme) =>
						theme.transitions.create(["border-color"], {
							duration: theme.transitions.duration.shorter,
						}),
				}}
			>
				<Stack
					direction={{ xs: "column", sm: "row" }}
					alignItems={{ xs: "flex-start", sm: "center" }}
					justifyContent="space-between"
					spacing={1}
					sx={{ mb: 1.25 }}
				>
					<Box>
						<Typography component="legend" variant="subtitle1" fontWeight={700}>
							Protocols
							<Box component="span" sx={{ color: "error.main", ml: 0.25 }}>
								*
							</Box>
						</Typography>
						<Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
							{DEFAULT_HELPER}
						</Typography>
					</Box>
					{selectedCount > 0 ? (
						<Chip
							size="small"
							color="primary"
							variant="outlined"
							label={`${selectedCount} selected`}
							sx={{ fontWeight: 600 }}
						/>
					) : null}
				</Stack>

				{!hostTypeReady ? (
					<Box
						sx={{
							py: 3,
							px: 2,
							borderRadius: 1.5,
							border: 1,
							borderStyle: "dashed",
							borderColor: "divider",
							textAlign: "center",
						}}
					>
						<Typography variant="body2" color="text.secondary">
							Choose a host type above to see the protocols available for this resource.
						</Typography>
					</Box>
				) : (
					<Box
						role="group"
						aria-label="Protocols"
						sx={{
							display: "grid",
							gridTemplateColumns: {
								xs: "1fr",
								sm: "repeat(2, minmax(0, 1fr))",
								lg: "repeat(3, minmax(0, 1fr))",
							},
							gap: 1.25,
						}}
					>
						{protocolOptions.map((protocol) => {
							const selected = selectedSet.has(protocol.id);
							const { Icon, summary } = getProtocolPickerMetadata(protocol.id);
							return (
								<Box
									key={protocol.id}
									component="button"
									type="button"
									role="checkbox"
									aria-checked={selected}
									aria-label={`${protocol.label}. ${summary}`}
									onClick={() => toggle(protocol.id)}
									sx={protocolCardSx(selected, error && !selected && selectedCount === 0)}
								>
									<Box sx={protocolIconBoxSx(selected)}>
										<Icon sx={{ fontSize: 22 }} />
									</Box>
									<Box sx={{ flex: 1, minWidth: 0, pt: 0.25 }}>
										<Stack direction="row" alignItems="flex-start" spacing={0.75}>
											<Typography
												variant="body2"
												fontWeight={selected ? 700 : 600}
												sx={{ flex: 1, lineHeight: 1.3 }}
											>
												{protocol.label}
											</Typography>
											{selected ? (
												<CheckCircleIcon color="primary" sx={{ fontSize: 20, mt: "-1px" }} />
											) : (
												<RadioButtonUncheckedIcon
													sx={{ fontSize: 20, mt: "-1px", color: "action.disabled" }}
												/>
											)}
										</Stack>
										<Typography
											variant="caption"
											color="text.secondary"
											sx={{ display: "block", mt: 0.5, lineHeight: 1.35 }}
										>
											{summary}
										</Typography>
									</Box>
								</Box>
							);
						})}
					</Box>
				)}
			</Box>
			{error && helperText ? <FormHelperText sx={{ mt: 1 }}>{helperText}</FormHelperText> : null}
		</FormControl>
	);
};

export default ProtocolSelectionPanel;
