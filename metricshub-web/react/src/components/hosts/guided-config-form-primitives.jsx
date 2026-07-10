import * as React from "react";
import {
	Box,
	FormControl,
	FormHelperText,
	Select,
	Stack,
	TextField,
	Typography,
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import FieldHelpTooltip from "./FieldHelpTooltip";

/**
 * Field title styling shared by every guided-config form label (resource details,
 * protocols, connectors) so they all match the section titles "Host Type" and
 * "Protocols": 1rem, bold, full-strength primary text.
 *
 * Uses an explicit fontSize instead of the `subtitle1` variant because the theme
 * dims subtitle1 with opacity 0.85, which is what made those two titles look faded.
 */
export const guidedConfigFieldLabelSx = {
	fontSize: "1rem",
	fontWeight: 700,
	lineHeight: 1.4,
	color: "text.primary",
	opacity: 1,
};

/** Placeholder tone for guided-config fields (static label above the input). */
export const guidedConfigPlaceholderSx = {
	"& .MuiInputBase-input::placeholder": {
		color: (theme) =>
			alpha(theme.palette.text.secondary, theme.palette.mode === "dark" ? 0.45 : 0.4),
		opacity: 1,
	},
};

/**
 * Class toggled on a section panel to briefly flash it after the user jumps to it
 * from the stepper, so the target is obvious. The keyframes/rule live in
 * {@link guidedConfigSectionFlashSx}, spread onto the scroll container.
 */
export const CONFIG_SECTION_FLASH_CLASS = "host-config-section-flash";

/**
 * Scroll-container styling that defines the temporary "jumped here" flash.
 * Uses an INSET box-shadow ring (not an outer outline): the scroll container clips
 * overflow on both axes, so an outward outline would be cut off on the sides. An
 * inset ring stays inside the panel, follows its border-radius, and is fully visible
 * on all four sides — growing in, holding, then fading over ~2s.
 *
 * @param {import("@mui/material").Theme} theme
 */
export const guidedConfigSectionFlashSx = (theme) => {
	const ring = theme.palette.primary.main;
	return {
		"@keyframes hostConfigSectionFlash": {
			"0%": { boxShadow: `inset 0 0 0 0 ${ring}` },
			"12%": { boxShadow: `inset 0 0 0 3px ${ring}` },
			"80%": { boxShadow: `inset 0 0 0 3px ${ring}` },
			"100%": { boxShadow: `inset 0 0 0 0 ${ring}` },
		},
		[`& .${CONFIG_SECTION_FLASH_CLASS}`]: {
			animation: "hostConfigSectionFlash 2s ease",
		},
	};
};

/** Filled TextField/Select styling when the label is rendered outside the control. */
export const filledInputNoLabelSx = {
	"& .MuiFilledInput-input": {
		paddingTop: "10px",
		paddingBottom: "10px",
	},
	"& .MuiFilledInput-root:hover": {
		backgroundColor: "transparent",
	},
	...guidedConfigPlaceholderSx,
};

/** Outlined panel used for stepper rail, protocol forms, and info cards. */
export const guidedConfigBorderedPanelSx = {
	borderRadius: 2,
	p: 2,
	bgcolor: "transparent",
	border: 1,
	borderColor: "divider",
	boxShadow: "none",
};

/** Rounded inset panel for key/value editors (resource group attributes, etc.). */
export const kvEditorBorderedContainerSx = {
	border: 1,
	borderColor: "divider",
	borderRadius: 2,
	p: 2,
	bgcolor: "transparent",
};

/** Selected state for exclusive choice ToggleButtons (matches connector table "Select" primary blue). */
export const guidedConfigChoiceToggleButtonSx = {
	textTransform: "none",
	px: 2.5,
	fontWeight: 600,
	"&.Mui-selected": {
		bgcolor: "primary.main",
		color: "primary.contrastText",
		"&:hover": {
			bgcolor: "primary.dark",
		},
	},
};

/** Primary Save action — matches Configuration YAML editor header button. */
export const guidedConfigSaveButtonSx = {
	"&:not(.Mui-disabled)": {
		background: "linear-gradient(135deg, #0A58CA 0%, #267DF4 100%)",
		color: "#fff",
	},
	"&:hover:not(.Mui-disabled)": {
		background: "linear-gradient(135deg, #084298 0%, #0A58CA 100%)",
	},
};

/** Destructive action — same gradient treatment as {@link guidedConfigSaveButtonSx}. */
export const guidedConfigDeleteButtonSx = {
	"&:not(.Mui-disabled)": {
		background: "linear-gradient(135deg, #B71C1C 0%, #E53935 100%)",
		color: "#fff",
	},
	"&:hover:not(.Mui-disabled)": {
		background: "linear-gradient(135deg, #8B0000 0%, #C62828 100%)",
	},
};

/**
 * Text field with a fixed label above the input (no shrinking placeholder label).
 *
 * @param {object} props
 * @param {string} props.id
 * @param {string} props.label
 * @param {boolean} [props.required]
 * @param {string} [props.placeholder]
 * @param {boolean} [props.error]
 * @param {React.ReactNode} [props.helperText]
 */
export const LabeledTextField = ({
	id,
	label,
	required = false,
	placeholder,
	error = false,
	helperText,
	...textFieldProps
}) => (
	<Box>
		<Typography
			component="label"
			htmlFor={id}
			sx={{ ...guidedConfigFieldLabelSx, display: "block", mb: 0.75 }}
		>
			{label}
			{required ? (
				<Box component="span" sx={{ color: "error.main", ml: 0.25 }}>
					*
				</Box>
			) : null}
		</Typography>
		<TextField
			id={id}
			size="small"
			fullWidth
			hiddenLabel
			placeholder={placeholder}
			error={error}
			helperText={helperText}
			sx={filledInputNoLabelSx}
			{...textFieldProps}
		/>
	</Box>
);

/**
 * Select with a fixed label above the control.
 *
 * @param {object} props
 * @param {string} props.id
 * @param {string} props.label
 * @param {boolean} [props.required]
 * @param {boolean} [props.error]
 * @param {React.ReactNode} [props.helperText]
 * @param {string} [props.attributeName] MetricsHub attribute name shown in a help tooltip
 * @param {import("@mui/material").SelectProps} props.selectProps
 * @param {React.ReactNode} props.children
 */
export const LabeledSelect = ({
	id,
	label,
	required = false,
	error = false,
	helperText,
	attributeName,
	selectProps = {},
	children,
}) => (
	<Box>
		<Stack direction="row" alignItems="center" spacing={0.25} sx={{ mb: 0.75 }}>
			<Typography component="label" htmlFor={id} sx={guidedConfigFieldLabelSx}>
				{label}
				{required ? (
					<Box component="span" sx={{ color: "error.main", ml: 0.25 }}>
						*
					</Box>
				) : null}
			</Typography>
			{attributeName ? <FieldHelpTooltip title={`MetricsHub attribute: ${attributeName}`} /> : null}
		</Stack>
		<FormControl fullWidth size="small" error={error} required={required}>
			<Select id={id} displayEmpty sx={filledInputNoLabelSx} {...selectProps}>
				{children}
			</Select>
			{helperText ? <FormHelperText>{helperText}</FormHelperText> : null}
		</FormControl>
	</Box>
);

/**
 * Section block with icon badge, title, and description.
 *
 * @param {object} props
 * @param {string} [props.id]
 * @param {React.ReactNode} props.icon
 * @param {string} props.title
 * @param {string} props.description
 * @param {boolean} [props.compact] slimmer header and padding for multi-field sections
 * @param {boolean} [props.emphasized] larger title for form sub-sections
 * @param {React.ReactNode} props.children
 */
export const FormSection = ({
	id,
	icon,
	title,
	description,
	compact = false,
	emphasized = false,
	children,
}) => (
	<Box
		id={id}
		component="section"
		sx={{
			scrollMarginTop: 88,
			p: compact ? 2 : 2.5,
			borderRadius: 1.5,
			border: 1,
			borderColor: "divider",
			bgcolor: "transparent",
		}}
	>
		<Box
			sx={{
				display: "flex",
				alignItems: compact ? "center" : "flex-start",
				gap: compact ? 1.25 : 1.5,
				mb: compact ? 1.5 : 2.5,
			}}
		>
			<Box
				sx={{
					width: compact ? 32 : 40,
					height: compact ? 32 : 40,
					borderRadius: 1.5,
					display: "flex",
					alignItems: "center",
					justifyContent: "center",
					flexShrink: 0,
					bgcolor: "transparent",
					border: 1,
					borderColor: "divider",
					color: "primary.main",
					"& .MuiSvgIcon-root": { fontSize: compact ? 18 : 22 },
				}}
			>
				{icon}
			</Box>
			<Box sx={{ minWidth: 0 }}>
				<Typography
					variant={emphasized ? "h6" : compact ? "subtitle2" : "subtitle1"}
					fontWeight={700}
					sx={{ lineHeight: 1.3 }}
				>
					{title}
				</Typography>
				{description ? (
					<Typography
						variant={compact ? "caption" : "body2"}
						color="text.secondary"
						sx={{ mt: compact ? 0.25 : 0.5, display: "block" }}
					>
						{description}
					</Typography>
				) : null}
			</Box>
		</Box>
		{children}
	</Box>
);

/**
 * Hero banner for guided-config create/edit pages.
 *
 * @param {object} props
 * @param {React.ReactNode} props.icon
 * @param {string} props.title
 * @param {string} props.description
 */
export const GuidedConfigFormHero = ({ icon, title, description }) => (
	<Box
		sx={{
			px: 3,
			pt: 3,
			pb: 2.5,
			borderBottom: 1,
			borderColor: "divider",
			background: (t) =>
				t.palette.mode === "dark"
					? `linear-gradient(180deg, ${t.palette.primary.alpha8} 0%, transparent 100%)`
					: `linear-gradient(180deg, ${t.palette.primary.alpha12} 0%, transparent 100%)`,
		}}
	>
		<Box sx={{ display: "flex", alignItems: "flex-start", gap: 2 }}>
			<Box
				sx={{
					width: 48,
					height: 48,
					borderRadius: "50%",
					bgcolor: "primary.main",
					display: "flex",
					alignItems: "center",
					justifyContent: "center",
					color: "primary.contrastText",
					flexShrink: 0,
					boxShadow: (t) =>
						`0 4px 14px ${t.palette.mode === "dark" ? "rgba(38,125,244,0.35)" : "rgba(38,125,244,0.28)"}`,
					"& .MuiSvgIcon-root": { fontSize: 26 },
				}}
			>
				{icon}
			</Box>
			<Box sx={{ pt: 0.25, minWidth: 0 }}>
				<Typography variant="h6" fontWeight={700} sx={{ lineHeight: 1.25 }}>
					{title}
				</Typography>
				<Typography variant="body2" color="text.secondary" sx={{ mt: 0.75, maxWidth: 560 }}>
					{description}
				</Typography>
			</Box>
		</Box>
	</Box>
);
