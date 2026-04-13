import * as React from "react";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import { sectionTitleSx, dataGridSx } from "./table-styles";
import TruncatedText from "./TruncatedText";

const COLUMNS_BASE = [
	{
		field: "name",
		headerName: "Key",
		flex: 1,
		renderCell: (params) => (
			<Box sx={{ display: "flex", alignItems: "center", gap: 1, width: "100%" }}>
				<NodeTypeIcons type="resource" />
				<TruncatedText text={params.value}>{params.value}</TruncatedText>
			</Box>
		),
	},
	{
		field: "hostName",
		headerName: "host.name",
		flex: 1,
		valueGetter: (value, row) => row.attributes?.["host.name"] ?? "",
		renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
	},
	{
		field: "hostType",
		headerName: "host.type",
		flex: 1,
		valueGetter: (value, row) => row.attributes?.["host.type"] ?? "",
		renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
	},
];

const COLUMNS_WITH_OS = [
	...COLUMNS_BASE,
	{
		field: "osType",
		headerName: "os.type",
		flex: 1,
		valueGetter: (value, row) => row.attributes?.["os.type"] ?? "",
		renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
	},
];

/**
 * Table displaying resources.
 *
 * @param {object} props - Component props
 * @param {Array<{ name: string, attributes?: Record<string, unknown> }>} [props.resources] - List of resources to display
 * @param {(resource: any) => void} [props.onResourceClick] - Callback for row click
 * @param {boolean} [props.showOsType=false] - Whether to show the OS type column
 * @returns {JSX.Element}
 */
const ResourcesTable = ({ resources, onResourceClick, showOsType = false }) => {
	const rows = React.useMemo(
		() => (Array.isArray(resources) ? resources.map((r) => ({ id: r.name || r.key, ...r })) : []),
		[resources],
	);

	const handleRowClick = React.useCallback(
		(params) => {
			if (onResourceClick) {
				onResourceClick(params.row);
			}
		},
		[onResourceClick],
	);

	const columns = showOsType ? COLUMNS_WITH_OS : COLUMNS_BASE;

	return (
		<Box>
			<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
				<NodeTypeIcons type="resource" />
				Resources
			</Typography>
			<DataGrid
				rows={rows}
				columns={columns}
				onRowClick={handleRowClick}
				disableRowSelectionOnClick
				hideFooter
				autoHeight
				density="compact"
				sx={{
					...dataGridSx,
					"& .MuiDataGrid-row": {
						cursor: onResourceClick ? "pointer" : "default",
					},
				}}
			/>
		</Box>
	);
};

export default React.memo(ResourcesTable);
