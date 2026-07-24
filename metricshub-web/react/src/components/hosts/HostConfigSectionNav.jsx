import * as React from "react";
import { Box, Stack, Typography } from "@mui/material";
import { alpha } from "@mui/material/styles";
import CheckIcon from "@mui/icons-material/Check";
import { getFormSectionDescription } from "./host-config-sections";

const CIRCLE_SIZE = 32;

/**
 * Card-style vertical stepper for the create-resource page.
 *
 * @param {object} props
 * @param {Array<{ id: string; label: string }>} props.steps
 * @param {number} props.activeStep
 * @param {string[]} [props.validatedStepIds]
 * @param {string[]} [props.invalidStepIds]
 * @param {string[]} [props.editedStepIds]
 * @param {(index: number) => void} props.onStepClick
 */
const HostConfigSectionNav = ({
	steps = [],
	activeStep,
	validatedStepIds = [],
	invalidStepIds = [],
	editedStepIds = [],
	onStepClick,
}) => {
	const validSet = React.useMemo(() => new Set(validatedStepIds), [validatedStepIds]);
	const invalidSet = React.useMemo(() => new Set(invalidStepIds), [invalidStepIds]);
	const editedSet = React.useMemo(() => new Set(editedStepIds), [editedStepIds]);

	return (
		<Stack spacing={0} sx={{ position: "relative" }}>
			{steps.map((step, index) => {
				const isActive = index === activeStep;
				const isEdited = editedSet.has(step.id);
				const isCompleted = validSet.has(step.id) && !isEdited;
				const isInvalid = invalidSet.has(step.id);
				const isLast = index === steps.length - 1;
				const description = isActive ? (step.description ?? getFormSectionDescription(step)) : null;

				return (
					<Box
						key={step.id}
						component="button"
						type="button"
						onClick={() => onStepClick(index)}
						sx={{
							display: "flex",
							alignItems: "stretch",
							gap: 1.5,
							width: "100%",
							textAlign: "left",
							border: 0,
							bgcolor: "transparent",
							borderRadius: 1.5,
							p: 1.25,
							cursor: "pointer",
							transition: "background-color 0.15s ease",
							"&:hover": {
								bgcolor: (theme) =>
									alpha(theme.palette.primary.main, theme.palette.mode === "dark" ? 0.08 : 0.04),
							},
						}}
					>
						<Box
							sx={{
								display: "flex",
								flexDirection: "column",
								alignItems: "center",
								width: CIRCLE_SIZE,
								flexShrink: 0,
							}}
						>
							<Box
								sx={{
									width: CIRCLE_SIZE,
									height: CIRCLE_SIZE,
									borderRadius: "50%",
									display: "flex",
									alignItems: "center",
									justifyContent: "center",
									flexShrink: 0,
									fontWeight: 700,
									fontSize: "0.875rem",
									border: 1,
									borderColor: "divider",
									...(isCompleted && {
										bgcolor: "success.main",
										color: "success.contrastText",
										borderColor: "success.main",
									}),
									...(isEdited &&
										!isInvalid && {
											bgcolor: "transparent",
											color: "text.secondary",
											borderColor: "divider",
										}),
									...(isActive &&
										!isCompleted &&
										!isEdited && {
											bgcolor: "primary.main",
											color: "primary.contrastText",
											borderColor: "primary.main",
										}),
									...(!isActive &&
										!isCompleted &&
										!isEdited && {
											bgcolor: "transparent",
											color: "text.secondary",
										}),
									...(isInvalid && {
										bgcolor: "error.main",
										color: "error.contrastText",
										borderColor: "error.main",
									}),
								}}
							>
								{isCompleted ? <CheckIcon sx={{ fontSize: 18 }} /> : index + 1}
							</Box>
							{!isLast ? (
								<Box
									sx={{
										width: "1px",
										flexGrow: 1,
										flexShrink: 0,
										alignSelf: "center",
										minHeight: 12,
										mt: 0.5,
										mb: -1.25,
										bgcolor: "divider",
										borderRadius: "1px",
									}}
								/>
							) : null}
						</Box>
						<Box sx={{ flex: 1, minWidth: 0, pt: 0.25, pb: isLast ? 0 : 0.5 }}>
							<Typography
								variant="body2"
								fontWeight={isActive ? 700 : 500}
								color={isInvalid ? "error.main" : isEdited ? "text.secondary" : "text.primary"}
								sx={{ lineHeight: 1.35 }}
							>
								{step.label}
							</Typography>
							{description ? (
								<Typography
									variant="caption"
									color="text.secondary"
									sx={{ display: "block", mt: 0.25, lineHeight: 1.35 }}
								>
									{description}
								</Typography>
							) : null}
						</Box>
					</Box>
				);
			})}
		</Stack>
	);
};

export default HostConfigSectionNav;
