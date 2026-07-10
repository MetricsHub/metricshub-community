import * as React from "react";
import { Autocomplete, Box, TextField, Typography } from "@mui/material";
import { compareLocale } from "../../utils/alphabetic-sort";
import { filledInputNoLabelSx, guidedConfigFieldLabelSx } from "./guided-config-form-primitives";
import { NO_RESOURCE_GROUP } from "./hosts-labels";

/**
 * Combobox to choose resource placement: no resource group or a named group.
 *
 * @param {object} props
 * @param {string[]} props.resourceGroups
 * @param {"standalone" | "group" | ""} props.targetType
 * @param {string} props.resourceGroup selected group when targetType is group
 * @param {(patch: { targetType: string; resourceGroup: string }) => void} props.onChange
 * @param {boolean} [props.error]
 * @param {string} [props.helperText]
 * @param {boolean} [props.labelsAbove] render the label above the field instead of a floating label
 */
const ResourceGroupPlacementCombobox = ({
	resourceGroups = [],
	targetType,
	resourceGroup,
	onChange,
	error = false,
	helperText,
	labelsAbove = false,
}) => {
	const options = React.useMemo(() => {
		const groups = [...(resourceGroups || [])].sort(compareLocale);
		return [NO_RESOURCE_GROUP, ...groups];
	}, [resourceGroups]);

	const selectedLabel =
		targetType === "standalone"
			? NO_RESOURCE_GROUP
			: targetType === "group"
				? String(resourceGroup || "").trim()
				: "";

	const selectedOption = options.includes(selectedLabel) ? selectedLabel : selectedLabel || null;

	const handleChange = (_event, newValue) => {
		if (!newValue) {
			onChange({ targetType: "", resourceGroup: "" });
			return;
		}
		if (newValue === NO_RESOURCE_GROUP) {
			onChange({ targetType: "standalone", resourceGroup: "" });
			return;
		}
		onChange({ targetType: "group", resourceGroup: newValue });
	};

	return (
		<Box>
			{labelsAbove ? (
				<Typography
					component="label"
					htmlFor="resource-group-placement"
					sx={{ ...guidedConfigFieldLabelSx, display: "block", mb: 0.75 }}
				>
					Resource group
					<Box component="span" sx={{ color: "error.main", ml: 0.25 }}>
						*
					</Box>
				</Typography>
			) : null}
			<Autocomplete
				options={options}
				value={selectedOption}
				onChange={handleChange}
				fullWidth
				renderInput={(params) => (
					<TextField
						{...params}
						id="resource-group-placement"
						label={labelsAbove ? undefined : "Resource group"}
						hiddenLabel={labelsAbove}
						size="small"
						required
						error={error}
						helperText={helperText || "Choose a resource group or no resource group"}
						placeholder="Select a resource group"
						sx={labelsAbove ? filledInputNoLabelSx : undefined}
					/>
				)}
			/>
		</Box>
	);
};

export default ResourceGroupPlacementCombobox;
