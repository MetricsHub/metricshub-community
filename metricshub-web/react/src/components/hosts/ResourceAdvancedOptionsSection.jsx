import * as React from "react";
import {
	Box,
	Button,
	Collapse,
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
} from "./guided-config-form-primitives";
import {
	ENABLE_SELF_MONITORING_OPTIONS,
	LOGGER_LEVEL_OPTIONS,
	STATE_SET_COMPRESSION_OPTIONS,
} from "./resource-config-fields";
import { TIMEOUT_FORMAT_TOOLTIP } from "./protocol-definitions";
import FieldHelpTooltip from "./FieldHelpTooltip";

const DurationLabel = ({ label }) => (
	<Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 0.75 }}>
		<Typography component="span" variant="body2" fontWeight={600}>
			{label}
		</Typography>
		<FieldHelpTooltip title={TIMEOUT_FORMAT_TOOLTIP} />
	</Box>
);

const hasConfiguredValue = (resourceAdvanced) => {
	if (!resourceAdvanced || typeof resourceAdvanced !== "object") {
		return false;
	}
	const scalarFields = [
		"loggerLevel",
		"outputDirectory",
		"collectPeriod",
		"discoveryCycle",
		"monitorFilters",
		"jobTimeout",
		"stateSetCompression",
		"enrichments",
		"alertingSystemProblemTemplate",
	];
	if (scalarFields.some((field) => String(resourceAdvanced[field] ?? "").trim() !== "")) {
		return true;
	}
	if (resourceAdvanced.sequential) {
		return true;
	}
	if (resourceAdvanced.logFileSourceDetails) {
		return true;
	}
	if (resourceAdvanced.resolveHostnameToFqdn) {
		return true;
	}
	if (resourceAdvanced.alertingSystemDisable) {
		return true;
	}
	if (String(resourceAdvanced.enableSelfMonitoring ?? "").trim() !== "") {
		return true;
	}
	if ((resourceAdvanced.customAttributeRows || []).some((row) => String(row?.key ?? "").trim())) {
		return true;
	}
	if ((resourceAdvanced.metricRows || []).some((row) => String(row?.key ?? "").trim())) {
		return true;
	}
	return false;
};

/**
 * Collapsible advanced resource options aligned with {@code ResourceConfig}.
 *
 * @param {object} props
 * @param {ReturnType<import("./resource-config-fields").createEmptyResourceAdvancedState>} props.values
 * @param {(patch: object) => void} props.onChange
 */
const ResourceAdvancedOptionsSection = ({ values, onChange }) => {
	const [open, setOpen] = React.useState(() => hasConfiguredValue(values));

	React.useEffect(() => {
		if (hasConfiguredValue(values)) {
			setOpen(true);
		}
	}, [values]);

	const patch = (partial) => onChange({ resourceAdvanced: { ...values, ...partial } });

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
					fontWeight: 600,
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
					<Box
						sx={{
							display: "grid",
							gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" },
							gap: 2,
							alignItems: "start",
						}}
					>
						<LabeledSelect
							id="resource-logger-level"
							label="Logger level"
							selectProps={{
								value: values.loggerLevel ?? "",
								onChange: (e) => patch({ loggerLevel: e.target.value }),
							}}
						>
							{LOGGER_LEVEL_OPTIONS.map((option) => (
								<MenuItem key={option.value || "default"} value={option.value}>
									{option.label}
								</MenuItem>
							))}
						</LabeledSelect>
						<LabeledTextField
							id="resource-output-directory"
							label="Output directory"
							value={values.outputDirectory ?? ""}
							onChange={(e) => patch({ outputDirectory: e.target.value })}
							placeholder="e.g. /opt/metricshub/logs"
						/>
						<Box>
							<DurationLabel label="Collect period" />
							<TextField
								id="resource-collect-period"
								size="small"
								fullWidth
								hiddenLabel
								value={values.collectPeriod ?? ""}
								onChange={(e) => patch({ collectPeriod: e.target.value })}
								placeholder="e.g. 2m"
								helperText="Plain numbers are seconds."
								sx={filledInputNoLabelSx}
							/>
						</Box>
						<LabeledTextField
							id="resource-discovery-cycle"
							label="Discovery cycle"
							value={values.discoveryCycle ?? ""}
							onChange={(e) => patch({ discoveryCycle: e.target.value.replace(/\D/g, "") })}
							placeholder="e.g. 30"
							helperText="Number of collects between discovery runs."
						/>
						<Box>
							<DurationLabel label="Job timeout" />
							<TextField
								id="resource-job-timeout"
								size="small"
								fullWidth
								hiddenLabel
								value={values.jobTimeout ?? ""}
								onChange={(e) => patch({ jobTimeout: e.target.value })}
								placeholder="e.g. 5m"
								sx={filledInputNoLabelSx}
							/>
						</Box>
						<LabeledSelect
							id="resource-state-set-compression"
							label="State set compression"
							selectProps={{
								value: values.stateSetCompression ?? "",
								onChange: (e) => patch({ stateSetCompression: e.target.value }),
							}}
						>
							{STATE_SET_COMPRESSION_OPTIONS.map((option) => (
								<MenuItem key={option.value || "default"} value={option.value}>
									{option.label}
								</MenuItem>
							))}
						</LabeledSelect>
						<LabeledSelect
							id="resource-enable-self-monitoring"
							label="Self monitoring"
							selectProps={{
								value: values.enableSelfMonitoring ?? "",
								onChange: (e) => patch({ enableSelfMonitoring: e.target.value }),
							}}
						>
							{ENABLE_SELF_MONITORING_OPTIONS.map((option) => (
								<MenuItem key={option.value || "default"} value={option.value}>
									{option.label}
								</MenuItem>
							))}
						</LabeledSelect>
						<LabeledTextField
							id="resource-monitor-filters"
							label="Monitor filters"
							value={values.monitorFilters ?? ""}
							onChange={(e) => patch({ monitorFilters: e.target.value })}
							placeholder="Comma-separated monitor names"
						/>
						<LabeledTextField
							id="resource-enrichments"
							label="Enrichments"
							value={values.enrichments ?? ""}
							onChange={(e) => patch({ enrichments: e.target.value })}
							placeholder="Comma-separated enrichment names"
						/>
					</Box>

					<Stack spacing={1}>
						<FormControlLabel
							control={
								<Switch
									checked={Boolean(values.sequential)}
									onChange={(e) => patch({ sequential: e.target.checked })}
								/>
							}
							label="Sequential network calls"
						/>
						<FormControlLabel
							control={
								<Switch
									checked={Boolean(values.logFileSourceDetails)}
									onChange={(e) => patch({ logFileSourceDetails: e.target.checked })}
								/>
							}
							label="Log file source details"
						/>
						<FormControlLabel
							control={
								<Switch
									checked={Boolean(values.resolveHostnameToFqdn)}
									onChange={(e) => patch({ resolveHostnameToFqdn: e.target.checked })}
								/>
							}
							label="Resolve hostname to FQDN"
						/>
						<FormControlLabel
							control={
								<Switch
									checked={Boolean(values.alertingSystemDisable)}
									onChange={(e) => patch({ alertingSystemDisable: e.target.checked })}
								/>
							}
							label="Disable alerting system"
						/>
					</Stack>

					<LabeledTextField
						id="resource-alerting-problem-template"
						label="Alert problem template"
						value={values.alertingSystemProblemTemplate ?? ""}
						onChange={(e) => patch({ alertingSystemProblemTemplate: e.target.value })}
						placeholder="Problem on ${FQDN} with ${MONITOR_NAME}…"
						multiline
						minRows={3}
					/>

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
				</Stack>
			</Collapse>
		</Box>
	);
};

export default ResourceAdvancedOptionsSection;
