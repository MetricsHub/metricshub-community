import * as React from "react";
import { Autocomplete, Box, TextField, Typography } from "@mui/material";
import { compareLocale } from "../../utils/alphabetic-sort";
import { filledInputNoLabelSx, guidedConfigFieldLabelSx } from "./guided-config-form-primitives";
import { NO_RESOURCE_GROUP } from "./hosts-labels";

/**
 * Sentinel option that switches the field into new-group-name entry mode. The leading
 * "+" keeps it from colliding with a real resource group name.
 */
export const NEW_RESOURCE_GROUP = "+ New Resource Group";

/**
 * Combobox to choose resource placement: no resource group, an existing named group, or
 * a brand-new group (typed inline and confirmed with Enter — the group is created when the
 * resource is saved).
 *
 * @param {object} props
 * @param {string[]} props.resourceGroups
 * @param {"standalone" | "group" | ""} props.targetType
 * @param {string} props.resourceGroup selected group when targetType is group
 * @param {(patch: { targetType: string; resourceGroup: string }) => void} props.onChange
 * @param {(name: string) => void} [props.onCreateResourceGroup] persist a brand-new group
 *        immediately (so it appears in the tree); no-op / omitted → group is created on save
 * @param {boolean} [props.error]
 * @param {string} [props.helperText]
 * @param {boolean} [props.labelsAbove] render the label above the field instead of a floating label
 */
const ResourceGroupPlacementCombobox = ({
	resourceGroups = [],
	targetType,
	resourceGroup,
	onChange,
	onCreateResourceGroup,
	error = false,
	helperText,
	labelsAbove = false,
}) => {
	// Inline "create a new group" entry mode, entered by picking the NEW_RESOURCE_GROUP option.
	const [creating, setCreating] = React.useState(false);
	const [newName, setNewName] = React.useState("");

	const knownGroups = React.useMemo(() => {
		const groups = new Set(resourceGroups || []);
		// Include a just-typed group that isn't persisted yet so it shows up as selected.
		if (targetType === "group" && resourceGroup) {
			groups.add(resourceGroup);
		}
		return [...groups].sort(compareLocale);
	}, [resourceGroups, targetType, resourceGroup]);

	const options = React.useMemo(
		() => [NO_RESOURCE_GROUP, ...knownGroups, NEW_RESOURCE_GROUP],
		[knownGroups],
	);

	const selectedLabel =
		targetType === "standalone"
			? NO_RESOURCE_GROUP
			: targetType === "group"
				? String(resourceGroup || "").trim()
				: "";

	const selectedOption = options.includes(selectedLabel) ? selectedLabel : selectedLabel || null;

	const handleChange = (_event, newValue) => {
		if (newValue === NEW_RESOURCE_GROUP) {
			setNewName("");
			setCreating(true);
			return;
		}
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

	const commitNewGroup = () => {
		const trimmed = newName.trim();
		if (!trimmed) {
			return;
		}
		// Select it as the placement, then persist it right away so it shows up in the tree.
		// If no create handler is wired, the group is still materialized when the resource is
		// saved (the backend creates it on demand).
		onChange({ targetType: "group", resourceGroup: trimmed });
		if (onCreateResourceGroup) {
			Promise.resolve(onCreateResourceGroup(trimmed)).catch(() => {});
		}
		setCreating(false);
		setNewName("");
	};

	const cancelNewGroup = () => {
		setCreating(false);
		setNewName("");
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
			{creating ? (
				<TextField
					autoFocus
					id="resource-group-placement"
					label={labelsAbove ? undefined : "New resource group"}
					hiddenLabel={labelsAbove}
					size="small"
					required
					fullWidth
					value={newName}
					onChange={(e) => setNewName(e.target.value)}
					onKeyDown={(e) => {
						if (e.key === "Enter") {
							e.preventDefault();
							commitNewGroup();
						} else if (e.key === "Escape") {
							e.preventDefault();
							cancelNewGroup();
						}
					}}
					onBlur={() => (newName.trim() ? commitNewGroup() : cancelNewGroup())}
					error={error}
					helperText={
						helperText || "Type a name and press Enter to create the group. Esc to cancel."
					}
					placeholder="e.g. Production datacenter"
					sx={labelsAbove ? filledInputNoLabelSx : undefined}
				/>
			) : (
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
			)}
		</Box>
	);
};

export default ResourceGroupPlacementCombobox;
