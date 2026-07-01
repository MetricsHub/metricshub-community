import * as React from "react";
import { Box, Checkbox, Chip, Stack, TablePagination, Tooltip, Typography } from "@mui/material";
import DragIndicatorIcon from "@mui/icons-material/DragIndicator";
import { DataGrid } from "@mui/x-data-grid";
import { hostsDataGridSx } from "./hosts-table-styles";
import TruncatedText from "../explorer/views/common/TruncatedText";
import {
	getHostDisplayName,
	getHostNames,
	getHostProtocolNames,
	isMultiHostConfig,
	buildMultiHostDerivedIds,
} from "./host-config-utils";
import { ProtocolChips } from "./ProtocolChip";
import { protocolChipSx } from "./protocol-chip-styles";
import { setHostDragData } from "./host-drag";

export { buildMultiHostDerivedIds };

/**
 * Aggregates host.up status counts per protocol across all declared hosts of
 * a single multi-host configuration.
 *
 * @param {string} hostId
 * @param {string[]} hostNames
 * @param {string[]} protocols
 * @param {Record<string, Record<string, 0 | 1 | null>>} healthByHostId
 * @returns {Record<string, { up: number; down: number; unknown: number }>}
 */
export const aggregateMultiHostHealth = (hostId, hostNames, protocols, healthByHostId = {}) => {
	/** @type {Record<string, { up: number; down: number; unknown: number }>} */
	const result = {};
	for (const protocol of protocols) {
		result[protocol] = { up: 0, down: 0, unknown: 0 };
	}
	hostNames.forEach((name, index) => {
		const derivedId = `${hostId}-${index + 1}-${name}`;
		const health = healthByHostId[derivedId] ?? {};
		for (const protocol of protocols) {
			const value = protocol in health ? health[protocol] : null;
			if (value === 1) {
				result[protocol].up++;
			} else if (value === 0) {
				result[protocol].down++;
			} else {
				result[protocol].unknown++;
			}
		}
	});
	return result;
};

/**
 * @param {string} hostId
 * @param {Record<string, unknown>} hostConfig
 * @returns {object}
 */
export const hostConfigToTableRow = (hostId, hostConfig) => {
	const hostType = hostConfig?.attributes?.["host.type"];
	const hostNames = getHostNames(hostConfig?.attributes?.["host.name"]);
	const multi = isMultiHostConfig(hostConfig);
	return {
		id: hostId,
		hostId,
		hostName: getHostDisplayName(hostId, hostConfig),
		hostNames,
		hostCount: multi ? hostNames.length : 1,
		isMultiHost: multi,
		hostType: hostType != null && String(hostType).trim() !== "" ? String(hostType) : "—",
		protocols: getHostProtocolNames(hostConfig),
	};
};

// ---------------------------------------------------------------------------
// Multi-host aggregate-status chip (one chip per protocol)
// ---------------------------------------------------------------------------

const COUNT_DOT_SX = {
	display: "inline-block",
	width: 8,
	height: 8,
	borderRadius: "50%",
	flexShrink: 0,
};

/**
 * Renders one chip per protocol summarising host.up counts across all hosts
 * in a multi-host configuration. Format: `<protocol>  🟢N 🔴M ⚪K`. Zero counts
 * are omitted. Tooltip explains each value.
 *
 * @param {object} props
 * @param {string[]} props.protocols
 * @param {Record<string, { up: number; down: number; unknown: number }>} props.aggregate
 */
const AggregateProtocolChips = ({ protocols, aggregate }) => {
	if (!protocols?.length) return null;
	return (
		<>
			{protocols.map((protocol) => {
				const a = aggregate?.[protocol] ?? { up: 0, down: 0, unknown: 0 };
				const tooltip = `${protocol}: ${a.up} up, ${a.down} down, ${a.unknown} unknown`;
				return (
					<Tooltip key={protocol} title={tooltip}>
						<Chip
							size="small"
							variant="outlined"
							sx={{
								...protocolChipSx(protocol),
								"&.MuiChip-outlined": { bgcolor: "transparent" },
							}}
							label={
								<Stack
									direction="row"
									spacing={0.75}
									alignItems="center"
									sx={{ display: "inline-flex" }}
								>
									<Box component="span">{protocol}</Box>
									{a.up > 0 && (
										<Stack direction="row" spacing={0.25} alignItems="center">
											<Box component="span" sx={{ ...COUNT_DOT_SX, bgcolor: "success.main" }} />
											<Typography variant="caption" component="span" fontWeight={600}>
												{a.up}
											</Typography>
										</Stack>
									)}
									{a.down > 0 && (
										<Stack direction="row" spacing={0.25} alignItems="center">
											<Box component="span" sx={{ ...COUNT_DOT_SX, bgcolor: "error.main" }} />
											<Typography variant="caption" component="span" fontWeight={600}>
												{a.down}
											</Typography>
										</Stack>
									)}
									{a.unknown > 0 && (
										<Stack direction="row" spacing={0.25} alignItems="center">
											<Box component="span" sx={{ ...COUNT_DOT_SX, bgcolor: "text.disabled" }} />
											<Typography variant="caption" component="span" fontWeight={600}>
												{a.unknown}
											</Typography>
										</Stack>
									)}
								</Stack>
							}
						/>
					</Tooltip>
				);
			})}
		</>
	);
};

// ---------------------------------------------------------------------------
// Columns
// ---------------------------------------------------------------------------

const baseColumns = [
	{
		field: "hostId",
		headerName: "Resource ID",
		flex: 1,
		minWidth: 120,
		renderCell: (params) => (
			<TruncatedText text={params.value}>
				<Typography variant="body2" component="span" fontWeight={500}>
					{params.value}
				</Typography>
			</TruncatedText>
		),
	},
	{
		field: "hostName",
		headerName: "host.name",
		flex: 1,
		minWidth: 140,
		renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
	},
	{
		field: "hostType",
		headerName: "host.type",
		flex: 0.8,
		minWidth: 100,
		renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
	},
	{
		field: "protocols",
		headerName: "Protocols",
		flex: 1.2,
		minWidth: 160,
		sortable: false,
		cellClassName: "hosts-resource-table-protocols-cell",
	},
];

/**
 * @param {object} args
 * @param {Record<string, Record<string, 0 | 1 | null>>} args.healthByHostId
 * @param {string} [args.dragSource]
 * @param {boolean} args.checkboxSelection
 * @param {string[]} args.selectedHostIds
 * @param {string[]} args.allRowIds
 * @param {(hostIds: string[]) => void} [args.onSelectedHostIdsChange]
 */
const buildColumns = ({
	healthByHostId,
	dragSource,
	checkboxSelection,
	selectedHostIds,
	allRowIds,
	onSelectedHostIdsChange,
}) => {
	// host.name column: per-row decision — text for single-host, "N hosts" chip for multi-host.
	const hostNameColumn = {
		...baseColumns[1],
		renderCell: (params) => {
			if (params.row.isMultiHost) {
				const count = params.row.hostCount || params.row.hostNames?.length || 0;
				return (
					<Chip
						size="medium"
						color="secondary"
						variant="outlined"
						label={`${count} ${count === 1 ? "host" : "hosts"}`}
						sx={{
							height: 32,
							"& .MuiChip-label": {
								px: 1.25,
								fontSize: "0.875rem",
								fontWeight: 500,
							},
						}}
					/>
				);
			}
			return <TruncatedText text={params.value}>{params.value}</TruncatedText>;
		},
	};

	// Protocols column: per-row decision — single status dot for single-host,
	// aggregate counts for multi-host.
	const protocolsColumn = {
		...baseColumns[3],
		renderCell: (params) => {
			const protocols = params.value || [];
			if (!protocols.length) {
				return (
					<Typography variant="body2" color="text.secondary">
						—
					</Typography>
				);
			}
			return (
				<Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5, py: 0.5, width: "100%" }}>
					{params.row.isMultiHost ? (
						<AggregateProtocolChips protocols={protocols} aggregate={params.row.aggregateHealth} />
					) : (
						<ProtocolChips
							protocols={protocols}
							healthByProtocol={healthByHostId[params.row.hostId]}
						/>
					)}
				</Box>
			);
		},
	};

	const cols = [baseColumns[0], hostNameColumn, baseColumns[2], protocolsColumn];

	const selectedSet = new Set(selectedHostIds);
	if (checkboxSelection) {
		const selectedCount = allRowIds.filter((id) => selectedSet.has(id)).length;
		const allSelected = allRowIds.length > 0 && selectedCount === allRowIds.length;
		const someSelected = selectedCount > 0 && !allSelected;
		cols.unshift({
			field: "__select",
			headerName: "",
			width: 44,
			sortable: false,
			disableColumnMenu: true,
			renderHeader: () => (
				<Checkbox
					size="small"
					checked={allSelected}
					indeterminate={someSelected}
					onChange={(_e, checked) => {
						if (checked) {
							// Union: keep prior selections, add all visible.
							const next = new Set(selectedSet);
							for (const id of allRowIds) next.add(id);
							onSelectedHostIdsChange?.([...next]);
						} else {
							// Difference: remove visible from prior selections.
							const visible = new Set(allRowIds);
							onSelectedHostIdsChange?.(selectedHostIds.filter((id) => !visible.has(id)));
						}
					}}
					inputProps={{ "aria-label": "Select all resources in this table" }}
				/>
			),
			renderCell: (params) => {
				const id = String(params.row.hostId || params.id);
				const checked = selectedSet.has(id);
				return (
					<Checkbox
						size="small"
						checked={checked}
						onClick={(e) => e.stopPropagation()}
						onChange={(_e, nextChecked) => {
							const next = new Set(selectedSet);
							if (nextChecked) {
								next.add(id);
							} else {
								next.delete(id);
							}
							onSelectedHostIdsChange?.([...next]);
						}}
						inputProps={{ "aria-label": `Select resource ${id}` }}
					/>
				);
			},
		});
	}

	if (!dragSource) {
		return cols;
	}
	return [
		{
			field: "__drag",
			headerName: "",
			width: 40,
			sortable: false,
			disableColumnMenu: true,
			renderCell: () => (
				<DragIndicatorIcon fontSize="small" sx={{ color: "text.disabled", cursor: "grab" }} />
			),
		},
		...cols,
	];
};

/**
 * Tabular list of hosts with ID, name, type, and configured protocols.
 *
 * @param {object} props
 * @param {Array<[string, Record<string, unknown>]>} [props.hostEntries] entries as [hostId, hostConfig]
 * @param {(hostId: string) => void} [props.onHostClick]
 * @param {string} [props.dragSource] group name or standalone sentinel; enables drag-and-drop
 * @param {Record<string, Record<string, 0 | 1 | null>>} [props.healthByHostId] protocol up/down per host
 * @param {boolean} [props.checkboxSelection]
 * @param {string[]} [props.selectedHostIds]
 * @param {(hostIds: string[]) => void} [props.onSelectedHostIdsChange]
 * @param {boolean} [props.paginated] show rows-per-page selector and page controls
 * @param {number} [props.page]
 * @param {(page: number) => void} [props.onPageChange]
 * @param {number} [props.rowsPerPage]
 * @param {(rowsPerPage: number) => void} [props.onRowsPerPageChange]
 */
const HostsResourceTable = ({
	hostEntries = [],
	onHostClick,
	dragSource,
	healthByHostId = {},
	checkboxSelection = false,
	selectedHostIds = [],
	onSelectedHostIdsChange,
	paginated = false,
	page: pageProp,
	onPageChange,
	rowsPerPage: rowsPerPageProp,
	onRowsPerPageChange,
}) => {
	const [internalPage, setInternalPage] = React.useState(0);
	const [internalRowsPerPage, setInternalRowsPerPage] = React.useState(10);
	const page = pageProp ?? internalPage;
	const rowsPerPage = rowsPerPageProp ?? internalRowsPerPage;
	const setPage = onPageChange ?? setInternalPage;
	const setRowsPerPage = onRowsPerPageChange ?? setInternalRowsPerPage;

	const entriesFingerprint = React.useMemo(
		() => hostEntries.map(([hostId]) => hostId).join(" "),
		[hostEntries],
	);

	React.useEffect(() => {
		if (pageProp !== undefined) {
			return;
		}
		setInternalPage(0);
	}, [entriesFingerprint, pageProp]);

	const paginatedEntries = React.useMemo(() => {
		if (!paginated) {
			return hostEntries;
		}
		const start = page * rowsPerPage;
		return hostEntries.slice(start, start + rowsPerPage);
	}, [hostEntries, paginated, page, rowsPerPage]);

	const rows = React.useMemo(
		() =>
			paginatedEntries.map(([hostId, hostConfig]) => {
				const row = hostConfigToTableRow(hostId, hostConfig);
				// Compute aggregate health for multi-host rows so the protocols cell
				// can render the per-protocol "🟢N 🔴M ⚪K" summary in the same table.
				if (row.isMultiHost) {
					row.aggregateHealth = aggregateMultiHostHealth(
						hostId,
						row.hostNames,
						row.protocols,
						healthByHostId,
					);
				}
				return row;
			}),
		[paginatedEntries, healthByHostId],
	);
	const allRowIds = React.useMemo(() => rows.map((r) => String(r.hostId || r.id)), [rows]);

	const columns = React.useMemo(
		() =>
			buildColumns({
				healthByHostId,
				dragSource,
				checkboxSelection,
				selectedHostIds,
				allRowIds,
				onSelectedHostIdsChange,
			}),
		[
			healthByHostId,
			dragSource,
			checkboxSelection,
			selectedHostIds,
			allRowIds,
			onSelectedHostIdsChange,
		],
	);

	const handleRowClick = React.useCallback(
		(params) => {
			if (onHostClick) {
				onHostClick(params.row.hostId);
			}
		},
		[onHostClick],
	);

	if (rows.length === 0) {
		return null;
	}

	return (
		<Box>
			<DataGrid
				rows={rows}
				columns={columns}
				onRowClick={handleRowClick}
				disableRowSelectionOnClick
				hideFooter
				autoHeight
				density="compact"
				getRowHeight={() => "auto"}
				slotProps={{
					row: dragSource
						? {
								draggable: true,
								onDragStart: (event) => {
									const hostId = event.currentTarget.getAttribute("data-id");
									if (!hostId) {
										return;
									}
									setHostDragData(event, dragSource, hostId);
								},
								sx: { cursor: "grab" },
							}
						: undefined,
				}}
				sx={{
					...hostsDataGridSx,
					"& .MuiDataGrid-row": {
						cursor: dragSource ? "grab" : onHostClick ? "pointer" : "default",
					},
				}}
			/>
			{paginated && hostEntries.length > 0 && (
				<TablePagination
					component="div"
					count={hostEntries.length}
					page={page}
					onPageChange={(_event, nextPage) => setPage(nextPage)}
					rowsPerPage={rowsPerPage}
					onRowsPerPageChange={(event) => {
						setRowsPerPage(parseInt(String(event.target.value), 10));
						setPage(0);
					}}
					rowsPerPageOptions={[5, 10, 25, 50]}
					labelRowsPerPage="Rows per page"
					sx={{ borderTop: 1, borderColor: "divider" }}
				/>
			)}
		</Box>
	);
};

export default React.memo(HostsResourceTable);
