import * as React from "react";
import { Autocomplete, TextField, CircularProgress, Box, Typography } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { paths } from "../../../paths";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { searchExplorer } from "../../../store/thunks/explorer-thunks";
import {
	selectSearchResults,
	selectSearchLoading,
	clearSearchResults,
} from "../../../store/slices/explorer-slice";

/**
 * Explorer Search component.
 * Provides an autocomplete search bar to navigate through the resource hierarchy.
 * Uses Redux for state management and debounced search.
 */
export default function ExplorerSearch() {
	const [open, setOpen] = React.useState(false);
	const [inputValue, setInputValue] = React.useState("");
	const [value, setValue] = React.useState(null);
	const navigate = useNavigate();
	const dispatch = useAppDispatch();
	const options = useAppSelector(selectSearchResults);
	const loading = useAppSelector(selectSearchLoading);

	React.useEffect(() => {
		if (inputValue.length < 2) {
			dispatch(clearSearchResults());
			return undefined;
		}

		const timer = setTimeout(() => {
			dispatch(searchExplorer(inputValue));
		}, 400);

		return () => {
			clearTimeout(timer);
		};
	}, [inputValue, dispatch]);

	/**
	 * Handles the selection of a search result.
	 * Parses the path to determine the correct navigation parameters.
	 *
	 * @param {object} event - The change event.
	 * @param {object} newValue - The selected option.
	 */
	const handleChange = React.useCallback(
		(event, newValue) => {
			// Reset value to null to allow re-selection of the same item
			setValue(null);

			if (!newValue) return;

			let { groupName, resourceName, name, type, path } = newValue;
			const lowerType = type?.toLowerCase();

			if (path) {
				// Heuristic: The path is constructed by appending the name to the parent path.
				// If we remove the name from the end of the path, we get the prefix.
				// This handles cases where the name itself contains slashes.
				let prefix = path;
				if (path.endsWith(name)) {
					prefix = path.substring(0, path.length - name.length);
				}
				// Remove trailing slash if present
				if (prefix.endsWith("/")) {
					prefix = prefix.substring(0, prefix.length - 1);
				}

				const parts = prefix.split("/");
				const len = parts.length;

				// parts[0] is Agent
				// parts[1] is Group (if grouped) or Resource (if top-level)

				if (
					lowerType === "monitor" ||
					lowerType === "monitor-type" ||
					lowerType === "monitor_type"
				) {
					// Top-level: Agent/Resource/Connector (3 parts) -> MonitorType is the name
					// Grouped: Agent/Group/Resource/Connector (4 parts) -> MonitorType is the name
					if (len === 3) {
						resourceName = parts[1];
					} else if (len === 4) {
						groupName = parts[1];
						resourceName = parts[2];
					}
				} else if (lowerType === "instance") {
					// Top-level: Agent/Resource/Connector/MonitorType (4 parts) -> Instance is the name
					// Grouped: Agent/Group/Resource/Connector/MonitorType (5 parts) -> Instance is the name
					// We need to navigate to the MonitorType view, so 'name' becomes the MonitorType name (last part of prefix)
					if (len === 4) {
						resourceName = parts[1];
						name = parts[3]; // MonitorType
					} else if (len === 5) {
						groupName = parts[1];
						resourceName = parts[2];
						name = parts[4]; // MonitorType
					}
				} else if (lowerType === "resource") {
					// Top-level: Agent (1 part) -> Resource is the name
					// Grouped: Agent/Group (2 parts) -> Resource is the name
					if (len === 2) {
						groupName = parts[1];
					}
				} else if (lowerType === "connector") {
					// Top-level: Agent/Resource (2 parts) -> Connector is the name
					// Grouped: Agent/Group/Resource (3 parts) -> Connector is the name
					if (len === 2) {
						resourceName = parts[1];
					} else if (len === 3) {
						groupName = parts[1];
						resourceName = parts[2];
					}
				}
			}

			switch (lowerType) {
				case "resource_group":
				case "resource-group":
					navigate(paths.explorerResourceGroup(name));
					break;
				case "resource":
					navigate(paths.explorerResource(groupName, name));
					break;
				case "connector":
					navigate(paths.explorerResource(groupName, resourceName) + `#${name}`);
					break;
				case "monitor":
				case "monitor-type":
				case "monitor_type":
					navigate(paths.explorerMonitorType(groupName, resourceName, name));
					break;
				case "instance":
					navigate(paths.explorerMonitorType(groupName, resourceName, name) + `#${newValue.name}`);
					break;
				default:
					console.warn("Unknown search result type:", type);
					break;
			}
		},
		[navigate],
	);

	return (
		<Autocomplete
			open={open}
			onOpen={() => setOpen(true)}
			onClose={() => setOpen(false)}
			value={value}
			isOptionEqualToValue={(option, value) =>
				option.name === value.name && option.type === value.type
			}
			getOptionLabel={(option) => option.name}
			options={options}
			loading={loading}
			onInputChange={(event, newInputValue) => {
				setInputValue(newInputValue);
			}}
			onChange={handleChange}
			renderInput={(params) => (
				<TextField
					{...params}
					label="Search..."
					size="small"
					InputProps={{
						...params.InputProps,
						endAdornment: (
							<React.Fragment>
								{loading ? <CircularProgress color="inherit" size={20} /> : null}
								{params.InputProps.endAdornment}
							</React.Fragment>
						),
					}}
				/>
			)}
			renderOption={(props, option) => {
				const { key, ...otherProps } = props;
				return (
					<li key={key} {...otherProps}>
						<Box>
							<Typography variant="body1">{option.name}</Typography>
							<Typography variant="caption" color="text.secondary">
								{option.type} {option.path ? `- ${option.path}` : ""}
							</Typography>
						</Box>
					</li>
				);
			}}
		/>
	);
}
