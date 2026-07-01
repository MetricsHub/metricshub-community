import * as React from "react";
import { Alert, Box, Button, Chip, Stack, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import HostsHostFilterBar from "./HostsHostFilterBar";
import HostsResourceTable from "./HostsResourceTable";
import { sortHostEntries } from "./hosts-filter-utils";
import { useHostsProtocolHealth } from "../../hooks/use-hosts-protocol-health";
import {
	collectProtocolsInGroup,
	getGroupResources,
	hostMatchesFilters,
	summarizeHostsSnapshot,
} from "./host-config-utils";
import { formatResourceCountChip, NO_RESOURCE_GROUP } from "./hosts-labels";
import HostsEntityTitle from "./HostsEntityTitle";

/**
 * List and manage resources with no resource group.
 *
 * @param {object} props
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot
 * @param {boolean} [props.busy]
 * @param {(hostId: string, hostConfig: Record<string, unknown>) => void} props.onOpenHost
 * @param {() => void} props.onAddHost
 * @param {(hostIds: string[]) => void} [props.onDeleteHosts]
 * @param {boolean} [props.embedded] tree layout: no local filter bar; use global search
 * @param {string} [props.globalSearch]
 */
const StandaloneHostsView = ({
	snapshot,
	busy = false,
	onOpenHost,
	onAddHost,
	onDeleteHosts,
	embedded = false,
	globalSearch = "",
}) => {
	const [searchQuery, setSearchQuery] = React.useState("");
	const [protocolFilter, setProtocolFilter] = React.useState("all");
	const [sortBy, setSortBy] = React.useState("name-asc");
	const [selectedHostIds, setSelectedHostIds] = React.useState([]);
	const [tablePage, setTablePage] = React.useState(0);
	const [tableRowsPerPage, setTableRowsPerPage] = React.useState(10);

	const clearFilters = React.useCallback(() => {
		setSearchQuery("");
		setProtocolFilter("all");
		setSortBy("name-asc");
	}, []);

	const resources = React.useMemo(
		() => getGroupResources({ resources: snapshot?.resources }),
		[snapshot?.resources],
	);

	const availableProtocols = React.useMemo(() => collectProtocolsInGroup(resources), [resources]);

	const effectiveSearch = embedded ? globalSearch : searchQuery;
	const effectiveProtocolFilter = embedded ? "all" : protocolFilter;

	const filteredHosts = React.useMemo(() => {
		const filtered = Object.entries(resources).filter(([hostId, hostConfig]) =>
			hostMatchesFilters(hostId, hostConfig, effectiveSearch, effectiveProtocolFilter),
		);
		return sortHostEntries(filtered, sortBy);
	}, [resources, effectiveSearch, effectiveProtocolFilter, sortBy]);

	const filteredHostFingerprint = React.useMemo(
		() => filteredHosts.map(([hostId]) => hostId).join("\u0000"),
		[filteredHosts],
	);

	React.useEffect(() => {
		setTablePage(0);
	}, [filteredHostFingerprint]);

	React.useEffect(() => {
		const visibleIds = new Set(filteredHosts.map(([hostId]) => hostId));
		setSelectedHostIds((prev) => prev.filter((hostId) => visibleIds.has(hostId)));
	}, [filteredHosts]);

	const totalHosts = Object.keys(resources).length;

	const displayedHostCount = React.useMemo(() => {
		if (filteredHosts.length === 0) {
			return 0;
		}
		const start = tablePage * tableRowsPerPage;
		return Math.min(tableRowsPerPage, Math.max(0, filteredHosts.length - start));
	}, [filteredHosts.length, tablePage, tableRowsPerPage]);

	const resourceCountChipLabel = React.useMemo(
		() =>
			formatResourceCountChip({
				displayed: displayedHostCount,
				filtered: filteredHosts.length,
				total: totalHosts,
			}),
		[displayedHostCount, filteredHosts.length, totalHosts],
	);
	const summary = React.useMemo(() => summarizeHostsSnapshot(snapshot), [snapshot]);

	const hostsForHealth = React.useMemo(
		() => summary.standaloneHosts.map((h) => ({ hostId: h.hostId, resourceGroup: null })),
		[summary.standaloneHosts],
	);
	const { healthByHostId } = useHostsProtocolHealth(hostsForHealth, totalHosts > 0);

	return (
		<Stack spacing={2}>
			<Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
				<HostsEntityTitle iconType="resource-group" compact>
					{NO_RESOURCE_GROUP}
				</HostsEntityTitle>
				<Chip size="small" label={resourceCountChipLabel} variant="outlined" />
				{onDeleteHosts && (
					<Button
						variant="outlined"
						color="error"
						size="small"
						startIcon={<DeleteOutlineIcon />}
						onClick={() => onDeleteHosts(selectedHostIds)}
						disabled={busy || selectedHostIds.length === 0}
						sx={{
							visibility: selectedHostIds.length > 0 ? "visible" : "hidden",
							minWidth: 110,
						}}
					>
						Delete ({selectedHostIds.length})
					</Button>
				)}
			</Stack>

			{!embedded && (
				<HostsHostFilterBar
					search={searchQuery}
					onSearchChange={setSearchQuery}
					protocolFilter={protocolFilter}
					onProtocolFilterChange={setProtocolFilter}
					sortBy={sortBy}
					onSortChange={setSortBy}
					availableProtocols={availableProtocols}
					onClearFilters={clearFilters}
					actions={
						<Button
							variant="contained"
							size="small"
							startIcon={<AddIcon />}
							onClick={onAddHost}
							disabled={busy}
						>
							Add resource
						</Button>
					}
				/>
			)}

			<Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
				{protocolFilter !== "all" && (
					<Chip
						size="small"
						label={`Protocol: ${protocolFilter}`}
						onDelete={() => setProtocolFilter("all")}
					/>
				)}
				{effectiveSearch.trim() && (
					<Chip
						size="small"
						label={`Search: "${effectiveSearch.trim()}"`}
						onDelete={() => (embedded ? undefined : setSearchQuery(""))}
					/>
				)}
				{sortBy !== "name-asc" && (
					<Chip size="small" label={`Sort: ${sortBy}`} onDelete={() => setSortBy("name-asc")} />
				)}
			</Stack>

			{totalHosts === 0 && (
				<Alert severity="info">
					No resources yet. Add a resource and choose {NO_RESOURCE_GROUP} placement in the wizard.
				</Alert>
			)}

			{totalHosts > 0 && filteredHosts.length === 0 && (
				<Alert severity="warning">No resources match the current filters.</Alert>
			)}

			{filteredHosts.length > 0 && (
				<HostsResourceTable
					hostEntries={filteredHosts}
					healthByHostId={healthByHostId}
					onHostClick={(hostId) => onOpenHost(hostId, resources[hostId])}
					checkboxSelection
					selectedHostIds={selectedHostIds}
					onSelectedHostIdsChange={setSelectedHostIds}
					paginated
					page={tablePage}
					onPageChange={setTablePage}
					rowsPerPage={tableRowsPerPage}
					onRowsPerPageChange={setTableRowsPerPage}
				/>
			)}

			{summary.standaloneCount === 0 && summary.groupCount > 0 && (
				<Typography variant="body2" color="text.secondary">
					All resources are organized in resource groups. Open a group from the resources overview.
				</Typography>
			)}
		</Stack>
	);
};

export default StandaloneHostsView;
