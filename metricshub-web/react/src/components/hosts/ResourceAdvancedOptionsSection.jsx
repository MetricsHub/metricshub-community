import * as React from "react";
import {
	Box,
	Button,
	Checkbox,
	Collapse,
	Divider,
	FormControlLabel,
	MenuItem,
	Stack,
	Switch,
	TextField,
	Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import KeyValueRowsEditor from "./KeyValueRowsEditor";
import {
	LabeledSelect,
	LabeledTextField,
	filledInputNoLabelSx,
	guidedConfigFieldLabelSx,
} from "./guided-config-form-primitives";
import {
	DEFAULT_ENABLE_SELF_MONITORING,
	DEFAULT_ENRICHMENT,
	DEFAULT_LOGGER_LEVEL,
	DEFAULT_STATE_SET_COMPRESSION,
	ENRICHMENT_OPTIONS,
	LOGGER_LEVEL_OPTIONS,
	STATE_SET_COMPRESSION_OPTIONS,
} from "./resource-config-fields";
import { TIMEOUT_FORMAT_TOOLTIP } from "./protocol-definitions";
import FieldHelpTooltip from "./FieldHelpTooltip";
import { formatDurationSeconds } from "./use-resource-defaults";

/**
 * Group heading inside Advanced options (Log / Scheduling / Monitoring / …).
 * Deliberately larger than the field labels ({@link guidedConfigFieldLabelSx}) so
 * the group / field hierarchy reads at a glance.
 */
const groupTitleSx = {
	fontSize: "1.2rem",
	fontWeight: 700,
	lineHeight: 1.4,
	color: "text.primary",
};

/** Two-column responsive grid used by each advanced-options group. */
const twoColumnGridSx = {
	display: "grid",
	gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" },
	gap: 2,
	alignItems: "start",
};

const DurationLabel = ({ label }) => (
	<Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 0.75 }}>
		<Typography component="span" sx={guidedConfigFieldLabelSx}>
			{label}
		</Typography>
		<FieldHelpTooltip title={TIMEOUT_FORMAT_TOOLTIP} />
	</Box>
);

/**
 * Collapsible advanced resource options aligned with {@code ResourceConfig}.
 *
 * @param {object} props
 * @param {ReturnType<import("./resource-config-fields").createEmptyResourceAdvancedState>} props.values
 * @param {(patch: object) => void} props.onChange
 * @param {object | null} [props.inheritedDefaults] effective defaults (agent settings
 *        overridden by the resource group) shown as placeholders
 * @param {boolean} [props.showAttributesAndMetrics] include the Additional attributes /
 *        Metrics group (false for resource groups, which own those as top-level sections)
 * @param {string} [props.inheritLabel] label for the "inherit from parent" checkbox
 *        (e.g. "Apply advanced options from resource group" / "…from the Agent")
 */
const ResourceAdvancedOptionsSection = ({
	values,
	onChange,
	inheritedDefaults = null,
	showAttributesAndMetrics = true,
	inheritLabel = "Apply advanced options from the parent",
}) => {
	// Advanced options always start collapsed; the user expands them on demand.
	const [open, setOpen] = React.useState(false);

	const patch = (partial) => onChange({ resourceAdvanced: { ...values, ...partial } });

	// When on (the default), every inheritance-aware field is inherited from the parent
	// and locked; nothing is written to the config. Turning it off unlocks the fields.
	const inheritAdvanced = values.inheritAdvanced !== false;
	const managedDisabled = inheritAdvanced;

	// Logging is on unless the *effective* logger level resolves to "off". The effective
	// level cascades: the resource's own level → the inherited default (resource group →
	// agent, already resolved by the resource-defaults endpoint) → the built-in default.
	const OFF_LEVEL = "off";
	const ownLevel = String(values.loggerLevel ?? "").trim();
	const inheritedLevel = String(inheritedDefaults?.loggerLevel ?? "").trim();
	const inheritedIsReal = inheritedLevel !== "" && inheritedLevel.toLowerCase() !== OFF_LEVEL;
	const effectiveLevel = ownLevel || inheritedLevel || DEFAULT_LOGGER_LEVEL;
	const debugEnabled = effectiveLevel.toLowerCase() !== OFF_LEVEL;

	// A concrete, selectable level for the dropdown — never "" (inherit) or "off".
	const loggerLevelSelectValue =
		ownLevel && ownLevel.toLowerCase() !== OFF_LEVEL
			? ownLevel
			: inheritedIsReal
				? inheritedLevel
				: DEFAULT_LOGGER_LEVEL;

	const handleDebugToggle = (checked) => {
		// Enable → inherit the effective default when it is a real level, otherwise pick the
		// built-in level so it is not left off. Disable → persist an explicit "off" so the
		// disabled state survives a save/reload round-trip.
		if (checked) {
			patch({ loggerLevel: inheritedIsReal ? "" : DEFAULT_LOGGER_LEVEL });
		} else {
			patch({ loggerLevel: OFF_LEVEL });
		}
	};

	// Storing "" keeps the resource inheriting when the chosen level equals the inherited one.
	const handleLoggerLevelChange = (value) =>
		patch({ loggerLevel: value === inheritedLevel ? "" : value });

	return (
		<Box>
			<Button
				variant="text"
				size="small"
				onClick={() => setOpen((current) => !current)}
				endIcon={
					<ExpandMoreIcon
						sx={{
							transform: open ? "rotate(180deg)" : "rotate(0deg)",
							transition: "transform 0.2s",
						}}
					/>
				}
				sx={{
					px: 0,
					minWidth: 0,
					justifyContent: "flex-start",
					// Matches the "Protocols" title (subtitle1) in the same section.
					fontSize: "1rem",
					fontWeight: 700,
					color: "text.primary",
					"&:hover": {
						backgroundColor: "transparent",
						color: "primary.main",
					},
				}}
			>
				Advanced options
			</Button>
			<Collapse in={open} unmountOnExit>
				<Stack spacing={2.5} sx={{ mt: 2 }}>
					<Box>
						<FormControlLabel
							control={
								<Checkbox
									checked={inheritAdvanced}
									onChange={(e) => patch({ inheritAdvanced: e.target.checked })}
								/>
							}
							label={inheritLabel}
							slotProps={{ typography: { sx: guidedConfigFieldLabelSx } }}
						/>
						<Typography variant="caption" color="text.secondary" sx={{ display: "block", ml: 4 }}>
							{inheritAdvanced
								? "Uncheck to override any of the options below."
								: "Only the values you change are saved."}
						</Typography>
					</Box>

					<Divider />

					{/* — Log — */}
					<Typography sx={groupTitleSx}>Log</Typography>
					<FormControlLabel
						control={
							<Switch
								checked={debugEnabled}
								disabled={managedDisabled}
								onChange={(e) => handleDebugToggle(e.target.checked)}
							/>
						}
						label="Enable Debug"
						slotProps={{ typography: { sx: guidedConfigFieldLabelSx } }}
					/>
					<Box sx={twoColumnGridSx}>
						<LabeledSelect
							id="resource-logger-level"
							label="Logger level"
							selectProps={{
								value: loggerLevelSelectValue,
								disabled: managedDisabled || !debugEnabled,
								onChange: (e) => handleLoggerLevelChange(e.target.value),
							}}
						>
							{LOGGER_LEVEL_OPTIONS.map((option) => (
								<MenuItem key={option.value} value={option.value}>
									{option.label}
								</MenuItem>
							))}
						</LabeledSelect>
						<LabeledTextField
							id="resource-output-directory"
							label="Output directory"
							value={values.outputDirectory ?? ""}
							onChange={(e) => patch({ outputDirectory: e.target.value })}
							disabled={managedDisabled || !debugEnabled}
							placeholder={inheritedDefaults?.outputDirectory || "e.g. /opt/metricshub/logs"}
						/>
					</Box>

					<Divider />

					{/* — Scheduling — */}
					<Typography sx={groupTitleSx}>Scheduling</Typography>
					<Box sx={twoColumnGridSx}>
						<Box>
							<DurationLabel label="Collect period" />
							<TextField
								id="resource-collect-period"
								size="small"
								fullWidth
								hiddenLabel
								disabled={managedDisabled}
								value={values.collectPeriod ?? ""}
								onChange={(e) => patch({ collectPeriod: e.target.value })}
								placeholder={formatDurationSeconds(inheritedDefaults?.collectPeriod) || "e.g. 2m"}
								helperText="Plain numbers are seconds."
								sx={filledInputNoLabelSx}
							/>
						</Box>
						<LabeledTextField
							id="resource-discovery-cycle"
							label="Discovery cycle"
							value={values.discoveryCycle ?? ""}
							onChange={(e) => patch({ discoveryCycle: e.target.value.replace(/\D/g, "") })}
							disabled={managedDisabled}
							placeholder={
								inheritedDefaults?.discoveryCycle != null
									? String(inheritedDefaults.discoveryCycle)
									: "e.g. 30"
							}
							helperText="Number of collects between discovery runs."
						/>
						<Box>
							<DurationLabel label="Job timeout" />
							<TextField
								id="resource-job-timeout"
								size="small"
								fullWidth
								hiddenLabel
								disabled={managedDisabled}
								value={values.jobTimeout ?? ""}
								onChange={(e) => patch({ jobTimeout: e.target.value })}
								placeholder={formatDurationSeconds(inheritedDefaults?.jobTimeout) || "e.g. 5m"}
								sx={filledInputNoLabelSx}
							/>
						</Box>
					</Box>

					<Divider />

					{/* — Monitoring — */}
					<Typography sx={groupTitleSx}>Monitoring</Typography>
					<FormControlLabel
						control={
							<Switch
								checked={
									String(values.enableSelfMonitoring ?? DEFAULT_ENABLE_SELF_MONITORING) === "true"
								}
								disabled={managedDisabled}
								onChange={(e) =>
									patch({ enableSelfMonitoring: e.target.checked ? "true" : "false" })
								}
							/>
						}
						label="Self monitoring"
						slotProps={{ typography: { sx: guidedConfigFieldLabelSx } }}
					/>
					<Box sx={twoColumnGridSx}>
						<LabeledSelect
							id="resource-state-set-compression"
							label="State set compression"
							selectProps={{
								value: values.stateSetCompression ?? DEFAULT_STATE_SET_COMPRESSION,
								disabled: managedDisabled,
								onChange: (e) => patch({ stateSetCompression: e.target.value }),
							}}
						>
							{STATE_SET_COMPRESSION_OPTIONS.map((option) => (
								<MenuItem key={option.value} value={option.value}>
									{option.label}
								</MenuItem>
							))}
						</LabeledSelect>
						<LabeledSelect
							id="resource-enrichments"
							label="Enrichment"
							selectProps={{
								value: values.enrichments ?? DEFAULT_ENRICHMENT,
								disabled: managedDisabled,
								onChange: (e) => patch({ enrichments: e.target.value }),
							}}
						>
							{ENRICHMENT_OPTIONS.map((option) => (
								<MenuItem key={option.value} value={option.value}>
									{option.label}
								</MenuItem>
							))}
						</LabeledSelect>
						<LabeledTextField
							id="resource-monitor-filters"
							label="Monitor filters"
							value={values.monitorFilters ?? ""}
							onChange={(e) => patch({ monitorFilters: e.target.value })}
							disabled={managedDisabled}
							placeholder={
								(inheritedDefaults?.monitorFilters || []).length > 0
									? [...inheritedDefaults.monitorFilters].join(", ")
									: "Comma-separated monitor names"
							}
						/>
					</Box>

					<Divider />

					{/* — Network — */}
					<Typography sx={groupTitleSx}>Network</Typography>
					<Stack spacing={1}>
						<FormControlLabel
							control={
								<Switch
									checked={Boolean(values.sequential)}
									disabled={managedDisabled}
									onChange={(e) => patch({ sequential: e.target.checked })}
								/>
							}
							label="Sequential network calls"
							slotProps={{ typography: { sx: guidedConfigFieldLabelSx } }}
						/>
						<FormControlLabel
							control={
								<Switch
									checked={Boolean(values.resolveHostnameToFqdn)}
									disabled={managedDisabled}
									onChange={(e) => patch({ resolveHostnameToFqdn: e.target.checked })}
								/>
							}
							label="Resolve hostname to FQDN"
							slotProps={{ typography: { sx: guidedConfigFieldLabelSx } }}
						/>
					</Stack>

					{showAttributesAndMetrics ? (
						<>
							<Divider />

							{/* — Attributes and Metrics — */}
							<Typography sx={groupTitleSx}>Attributes and Metrics</Typography>
							<KeyValueRowsEditor
								rows={values.customAttributeRows || []}
								onRowsChange={(customAttributeRows) => patch({ customAttributeRows })}
								addLabel="Add attribute"
								keyLabel="Key"
								valueLabel="Value"
								sectionTitle="Additional attributes"
								labelsAbove
								bordered
							/>

							<KeyValueRowsEditor
								rows={values.metricRows || []}
								onRowsChange={(metricRows) => patch({ metricRows })}
								addLabel="Add metric"
								keyLabel="Key"
								valueLabel="Value"
								sectionTitle="Metrics"
								labelsAbove
								bordered
								monospaceKeys
								valueInputMode="decimal"
							/>
						</>
					) : null}
				</Stack>
			</Collapse>
		</Box>
	);
};

export default ResourceAdvancedOptionsSection;
