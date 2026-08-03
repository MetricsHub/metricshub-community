import * as React from "react";
import {
	Avatar,
	Box,
	List,
	ListItemAvatar,
	ListItemButton,
	ListItemText,
	Typography,
} from "@mui/material";
import { scrollbarThumbSx } from "../split-screen/SplitScreen";

/**
 * Fixed side panel listing configuration steps (numbered).
 *
 * @param {object} props
 * @param {Array<{ id: string; label: string }>} props.steps
 * @param {number} props.activeStep
 * @param {(index: number) => void} [props.onStepClick]
 */
const HostConfigStepRail = ({ steps = [], activeStep, onStepClick }) => (
	<Box
		sx={{
			display: "flex",
			flexDirection: "column",
			height: "100%",
			minHeight: 0,
			width: "100%",
			pt: 1.5,
			px: 1,
		}}
	>
		<Typography variant="caption" color="text.secondary" sx={{ px: 0.5, mb: 1, display: "block" }}>
			Configuration steps
		</Typography>
		<Box sx={(t) => ({ flex: 1, minHeight: 0, overflowY: "auto", ...scrollbarThumbSx(t) })}>
			<List dense disablePadding>
				{steps.map((step, index) => (
					<ListItemButton
						key={step.id}
						selected={activeStep === index}
						component={onStepClick ? "button" : "div"}
						disableRipple={!onStepClick}
						onClick={onStepClick ? () => onStepClick(index) : undefined}
						sx={{
							borderRadius: 1,
							py: 0.75,
							cursor: onStepClick ? "pointer" : "default",
						}}
					>
						<ListItemAvatar sx={{ minWidth: 36 }}>
							<Avatar
								sx={(theme) => ({
									width: 22,
									height: 22,
									fontSize: 12,
									fontWeight: 700,
									bgcolor: activeStep === index ? "primary.main" : "action.selected",
									color:
										activeStep === index
											? theme.palette.primary.contrastText
											: theme.palette.text.secondary,
								})}
							>
								{index + 1}
							</Avatar>
						</ListItemAvatar>
						<ListItemText
							primary={step.label}
							primaryTypographyProps={{
								variant: "body2",
								fontWeight: activeStep === index ? 600 : 400,
							}}
						/>
					</ListItemButton>
				))}
			</List>
		</Box>
	</Box>
);

export default HostConfigStepRail;
