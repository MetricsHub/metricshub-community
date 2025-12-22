import * as React from "react";
import { Autocomplete, TextField, CircularProgress, Box, Typography } from "@mui/material";
import { useNavigate } from "react-router-dom";
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
const ExplorerSearch = () => {
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

			if (newValue.path) {
				// Handle connector redirection
				// Pattern 1: /explorer/resources/:resource/connectors/:connectorId
				const resourceConnectorMatch = newValue.path.match(
					/^\/explorer\/resources\/([^/]+)\/connectors\/([^/]+)$/,
				);
				if (resourceConnectorMatch) {
					const [, resource, connectorId] = resourceConnectorMatch;
					navigate(`/explorer/resources/${resource}#${connectorId}`);
					return;
				}

				// Pattern 2: /explorer/resource-groups/:group/resources/:resource/connectors/:connectorId
				const groupResourceConnectorMatch = newValue.path.match(
					/^\/explorer\/resource-groups\/([^/]+)\/resources\/([^/]+)\/connectors\/([^/]+)$/,
				);
				if (groupResourceConnectorMatch) {
					const [, group, resource, connectorId] = groupResourceConnectorMatch;
					navigate(`/explorer/resource-groups/${group}/resources/${resource}#${connectorId}`);
					return;
				}

				navigate(newValue.path);
			}
		},
		[navigate],
	);

	return (
		<Autocomplete
			noOptionsText={inputValue.length < 2 ? "Start typing to search..." : "No result"}
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
};

export default React.memo(ExplorerSearch);
