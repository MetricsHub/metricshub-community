import React, { memo, useMemo } from "react";
import PropTypes from "prop-types";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import { dataGridSx } from "../explorer/views/common/table-styles";

const BASE_COLUMNS = [
	{ field: "property", headerName: "PROPERTY", flex: 1, minWidth: 200 },
	{ field: "value", headerName: "VALUE", flex: 2, minWidth: 200 },
];

/**
 * Reusable key-value table component using DataGrid.
 *
 * @param {object} props
 * @param {string} props.title
 * @param {{ id: string; property: string; value: string }[]} props.rows
 * @param {Record<string, (value: string) => React.ReactNode>} [props.customRenderers]
 *   Optional map from a row's `property` value to a custom cell renderer function.
 *   When provided the matching row's VALUE cell renders the returned node instead
 *   of plain text — useful for chip lists, badges, etc.
 */
const KeyValueTable = memo(({ title, rows, customRenderers }) => {
	// Hooks must be called unconditionally — before any early return.
	const hasCustom = Boolean(customRenderers && Object.keys(customRenderers).length > 0);

	const columns = useMemo(() => {
		if (!hasCustom) {
			return BASE_COLUMNS;
		}
		return [
			BASE_COLUMNS[0],
			{
				...BASE_COLUMNS[1],
				renderCell: (params) => {
					const renderer = customRenderers[params.row.property];
					return renderer ? renderer(params.row.value) : params.value;
				},
			},
		];
	}, [hasCustom, customRenderers]);

	// Auto-size rows that have a custom renderer so chip lists can wrap.
	const getRowHeight = useMemo(() => {
		if (!hasCustom) {
			return undefined;
		}
		return (params) => (customRenderers[params.model.property] ? "auto" : null);
	}, [hasCustom, customRenderers]);

	if (!rows || rows.length === 0) return null;

	return (
		<Box>
			<Typography variant="h6" sx={{ mb: 2 }}>
				{title}
			</Typography>
			<DataGrid
				rows={rows}
				columns={columns}
				disableRowSelectionOnClick
				hideFooter
				autoHeight
				density="compact"
				getRowHeight={getRowHeight}
				sx={dataGridSx}
			/>
		</Box>
	);
});

KeyValueTable.displayName = "KeyValueTable";

KeyValueTable.propTypes = {
	title: PropTypes.string.isRequired,
	rows: PropTypes.arrayOf(
		PropTypes.shape({
			id: PropTypes.string.isRequired,
			property: PropTypes.string.isRequired,
			value: PropTypes.string.isRequired,
		}),
	),
	customRenderers: PropTypes.objectOf(PropTypes.func),
};

export default KeyValueTable;
