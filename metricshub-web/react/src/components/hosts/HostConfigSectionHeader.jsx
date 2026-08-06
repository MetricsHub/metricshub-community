import * as React from "react";
import { Box, Stack, Tooltip, Typography } from "@mui/material";
import CheckIcon from "@mui/icons-material/Check";

/**
 * @param {0 | 1 | null | undefined} up
 * @returns {string}
 */
const protocolHealthColor = (up) => {
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
const protocolHealthLabel = (up) => {
	if (up === 1) {
		return "host.up = 1";
	}
	if (up === 0) {
		return "host.up = 0";
	}
	return "host.up = unknown";
};

/**
 * Prominent section heading for the host config form scroll sections.
 *
 * @param {object} props
 * @param {import("./host-config-sections").FormSectionDescriptor} props.step
 * @param {number} props.stepNumber 1-based index matching the stepper
 * @param {boolean} [props.isCompleted]
 * @param {boolean} [props.isInvalid]
 * @param {boolean} [props.isEdited]
 * @param {string} [props.description]
 * @param {0 | 1 | null} [props.protocolUp] live host.up for protocol sections (single-host edit)
 * @param {React.ReactNode} [props.endAction] optional control aligned with the title row (e.g. delete)
 */
const HostConfigSectionHeader = ({
	step,
	stepNumber,
	isCompleted = false,
	isInvalid = false,
	isEdited = false,
	description,
	protocolUp,
	endAction,
}) => (
	<Box sx={{ mb: 2.5 }}>
		<Stack direction="row" alignItems="flex-start" spacing={1.5}>
			<Box
				sx={{
					width: 34,
					height: 34,
					borderRadius: "50%",
					display: "flex",
					alignItems: "center",
					justifyContent: "center",
					flexShrink: 0,
					fontWeight: 700,
					fontSize: "0.9375rem",
					...(isCompleted && {
						bgcolor: "success.main",
						color: "success.contrastText",
					}),
					...(isEdited &&
						!isCompleted &&
						!isInvalid && {
							bgcolor: "transparent",
							color: "text.secondary",
							border: 1,
							borderColor: "divider",
						}),
					...(!isCompleted &&
						!isInvalid &&
						!isEdited && {
							bgcolor: "transparent",
							color: "text.secondary",
							border: 1,
							borderColor: "divider",
						}),
					...(isInvalid && {
						bgcolor: "error.main",
						color: "error.contrastText",
					}),
				}}
			>
				{isCompleted ? <CheckIcon sx={{ fontSize: 20 }} /> : stepNumber}
			</Box>
			<Box sx={{ minWidth: 0, flex: 1 }}>
				<Stack direction="row" alignItems="center" spacing={1} useFlexGap flexWrap="wrap">
					<Typography
						variant="h5"
						fontWeight={700}
						color={isInvalid ? undefined : isEdited ? "text.secondary" : "text.primary"}
						sx={{ lineHeight: 1.25 }}
					>
						{step.label}
					</Typography>
					{protocolUp !== undefined ? (
						<Tooltip title={protocolHealthLabel(protocolUp)}>
							<Box
								component="span"
								sx={{
									display: "inline-flex",
									alignItems: "center",
									gap: 0.5,
									px: 0.75,
									py: 0.15,
									borderRadius: 1,
									border: 1,
									borderColor: "divider",
								}}
							>
								<Box
									component="span"
									sx={{
										width: 8,
										height: 8,
										borderRadius: "50%",
										bgcolor: protocolHealthColor(protocolUp),
										flexShrink: 0,
									}}
								/>
								<Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
									host.up
								</Typography>
							</Box>
						</Tooltip>
					) : null}
				</Stack>
				{description ? (
					<Typography variant="body2" color="text.secondary" sx={{ mt: 0.35, lineHeight: 1.35 }}>
						{description}
					</Typography>
				) : null}
			</Box>
			{endAction ? <Box sx={{ flexShrink: 0, pt: 0.25 }}>{endAction}</Box> : null}
		</Stack>
	</Box>
);

export default HostConfigSectionHeader;
