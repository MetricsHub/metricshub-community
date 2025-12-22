import * as React from "react";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import { sectionTitleSx, dataGridSx } from "./table-styles";
import TruncatedText from "./TruncatedText";

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
	const allResources = React.useMemo(
		() => (Array.isArray(resources) ? resources : []),
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

	const columns = React.useMemo(() => {
		const cols = [
			{
				field: "name",
				headerName: "Key",
				flex: 1,
				renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
			},
			{
				field: "hostName",
				headerName: "host.name",
				flex: 1,
				valueGetter: (params) => params.row.attributes?.["host.name"] ?? "",
				renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
			},
			{
				field: "hostType",
				headerName: "host.type",
				flex: 1,
				valueGetter: (params) => params.row.attributes?.["host.type"] ?? "",
				renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
			},
		];
		if (showOsType) {
			cols.push({
				field: "osType",
				headerName: "os.type",
				flex: 1,
				valueGetter: (params) => params.row.attributes?.["os.type"] ?? "",
				renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
			});
		}
		return cols;
	}, [showOsType]);

	return (
		<Box>
			<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
				<NodeTypeIcons type="resource" />
				Resources
			</Typography>
			<DataGrid
				rows={allResources.map((r) => ({ id: r.name || r.key, ...r }))}
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
