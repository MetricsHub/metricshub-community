import * as React from "react";
import {
	Accordion,
	AccordionDetails,
	AccordionSummary,
	Alert,
	Box,
	Button,
	Chip,
	Stack,
	Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import BarChartIcon from "@mui/icons-material/BarChart";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import LabelOutlinedIcon from "@mui/icons-material/LabelOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import GroupConfigSection from "./GroupConfigSection";
import HostsResourceTable, { buildMultiHostDerivedIds } from "./HostsResourceTable";
import { sortHostEntries } from "./hosts-filter-utils";
import { useHostsProtocolHealth } from "../../hooks/use-hosts-protocol-health";
import {
	flattenToRows,
	getGroupResources,
	getHostNames,
	isMultiHostConfig,
	summarizeHostsSnapshot,
} from "./host-config-utils";
import { formatResourceCountChip } from "./hosts-labels";
import HostsEntityTitle from "./HostsEntityTitle";

/**
 * Accordion panel for one resource group on the overview page.
 *
 * @param {object} props
 * @param {{ name: string, node: Record<string, unknown>, hostCount: number }} props.group
 * @param {boolean} [props.defaultExpanded]
 * @param {Record<string, Record<string, 0 | 1 | null>>} props.healthByHostId
 * @param {boolean} [props.busy]
 * @param {() => void} props.onOpenGroup
 * @param {(hostId: string) => void} props.onOpenHost
 * @param {() => void} [props.onConfigureGroup]
 * @param {() => void} props.onAddHost
 * @param {() => void} props.onDeleteGroup
 */
const ResourceGroupOverviewAccordion = ({
	group,
	defaultExpanded = false,
	healthByHostId,
	busy = false,
	onOpenGroup,
	onOpenHost,
	onConfigureGroup,
	onAddHost,
	onDeleteGroup,
}) => {
	const resources = React.useMemo(() => getGroupResources(group.node), [group.node]);
	const hostEntries = React.useMemo(
		() => sortHostEntries(Object.entries(resources), "name-asc"),
		[resources],
	);

	const attributeRows = React.useMemo(
		() => flattenToRows(group.node?.attributes),
		[group.node?.attributes],
	);
	const metricRows = React.useMemo(() => flattenToRows(group.node?.metrics), [group.node?.metrics]);

	const resourceCountChipLabel = React.useMemo(
		() =>
			formatResourceCountChip({
				displayed: hostEntries.length,
				filtered: hostEntries.length,
				total: group.hostCount,
			}),
		[group.hostCount, hostEntries.length],
	);

	return (
		<Accordion
			defaultExpanded={defaultExpanded}
			variant="outlined"
			disableGutters
			sx={{
				bgcolor: "transparent",
				"&:before": { display: "none" },
				"& .MuiAccordionDetails-root": { bgcolor: "transparent" },
			}}
		>
			<AccordionSummary
				expandIcon={<ExpandMoreIcon />}
				sx={{
					bgcolor: "action.hover",
					transition: "background-color 0.4s ease",
					"&:hover": { bgcolor: "action.selected" },
					"& .MuiAccordionSummary-content": { my: 1 },
				}}
			>
				<Stack
					direction="row"
					alignItems="center"
					justifyContent="space-between"
					spacing={1}
					useFlexGap
					flexWrap="wrap"
					sx={{ width: "100%", pr: 1 }}
				>
					<Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
						<HostsEntityTitle iconType="resource-group" compact>
							{group.name}
						</HostsEntityTitle>
						<Chip size="small" label={resourceCountChipLabel} variant="outlined" />
					</Stack>
					<Button
						variant="text"
						size="small"
						startIcon={<OpenInNewIcon fontSize="small" />}
						onClick={(event) => {
							event.stopPropagation();
							onOpenGroup();
						}}
						sx={{ flexShrink: 0 }}
					>
						Open
					</Button>
				</Stack>
			</AccordionSummary>

			<AccordionDetails sx={{ p: 2, bgcolor: "transparent" }}>
				<Stack spacing={2}>
					<Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
						{onConfigureGroup && (
							<Button
								variant="outlined"
								size="small"
								startIcon={<EditOutlinedIcon />}
								onClick={onConfigureGroup}
								disabled={busy}
							>
								Modify
							</Button>
						)}
						<Button
							variant="contained"
							size="small"
							startIcon={<AddIcon />}
							onClick={onAddHost}
							disabled={busy}
						>
							Add resource
						</Button>
						<Button
							variant="outlined"
							color="error"
							size="small"
							startIcon={<DeleteOutlineIcon />}
							onClick={onDeleteGroup}
							disabled={busy}
						>
							Delete group
						</Button>
					</Stack>

					<GroupConfigSection
						title="Group Attributes"
						icon={<LabelOutlinedIcon fontSize="small" />}
						rows={attributeRows}
					/>

					<GroupConfigSection
						title="Group Metrics"
						icon={<BarChartIcon fontSize="small" />}
						rows={metricRows}
						monospaceProperty
					/>

					<Accordion
						defaultExpanded
						variant="outlined"
						disableGutters
						sx={{
							bgcolor: "transparent",
							"&:before": { display: "none" },
							"& .MuiAccordionDetails-root": { bgcolor: "transparent" },
						}}
					>
						<AccordionSummary
							expandIcon={<ExpandMoreIcon />}
							sx={{
								bgcolor: "action.hover",
								transition: "background-color 0.4s ease",
								"&:hover": { bgcolor: "action.selected" },
								"& .MuiAccordionSummary-content": { my: 1 },
							}}
						>
							<Stack direction="row" alignItems="center" spacing={1}>
								<DnsOutlinedIcon fontSize="small" />
								<Typography fontWeight={600}>Resources</Typography>
							</Stack>
						</AccordionSummary>

						<AccordionDetails sx={{ p: 0, bgcolor: "transparent" }}>
							{group.hostCount === 0 ? (
								<Box sx={{ p: 2 }}>
									<Alert severity="info">
										This resource group has no resources yet. Use Add resource to create one.
									</Alert>
								</Box>
							) : (
								<HostsResourceTable
									hostEntries={hostEntries}
									healthByHostId={healthByHostId}
									onHostClick={onOpenHost}
								/>
							)}
						</AccordionDetails>
					</Accordion>
				</Stack>
			</AccordionDetails>
		</Accordion>
	);
};

/**
 * Landing view for the resource groups page: one accordion per group with
 * attributes, metrics, and resources.
 *
 * @param {object} props
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot
 * @param {boolean} [props.busy]
 * @param {(groupName: string) => void} props.onOpenGroup
 * @param {(groupName: string, hostId: string) => void} props.onOpenHost
 * @param {(groupName: string) => void} [props.onConfigureGroup]
 * @param {(groupName: string) => void} props.onAddHostToGroup
 * @param {(groupName: string, hostCount: number) => void} props.onDeleteGroup
 */
const ResourceGroupsOverviewView = ({
	snapshot,
	busy = false,
	onOpenGroup,
	onOpenHost,
	onConfigureGroup,
	onAddHostToGroup,
	onDeleteGroup,
}) => {
	const summary = React.useMemo(() => summarizeHostsSnapshot(snapshot), [snapshot]);

	const hostsForHealth = React.useMemo(() => {
		/** @type {Array<{ hostId: string; resourceGroup: string }>} */
		const list = [];
		for (const group of summary.groups) {
			const resources = getGroupResources(group.node);
			for (const [hostId, hostConfig] of Object.entries(resources)) {
				if (isMultiHostConfig(hostConfig)) {
					const names = getHostNames(hostConfig?.attributes?.["host.name"]);
					for (const derivedId of buildMultiHostDerivedIds(hostId, names)) {
						list.push({ hostId: derivedId, resourceGroup: group.name });
					}
				} else {
					list.push({ hostId, resourceGroup: group.name });
				}
			}
		}
		return list;
	}, [summary.groups]);

	const { healthByHostId } = useHostsProtocolHealth(hostsForHealth, summary.groupedHostCount > 0);

	if (summary.groupCount === 0) {
		return (
			<Alert severity="info">
				No resource groups yet. Use <strong>New group</strong> in the sidebar to create one.
			</Alert>
		);
	}

	return (
		<Stack spacing={2}>
			<Typography variant="body2" color="text.secondary">
				{summary.groupCount} resource group{summary.groupCount === 1 ? "" : "s"},{" "}
				{summary.groupedHostCount} grouped resource
				{summary.groupedHostCount === 1 ? "" : "s"}. Expand a group to review its configuration and
				resources.
			</Typography>

			<Stack spacing={1.5}>
				{summary.groups.map((group, index) => (
					<ResourceGroupOverviewAccordion
						key={group.name}
						group={group}
						defaultExpanded={index === 0}
						healthByHostId={healthByHostId}
						busy={busy}
						onOpenGroup={() => onOpenGroup(group.name)}
						onOpenHost={(hostId) => onOpenHost(group.name, hostId)}
						onConfigureGroup={onConfigureGroup ? () => onConfigureGroup(group.name) : undefined}
						onAddHost={() => onAddHostToGroup(group.name)}
						onDeleteGroup={() => onDeleteGroup(group.name, group.hostCount)}
					/>
				))}
			</Stack>
		</Stack>
	);
};

export default ResourceGroupsOverviewView;
