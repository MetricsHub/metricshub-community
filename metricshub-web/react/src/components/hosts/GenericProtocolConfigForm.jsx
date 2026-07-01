import * as React from "react";
import {
	Box,
	Button,
	Collapse,
	FormControl,
	FormControlLabel,
	FormHelperText,
	MenuItem,
	Radio,
	RadioGroup,
	Select,
	Stack,
	Switch,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import DeferredPasswordEncryptAlert from "./DeferredPasswordEncryptAlert";
import ProtocolPasswordField from "./ProtocolPasswordField";
import {
	filledInputNoLabelSx,
	guidedConfigChoiceToggleButtonSx,
} from "./guided-config-form-primitives";
import {
	getDefaultPortForTransport,
	hasTransportLinkedPort,
	portDescriptionForProtocol,
	portDescriptionForTransport,
	resolveProtocolAuthMethod,
	sanitizePortInput,
	shouldAutoUpdatePortForTransport,
	TIMEOUT_FIELD_DESCRIPTION,
	TIMEOUT_FORMAT_TOOLTIP,
	transportDescriptionForProtocol,
	validatePortValue,
	validateTimeoutValue,
} from "./protocol-definitions";
import {
	getProtocolFieldStartIcon,
	ProtocolFieldLabelRow,
	ProtocolFieldStartAdornment,
	protocolFieldStackSx,
	protocolTextFieldProps,
} from "./protocol-form-primitives";

/**
 * Renders protocol fields from PROTOCOL_FIELDS with the same layout as SSH configuration.
 *
 * @param {object} props
 * @param {string} props.protocol
 * @param {Array<import("./protocol-definitions").ProtocolField>} props.fields
 * @param {Record<string, unknown>} props.values
 * @param {(name: string, value: unknown) => void} props.onChange
 * @param {Record<string, string>} props.errors
 * @param {(field: import("./protocol-definitions").ProtocolField) => boolean} props.isRequired
 * @param {boolean} [props.allowPasswordReveal]
 * @param {boolean} [props.deferEncryptUntilSave]
 */
const GenericProtocolConfigForm = ({
	protocol,
	fields,
	values,
	onChange,
	errors,
	isRequired,
	allowPasswordReveal = false,
	deferEncryptUntilSave = false,
}) => {
	const transportLinkedPort = hasTransportLinkedPort(protocol);

	const showRequiredMarker = (field) => {
		if (transportLinkedPort && field.name === "port") {
			return true;
		}
		return isRequired(field);
	};

	const handleTransportChange = React.useCallback(
		(nextTransport) => {
			const previousTransport = values.protocol;
			const shouldUpdatePort = shouldAutoUpdatePortForTransport(
				protocol,
				previousTransport,
				values.port,
			);
			const nextPort = shouldUpdatePort
				? getDefaultPortForTransport(protocol, nextTransport)
				: values.port;

			onChange("protocol", nextTransport);
			if (shouldUpdatePort && nextPort != null && nextPort !== values.port) {
				onChange("port", nextPort);
			}
		},
		[onChange, protocol, values.port, values.protocol],
	);

	const renderStartAdornment = (fieldName) => {
		const icon = getProtocolFieldStartIcon(fieldName);
		return icon ? <ProtocolFieldStartAdornment>{icon}</ProtocolFieldStartAdornment> : undefined;
	};

	const fieldDescription = (field) => {
		if (field.name === "port") {
			if (transportLinkedPort) {
				return portDescriptionForTransport(protocol, values.protocol);
			}
			if (!field.helperText) {
				const protocolPortHint = portDescriptionForProtocol(protocol);
				if (protocolPortHint) {
					return protocolPortHint;
				}
			}
		}
		if (field.name === "protocol" && transportLinkedPort) {
			return transportDescriptionForProtocol(protocol);
		}
		if (field.name === "timeout") {
			return TIMEOUT_FIELD_DESCRIPTION;
		}
		const error = errors[field.name];
		return !error && field.helperText ? field.helperText : undefined;
	};

	const fieldHelpTooltip = (field) => {
		if (field.helpTooltip) {
			return field.helpTooltip;
		}
		if (field.name === "timeout") {
			return TIMEOUT_FORMAT_TOOLTIP;
		}
		return undefined;
	};

	/** Validation errors only — static hints stay inline on {@link ProtocolFieldLabelRow}. */
	const fieldErrorText = (fieldName) => errors[fieldName] || undefined;

	const isAdvancedFieldConfigured = (field) => {
		const val = String(values[field.name] ?? "").trim();
		if (field.name === "authentications") {
			return val !== "" && val !== "NTLM";
		}
		return val !== "";
	};

	const renderField = (field, showDeferredEncryptAlert = false) => {
		if (field.type === "boolean") {
			return renderBooleanField(field);
		}
		if (field.type === "select") {
			return renderSelectField(field);
		}
		if (field.type === "radio") {
			return renderRadioField(field);
		}
		if (field.type === "authChoice") {
			return renderAuthChoiceField(field, showDeferredEncryptAlert);
		}
		if (field.type === "modeChoice") {
			return renderModeChoiceField(field);
		}
		if (field.type === "password") {
			return renderPasswordField(field, showDeferredEncryptAlert);
		}
		return renderTextField(field);
	};

	const renderBooleanField = (field) => {
		const value = values[field.name];
		const description = fieldErrorText(field.name) || fieldDescription(field);

		return (
			<Box key={field.name}>
				<FormControlLabel
					control={
						<Switch
							checked={Boolean(value)}
							onChange={(e) => onChange(field.name, e.target.checked)}
						/>
					}
					label={
						<Box
							sx={{
								display: "flex",
								alignItems: "baseline",
								flexWrap: "wrap",
								columnGap: 0.75,
							}}
						>
							<Typography variant="body2" fontWeight={600}>
								{field.label}
							</Typography>
							{description ? (
								<Typography variant="body2" color="text.secondary">
									{description}
								</Typography>
							) : null}
						</Box>
					}
				/>
			</Box>
		);
	};

	const renderSelectField = (field) => {
		const value = values[field.name];
		const error = errors[field.name];
		const required = showRequiredMarker(field);
		const helperText = fieldErrorText(field.name);
		const description = fieldDescription(field);
		const isTransportField = transportLinkedPort && field.name === "protocol";
		const startAdornment = renderStartAdornment(field.name);

		return (
			<Box key={field.name}>
				<ProtocolFieldLabelRow
					label={field.label}
					required={required}
					description={description}
					helpTooltip={fieldHelpTooltip(field)}
				/>
				<FormControl fullWidth size="small" error={Boolean(error)} required={required}>
					<Select
						id={`${protocol}-${field.name}`}
						value={value ?? ""}
						onChange={(e) =>
							isTransportField
								? handleTransportChange(e.target.value)
								: onChange(field.name, e.target.value)
						}
						displayEmpty={Boolean(field.options?.some((opt) => opt.value === ""))}
						sx={filledInputNoLabelSx}
						slotProps={{
							input: startAdornment ? { startAdornment } : undefined,
						}}
					>
						{(field.options || []).map((opt) => (
							<MenuItem key={String(opt.value)} value={opt.value}>
								{opt.label}
							</MenuItem>
						))}
					</Select>
					{helperText ? <FormHelperText>{helperText}</FormHelperText> : null}
				</FormControl>
			</Box>
		);
	};

	const renderRadioField = (field) => {
		const value = values[field.name];
		const error = errors[field.name];
		const helperText = fieldErrorText(field.name);
		const description = fieldDescription(field);

		return (
			<Box key={field.name}>
				<ProtocolFieldLabelRow
					label={field.label}
					description={description}
					helpTooltip={fieldHelpTooltip(field)}
				/>
				<FormControl component="fieldset" error={Boolean(error)} sx={{ width: "100%" }}>
					<RadioGroup
						row
						value={value ?? ""}
						onChange={(e) => {
							const nextValue = e.target.value;
							onChange(field.name, nextValue);
							const option = (field.options || []).find((opt) => String(opt.value) === nextValue);
							(option?.clears || []).forEach((fieldName) => onChange(fieldName, ""));
						}}
					>
						{(field.options || []).map((opt) => (
							<FormControlLabel
								key={String(opt.value)}
								value={opt.value}
								control={<Radio size="small" />}
								label={opt.label}
							/>
						))}
					</RadioGroup>
					{helperText ? <FormHelperText sx={{ mx: 0 }}>{helperText}</FormHelperText> : null}
				</FormControl>
			</Box>
		);
	};

	const resolveAuthMethod = React.useCallback(
		(field) => {
			const fromForm = resolveProtocolAuthMethod(protocol, values);
			if (fromForm) {
				return fromForm;
			}
			const options = field.authOptions || [];
			return options[0]?.value ?? "password";
		},
		[protocol, values],
	);

	const authChoiceField = fields.find((field) => field.type === "authChoice");
	const authMethod = authChoiceField ? resolveAuthMethod(authChoiceField) : "password";

	const getAuthCredentialFields = (option) => {
		if (!option) {
			return [];
		}
		if (option.fields?.length) {
			return option.fields;
		}
		if (option.fieldName) {
			return [
				{
					fieldName: option.fieldName,
					fieldLabel: option.fieldLabel,
					fieldType: option.fieldType,
					placeholder: option.placeholder,
				},
			];
		}
		return [];
	};

	const renderModeChoiceField = (field) => {
		const options = field.options || [];
		const value = values[field.name] ?? options[0]?.value ?? "";
		const description = fieldDescription(field);

		const handleModeChange = (_, nextMode) => {
			if (!nextMode) {
				return;
			}
			onChange(field.name, nextMode);
			const option = options.find((opt) => opt.value === nextMode);
			(option?.clears || []).forEach((fieldName) => onChange(fieldName, ""));
		};

		return (
			<Box key={field.name}>
				<ProtocolFieldLabelRow
					label={field.label}
					description={description}
					helpTooltip={fieldHelpTooltip(field)}
				/>
				<ToggleButtonGroup
					value={value}
					exclusive
					onChange={handleModeChange}
					size="small"
					sx={{ mb: 2 }}
				>
					{options.map((option) => (
						<ToggleButton
							key={String(option.value)}
							value={option.value}
							sx={guidedConfigChoiceToggleButtonSx}
						>
							{option.label}
						</ToggleButton>
					))}
				</ToggleButtonGroup>
			</Box>
		);
	};

	const renderAuthChoiceField = (field, showDeferredEncryptAlert = false) => {
		const options = field.authOptions || [];
		const required = showRequiredMarker(field);
		const description = fieldDescription(field);
		const activeOption =
			options.find((option) => option.value === authMethod) || options[0] || null;

		const handleAuthMethodChange = (_, nextMethod) => {
			if (!nextMethod) {
				return;
			}
			onChange("_authMethod", nextMethod);
			const nextOption = options.find((option) => option.value === nextMethod);
			if (nextOption?.skipAuth) {
				onChange("skipAuth", true);
				return;
			}
			if (values.skipAuth) {
				onChange("skipAuth", false);
			}
		};

		const renderCredentialFields = () => {
			if (!activeOption || activeOption.skipAuth) {
				return null;
			}

			const credentialFields = getAuthCredentialFields(activeOption);
			const firstPasswordFieldName = credentialFields.find(
				(credentialField) => credentialField.fieldType === "password",
			)?.fieldName;

			return (
				<Stack spacing={2.5}>
					{credentialFields.map((credentialField) => {
						const credentialError = errors[credentialField.fieldName];
						const credentialHelperText = credentialError || undefined;
						const credentialRequired = Boolean(credentialField.required);
						const startIcon = getProtocolFieldStartIcon(credentialField.fieldName);
						const showPasswordEncryptAlert =
							showDeferredEncryptAlert &&
							credentialField.fieldType === "password" &&
							credentialField.fieldName === firstPasswordFieldName;

						if (credentialField.fieldType === "password") {
							return (
								<Box key={credentialField.fieldName}>
									<ProtocolFieldLabelRow
										label={credentialField.fieldLabel}
										required={credentialRequired}
									/>
									<ProtocolPasswordField
										label={undefined}
										hiddenLabel
										size="small"
										value={values[credentialField.fieldName] ?? ""}
										onChange={(next) => onChange(credentialField.fieldName, next)}
										error={Boolean(credentialError)}
										helperText={credentialHelperText}
										required={credentialRequired}
										allowReveal={allowPasswordReveal}
										deferEncryptUntilSave={deferEncryptUntilSave}
										startAdornment={startIcon || undefined}
									/>
									{showPasswordEncryptAlert ? (
										<DeferredPasswordEncryptAlert allowPasswordReveal={allowPasswordReveal} />
									) : null}
								</Box>
							);
						}

						if (credentialField.fieldType === "textarea") {
							return (
								<Box key={credentialField.fieldName}>
									<ProtocolFieldLabelRow
										label={credentialField.fieldLabel}
										required={credentialRequired}
									/>
									<TextField
										{...protocolTextFieldProps}
										fullWidth
										multiline
										minRows={4}
										placeholder={credentialField.placeholder}
										value={values[credentialField.fieldName] ?? ""}
										onChange={(e) => onChange(credentialField.fieldName, e.target.value)}
										error={Boolean(credentialError)}
										helperText={credentialHelperText}
										required={credentialRequired}
									/>
								</Box>
							);
						}

						return (
							<Box key={credentialField.fieldName}>
								<ProtocolFieldLabelRow
									label={credentialField.fieldLabel}
									required={credentialRequired}
								/>
								<TextField
									{...protocolTextFieldProps}
									fullWidth
									placeholder={credentialField.placeholder}
									value={values[credentialField.fieldName] ?? ""}
									onChange={(e) => onChange(credentialField.fieldName, e.target.value)}
									error={Boolean(credentialError)}
									helperText={credentialHelperText}
									required={credentialRequired}
									slotProps={{
										input: startIcon
											? {
													startAdornment: (
														<ProtocolFieldStartAdornment>{startIcon}</ProtocolFieldStartAdornment>
													),
												}
											: undefined,
									}}
								/>
							</Box>
						);
					})}
				</Stack>
			);
		};

		return (
			<Box key={field.name}>
				<ProtocolFieldLabelRow
					label={field.label}
					required={required}
					description={description}
					helpTooltip={fieldHelpTooltip(field)}
				/>
				<ToggleButtonGroup
					value={authMethod}
					exclusive
					onChange={handleAuthMethodChange}
					size="small"
					sx={{ mb: 2 }}
				>
					{options.map((option) => (
						<ToggleButton
							key={option.value}
							value={option.value}
							sx={guidedConfigChoiceToggleButtonSx}
						>
							{option.label}
						</ToggleButton>
					))}
				</ToggleButtonGroup>
				{renderCredentialFields()}
			</Box>
		);
	};

	const renderPasswordField = (field, showDeferredEncryptAlert = false) => {
		const value = values[field.name];
		const error = errors[field.name];
		const required = showRequiredMarker(field);
		const helperText = fieldErrorText(field.name);
		const description = fieldDescription(field);
		const startIcon = getProtocolFieldStartIcon(field.name);

		return (
			<Box key={field.name}>
				<ProtocolFieldLabelRow
					label={field.label}
					required={required}
					description={description}
					helpTooltip={fieldHelpTooltip(field)}
				/>
				<ProtocolPasswordField
					label={undefined}
					hiddenLabel
					size="small"
					value={value ?? ""}
					onChange={(next) => onChange(field.name, next)}
					error={Boolean(error)}
					helperText={helperText}
					required={required}
					allowReveal={allowPasswordReveal}
					deferEncryptUntilSave={deferEncryptUntilSave}
					startAdornment={startIcon || undefined}
				/>
				{showDeferredEncryptAlert ? (
					<DeferredPasswordEncryptAlert allowPasswordReveal={allowPasswordReveal} />
				) : null}
			</Box>
		);
	};

	const renderTextField = (field) => {
		const value = values[field.name];
		const isPort = field.name === "port";
		const isTimeout = field.name === "timeout";
		const fieldError = errors[field.name];
		const portInlineError =
			isPort && !fieldError
				? validatePortValue(value, { required: isRequired(field), label: field.label })
				: null;
		const timeoutInlineError =
			isTimeout && !fieldError
				? validateTimeoutValue(value, { required: isRequired(field), label: field.label })
				: null;
		const displayError = fieldError || portInlineError || timeoutInlineError;
		const required = showRequiredMarker(field);
		const helperText = displayError || undefined;
		const description = fieldDescription(field);
		const startAdornment = renderStartAdornment(field.name);

		return (
			<Box key={field.name}>
				<ProtocolFieldLabelRow
					label={field.label}
					required={required}
					description={description}
					helpTooltip={fieldHelpTooltip(field)}
				/>
				<TextField
					{...protocolTextFieldProps}
					fullWidth
					type={field.type === "number" && !isPort && !isTimeout ? "number" : "text"}
					value={value ?? ""}
					onChange={(e) =>
						onChange(field.name, isPort ? sanitizePortInput(e.target.value) : e.target.value)
					}
					error={Boolean(displayError)}
					helperText={helperText}
					required={required}
					slotProps={{
						htmlInput:
							isPort || field.type === "number"
								? { inputMode: "numeric", pattern: "[0-9]*" }
								: undefined,
						input: startAdornment ? { startAdornment } : undefined,
					}}
				/>
			</Box>
		);
	};

	const visibleFields = fields.filter((field) => !field.showIf || field.showIf(values));
	const sudoFields = visibleFields.filter((field) =>
		["sudoCommand", "useSudoCommands"].includes(field.name),
	);
	const useSudoField = visibleFields.find((field) => field.name === "useSudo");
	const advancedFields = visibleFields.filter((field) => field.advanced);
	const hasAdvancedValues = advancedFields.some(isAdvancedFieldConfigured);
	const [advancedOpen, setAdvancedOpen] = React.useState(hasAdvancedValues);
	const regularFields = visibleFields.filter(
		(field) =>
			!field.advanced && !["useSudo", "sudoCommand", "useSudoCommands"].includes(field.name),
	);

	React.useEffect(() => {
		if (hasAdvancedValues) {
			setAdvancedOpen(true);
		}
	}, [hasAdvancedValues]);

	const firstPasswordFieldName = (() => {
		const standalonePassword = [...regularFields, ...advancedFields].find(
			(field) => field.type === "password",
		)?.name;
		if (standalonePassword) {
			return standalonePassword;
		}
		const authField = [...regularFields, ...advancedFields].find(
			(field) => field.type === "authChoice",
		);
		if (!authField) {
			return undefined;
		}
		for (const option of authField.authOptions || []) {
			if (option.fieldType === "password") {
				return option.fieldName;
			}
			const nestedPassword = option.fields?.find(
				(credentialField) => credentialField.fieldType === "password",
			);
			if (nestedPassword) {
				return nestedPassword.fieldName;
			}
		}
		return undefined;
	})();
	const shouldShowDeferredEncryptAlert = (field) => {
		if (!deferEncryptUntilSave) {
			return false;
		}
		if (field.type === "password") {
			return field.name === firstPasswordFieldName;
		}
		if (field.type === "authChoice") {
			return (field.authOptions || []).some((option) => {
				if (option.fieldType === "password") {
					return true;
				}
				return option.fields?.some((credentialField) => credentialField.fieldType === "password");
			});
		}
		return false;
	};

	return (
		<Stack spacing={2.5} sx={protocolFieldStackSx}>
			{regularFields.map((field) => renderField(field, shouldShowDeferredEncryptAlert(field)))}

			{advancedFields.length > 0 ? (
				<Box>
					<Button
						variant="text"
						size="small"
						onClick={() => setAdvancedOpen((open) => !open)}
						endIcon={
							<ExpandMoreIcon
								sx={{
									transform: advancedOpen ? "rotate(180deg)" : "rotate(0deg)",
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
					<Collapse in={advancedOpen} unmountOnExit>
						<Stack spacing={2.5} sx={{ mt: 2 }}>
							{advancedFields.map((field) =>
								renderField(field, shouldShowDeferredEncryptAlert(field)),
							)}
						</Stack>
					</Collapse>
				</Box>
			) : null}

			{useSudoField ? (
				<Box>
					{renderBooleanField(useSudoField)}
					<Collapse in={Boolean(values.useSudo)} unmountOnExit>
						<Stack spacing={2.5} sx={{ mt: 2 }}>
							{sudoFields.map((field) => renderTextField(field))}
						</Stack>
					</Collapse>
				</Box>
			) : null}
		</Stack>
	);
};

export default GenericProtocolConfigForm;
