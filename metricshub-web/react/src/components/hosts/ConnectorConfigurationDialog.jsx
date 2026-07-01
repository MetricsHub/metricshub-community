import * as React from "react";
import {
	Box,
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Stack,
	Typography,
} from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import {
	createDefaultAdditionalConnectorEntry,
	isAdditionalConnectorIdTaken,
	normalizeInstanceVariableValues,
	readInstanceVariableValues,
	suggestAdditionalConnectorInstanceId,
} from "./connector-utils";
import { guidedConfigDeleteButtonSx, LabeledTextField } from "./guided-config-form-primitives";

/**
 * @param {object | null | undefined} template
 * @returns {string}
 */
const getTemplateTitle = (template) => {
	const id = String(template?.id || "");
	return String(template?.displayName || "").trim() || id;
};

/**
 * Popup to create or edit a configurable connector configuration (instance).
 *
 * @param {object} props
 * @param {boolean} props.open
 * @param {() => void} props.onClose
 * @param {object | null} props.template compatible catalog entry with variables
 * @param {string | null} props.instanceKey existing configuration id when editing
 * @param {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>} props.additionalConnectors
 * @param {(next: Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>) => void} props.onSave
 * @param {(instanceKey: string) => void} [props.onDelete]
 * @param {Record<string, string>} [props.errors]
 * @param {number} [props.validationAttempt]
 */
const ConnectorConfigurationDialog = ({
	open,
	onClose,
	template,
	instanceKey = null,
	additionalConnectors = {},
	onSave,
	onDelete,
	errors = {},
	validationAttempt = 0,
}) => {
	const isEdit = Boolean(instanceKey);
	const templateId = String(template?.id || "").trim();
	const variableDefs = React.useMemo(() => template?.variables || [], [template?.variables]);

	const [configurationId, setConfigurationId] = React.useState("");
	const [variableValues, setVariableValues] = React.useState(
		/** @type {Record<string, string>} */ ({}),
	);
	const [touched, setTouched] = React.useState(/** @type {Record<string, boolean>} */ ({}));

	React.useEffect(() => {
		if (!open) {
			return;
		}
		if (isEdit && instanceKey) {
			const entry = additionalConnectors?.[instanceKey];
			setConfigurationId(instanceKey);
			setVariableValues(readInstanceVariableValues(entry?.variables, variableDefs));
		} else if (templateId) {
			const suggestedId = suggestAdditionalConnectorInstanceId(templateId, additionalConnectors);
			const entry = createDefaultAdditionalConnectorEntry(templateId, variableDefs);
			setConfigurationId(suggestedId);
			setVariableValues(readInstanceVariableValues(entry.variables, variableDefs));
		} else {
			setConfigurationId("");
			setVariableValues({});
		}
		setTouched({});
	}, [open, isEdit, instanceKey, templateId, additionalConnectors, variableDefs]);

	const showValidationErrors = validationAttempt > 0;
	const trimmedId = String(configurationId ?? "").trim();
	const idError = React.useMemo(() => {
		if (!touched.configurationId && !showValidationErrors) {
			return "";
		}
		const wizardError = errors[`additionalConnectors.${instanceKey || trimmedId}.id`];
		if (!trimmedId) {
			return wizardError || "Configuration ID is required.";
		}
		if (isAdditionalConnectorIdTaken(additionalConnectors, trimmedId, instanceKey || "")) {
			return wizardError || "This configuration ID is already in use.";
		}
		return wizardError || "";
	}, [
		additionalConnectors,
		errors,
		instanceKey,
		showValidationErrors,
		touched.configurationId,
		trimmedId,
	]);

	const handleSave = () => {
		setTouched({ configurationId: true });
		if (!templateId || !trimmedId || idError) {
			return;
		}
		const variables = normalizeInstanceVariableValues(variableValues, variableDefs);
		const prevEntry = instanceKey ? additionalConnectors?.[instanceKey] : null;
		const entry = {
			uses: templateId,
			force: prevEntry?.force !== false,
			variables,
		};
		const next = { ...(additionalConnectors || {}) };
		if (instanceKey && instanceKey !== trimmedId) {
			delete next[instanceKey];
		}
		next[trimmedId] = entry;
		onSave(next);
		onClose();
	};

	const handleDelete = () => {
		if (!instanceKey || !onDelete) {
			return;
		}
		onDelete(instanceKey);
		onClose();
	};

	return (
		<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
			<DialogTitle>
				{isEdit ? "Edit configuration" : "Add configuration"}
				{template ? (
					<Typography variant="body2" color="text.secondary" sx={{ mt: 0.25, fontWeight: 400 }}>
						{getTemplateTitle(template)}
					</Typography>
				) : null}
			</DialogTitle>
			<DialogContent dividers>
				<Stack spacing={2} sx={{ pt: 0.5 }}>
					<LabeledTextField
						id="connector-configuration-id"
						label="Configuration ID"
						required
						value={configurationId}
						onChange={(event) => setConfigurationId(event.target.value)}
						onBlur={() => setTouched((prev) => ({ ...prev, configurationId: true }))}
						error={Boolean(idError)}
						helperText={idError || "Unique identifier for this configuration"}
					/>
					{variableDefs.length > 0 ? (
						<Stack spacing={1.5}>
							{variableDefs.map((def) => {
								const varName = String(def?.name || "").trim();
								if (!varName) {
									return null;
								}
								return (
									<LabeledTextField
										key={varName}
										id={`connector-configuration-var-${varName}`}
										label={varName}
										value={variableValues[varName] ?? ""}
										helperText={def?.description || undefined}
										onChange={(event) =>
											setVariableValues((prev) => ({
												...prev,
												[varName]: event.target.value,
											}))
										}
									/>
								);
							})}
						</Stack>
					) : (
						<Typography variant="body2" color="text.secondary">
							No variables for this connector.
						</Typography>
					)}
				</Stack>
			</DialogContent>
			<DialogActions sx={{ justifyContent: "space-between", px: 3, py: 2 }}>
				<Box>
					{isEdit && onDelete ? (
						<Button
							color="error"
							startIcon={<DeleteOutlineIcon />}
							onClick={handleDelete}
							sx={guidedConfigDeleteButtonSx}
						>
							Delete
						</Button>
					) : null}
				</Box>
				<Stack direction="row" spacing={1}>
					<Button onClick={onClose}>Cancel</Button>
					<Button variant="contained" onClick={handleSave} disabled={!templateId}>
						Save
					</Button>
				</Stack>
			</DialogActions>
		</Dialog>
	);
};

export default ConnectorConfigurationDialog;
