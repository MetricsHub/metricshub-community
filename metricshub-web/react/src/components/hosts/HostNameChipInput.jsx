import * as React from "react";
import Autocomplete from "@mui/material/Autocomplete";
import { Box, Chip, Stack, TextField, Typography } from "@mui/material";
import { getHostNames } from "./host-config-utils";
import { filledInputNoLabelSx, guidedConfigFieldLabelSx } from "./guided-config-form-primitives";
import FieldHelpTooltip from "./FieldHelpTooltip";
import { HOST_NAME_UI } from "./protocol-definitions";

const HOST_NAME_CHIP_SX = (theme) => ({
	bgcolor: theme.palette.secondary.main,
	color: theme.palette.common.white,
	borderColor: theme.palette.secondary.main,
	height: 34,
	"& .MuiChip-label": {
		px: 1.25,
		fontSize: "1rem",
		fontWeight: 500,
	},
	"&:hover": {
		bgcolor: theme.palette.secondary.dark,
	},
	"& .MuiChip-deleteIcon": {
		color: theme.palette.common.white,
		fontSize: 18,
		"&:hover": { color: theme.palette.common.white },
	},
});

/**
 * Chip input for host.name. Single values remain a plain string; multiple values
 * are emitted as an alphabetized array. Chips render inside the text field.
 *
 * @param {object} props
 * @param {string | string[]} props.value
 * @param {(value: string | string[]) => void} props.onChange
 * @param {boolean} [props.error]
 * @param {React.ReactNode} [props.helperText]
 * @param {boolean} [props.staticLabel] render the label above the field instead of a floating label
 */
const HostNameChipInput = ({ value, onChange, error = false, helperText, staticLabel = false }) => {
	const committedNames = React.useMemo(() => {
		if (Array.isArray(value)) {
			return getHostNames(value);
		}
		const text = String(value ?? "");
		return /[;,]/.test(text) ? getHostNames(text) : [];
	}, [value]);

	const isChipMode = committedNames.length > 0;
	const [inputValue, setInputValue] = React.useState("");

	React.useEffect(() => {
		if (isChipMode) {
			setInputValue("");
			return;
		}
		setInputValue(String(value ?? ""));
	}, [isChipMode, value]);

	const emitNames = React.useCallback(
		(names, asTags = false) => {
			const nextNames = getHostNames(names);
			onChange(asTags ? nextNames : nextNames.length > 1 ? nextNames : nextNames[0] || "");
		},
		[onChange],
	);

	const commitInput = React.useCallback(() => {
		const next = inputValue.trim();
		if (!next) {
			return false;
		}
		emitNames([...committedNames, ...getHostNames(next)], true);
		setInputValue("");
		return true;
	}, [committedNames, emitNames, inputValue]);

	const handleAutocompleteChange = React.useCallback(
		(_event, newValue) => {
			const next = getHostNames(newValue);
			if (next.length === 0) {
				onChange("");
				setInputValue("");
				return;
			}
			emitNames(next, true);
		},
		[emitNames, onChange],
	);

	const handleInputChange = React.useCallback(
		(_event, newInputValue, reason) => {
			if (reason === "reset") {
				return;
			}
			if (!isChipMode && !/[;,]/.test(newInputValue)) {
				onChange(newInputValue);
				setInputValue(newInputValue);
				return;
			}
			if (/[;,]/.test(newInputValue)) {
				emitNames([...committedNames, ...getHostNames(newInputValue)], true);
				setInputValue("");
				return;
			}
			setInputValue(newInputValue);
		},
		[committedNames, emitNames, isChipMode, onChange],
	);

	const handlePaste = React.useCallback(
		(event) => {
			const pasted = event.clipboardData.getData("text");
			if (!/[;,]/.test(pasted) && !isChipMode) {
				return;
			}
			event.preventDefault();
			const pastedNames = getHostNames(pasted);
			if (pastedNames.length === 0) {
				return;
			}
			emitNames([...committedNames, ...pastedNames], true);
			setInputValue("");
		},
		[committedNames, emitNames, isChipMode],
	);

	const handleKeyDown = React.useCallback(
		(event) => {
			if (event.key === "Enter" || event.key === "," || event.key === ";") {
				if (!isChipMode && event.key === "Enter" && !inputValue.trim()) {
					return;
				}
				event.preventDefault();
				commitInput();
			}
		},
		[commitInput, inputValue, isChipMode],
	);

	const placeholder = isChipMode
		? "Add another host name and press Enter, comma, or semicolon"
		: "Type a host name";

	return (
		<Stack spacing={staticLabel ? 0 : 1}>
			{staticLabel ? (
				<Stack direction="row" alignItems="center" spacing={0.25} sx={{ mb: 0.75 }}>
					<Typography component="label" htmlFor="host-name-input" sx={guidedConfigFieldLabelSx}>
						{HOST_NAME_UI.fieldLabel}
						<Box component="span" sx={{ color: "error.main", ml: 0.25 }}>
							*
						</Box>
					</Typography>
					<FieldHelpTooltip
						title={`MetricsHub attribute: ${HOST_NAME_UI.attributeName}\n\n${HOST_NAME_UI.fieldHelper}`}
					/>
				</Stack>
			) : null}
			<Autocomplete
				multiple
				freeSolo
				disableClearable
				filterOptions={(options) => options}
				options={[]}
				value={committedNames}
				inputValue={isChipMode ? inputValue : String(value ?? "")}
				onChange={handleAutocompleteChange}
				onInputChange={handleInputChange}
				renderTags={(tagValue, getTagProps) =>
					tagValue.map((option, index) => {
						const { key, ...tagProps } = getTagProps({ index });
						return (
							<Chip
								key={key}
								label={option}
								size="medium"
								color="secondary"
								variant="filled"
								{...tagProps}
								sx={HOST_NAME_CHIP_SX}
							/>
						);
					})
				}
				renderInput={(params) => (
					<TextField
						{...params}
						id="host-name-input"
						label={staticLabel ? undefined : HOST_NAME_UI.fieldLabel}
						hiddenLabel={staticLabel}
						size="small"
						variant={staticLabel ? "filled" : "outlined"}
						placeholder={placeholder}
						error={error}
						helperText={helperText}
						required
						onPaste={handlePaste}
						onKeyDown={handleKeyDown}
						onBlur={() => {
							if (isChipMode) {
								commitInput();
							}
						}}
						sx={staticLabel ? filledInputNoLabelSx : undefined}
					/>
				)}
				slotProps={{
					popper: {
						sx: { display: "none" },
					},
				}}
				sx={{
					"& .MuiAutocomplete-inputRoot": {
						flexWrap: "wrap",
						gap: 0.75,
						alignItems: "center",
						...(isChipMode ? { py: 0.875 } : {}),
					},
					"& .MuiAutocomplete-input": {
						minWidth: 80,
						flexGrow: 1,
					},
				}}
			/>
		</Stack>
	);
};

export default React.memo(HostNameChipInput);
