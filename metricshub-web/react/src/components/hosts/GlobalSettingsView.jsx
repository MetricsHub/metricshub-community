import * as React from "react";
import { useLocation } from "react-router-dom";
import {
	Accordion,
	AccordionDetails,
	AccordionSummary,
	Box,
	Divider,
	Paper,
	Step,
	StepButton,
	Stepper,
	Typography,
	useTheme,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import DomainIcon from "@mui/icons-material/Domain";
import { DataGrid } from "@mui/x-data-grid";
import NodeTypeIcons from "../explorer/tree/icons/NodeTypeIcons";
import TruncatedText from "../explorer/views/common/TruncatedText";
import { flattenToRows, summarizeHostsSnapshot } from "./host-config-utils";
import { dataGridSx } from "../explorer/views/common/table-styles";
import { EXPLORER_METRIC_FONT_FAMILY } from "../../utils/metric-font";
import { HOST_CONFIG_STEP_RAIL_WIDTH } from "./host-config-form-layout";

const KEY_VALUE_COLUMNS = [
	{
		field: "property",
		headerName: "PROPERTY",
		flex: 1,
		minWidth: 200,
		renderCell: (params) => (
			<Box component="span" sx={{ fontFamily: EXPLORER_METRIC_FONT_FAMILY, fontSize: "0.85rem" }}>
				{params.value}
			</Box>
		),
	},
	{ field: "value", headerName: "VALUE", flex: 2, minWidth: 200 },
];

// Columns matching the explorer's ResourcesTable exactly
const STANDALONE_COLUMNS = [
	{
		field: "hostId",
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
		renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
	},
	{
		field: "hostType",
		headerName: "host.type",
		flex: 1,
		renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
	},
];

const STANDALONE_GRID_SX = {
	...dataGridSx,
	"& .MuiDataGrid-row": {
		...dataGridSx["& .MuiDataGrid-row"],
		cursor: "pointer",
	},
};

// ---------------------------------------------------------------------------
// Section definitions (drive both the content and the right-side stepper)
// ---------------------------------------------------------------------------

const SECTIONS = [
	{ id: "license", label: "License" },
	{ id: "logger-level", label: "Logger level" },
	{ id: "output-directory", label: "Output directory" },
	{ id: "collect-periods", label: "Collect periods" },
	{ id: "discovery-cycle", label: "Discovery cycle" },
	{ id: "alerting-system", label: "Alerting system configuration" },
	{ id: "sequential", label: "Sequential" },
	{ id: "self-monitoring", label: "Enable self-monitoring" },
	{ id: "resolve-fqdn", label: "Resolve hostname to FQDN" },
	{ id: "monitor-filters", label: "Monitor filters" },
	{ id: "job-timeout", label: "Job timeout" },
	{ id: "attributes", label: "Attributes" },
	{ id: "metrics", label: "Metrics" },
	{ id: "resources", label: "Resources" },
	{ id: "resource-groups", label: "Resource groups" },
	{ id: "enrichments", label: "Enrichments" },
	{ id: "state-set-compression", label: "State set compression" },
];

// ---------------------------------------------------------------------------
// Titled section (replaces the previous top-level accordions)
// ---------------------------------------------------------------------------

const SettingsSection = ({ id, label, registerRef, last = false, children }) => (
	<Box id={id} ref={registerRef(id)} sx={{ scrollMarginTop: 88 }}>
		<Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>
			{label}
		</Typography>
		{children ?? (
			<Typography variant="body2" color="text.secondary" sx={{ fontStyle: "italic" }}>
				Not configured
			</Typography>
		)}
		{!last && <Divider sx={{ mt: 2.5 }} />}
	</Box>
);

// ---------------------------------------------------------------------------
// DataGrid with gray header + transparent rows (used inside resource groups)
// ---------------------------------------------------------------------------

const ConfigGridSection = ({ title, rows, emptyLabel }) => {
	const theme = useTheme();
	const headerBackground =
		theme.palette.mode === "dark" ? theme.palette.neutral[800] : theme.palette.neutral[100];
	const gridSx = React.useMemo(
		() => ({
			...dataGridSx,
			"& .MuiDataGrid-columnHeaders": {
				...dataGridSx["& .MuiDataGrid-columnHeaders"],
				backgroundColor: headerBackground,
			},
			"& .MuiDataGrid-row": {
				backgroundColor: "transparent",
				"&:hover": { backgroundColor: "action.hover" },
			},
			"& .MuiDataGrid-cell": {
				...dataGridSx["& .MuiDataGrid-cell"],
				backgroundColor: "transparent",
			},
		}),
		[headerBackground],
	);

	if (rows.length > 0) {
		return (
			<Box>
				<Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
					{title}
				</Typography>
				<DataGrid
					rows={rows}
					columns={KEY_VALUE_COLUMNS}
					disableRowSelectionOnClick
					hideFooter
					autoHeight
					density="compact"
					sx={gridSx}
				/>
			</Box>
		);
	}
	return (
		<Typography variant="body2" color="text.secondary" sx={{ fontStyle: "italic" }}>
			{emptyLabel}
		</Typography>
	);
};

// ---------------------------------------------------------------------------
// Resource-group accordion — nested MonitorAccordion style
// ---------------------------------------------------------------------------

const ResourceGroupAccordion = ({ name, node, onConfigure }) => {
	const attributeRows = React.useMemo(() => flattenToRows(node?.attributes), [node?.attributes]);
	const metricRows = React.useMemo(() => flattenToRows(node?.metrics), [node?.metrics]);

	return (
		<Accordion
			disableGutters
			elevation={0}
			square
			sx={{
				bgcolor: "transparent",
				borderTop: "1px solid",
				borderColor: "divider",
				"&:before": { display: "none" },
				"& .MuiAccordionDetails-root": { bgcolor: "transparent" },
			}}
		>
			<AccordionSummary
				expandIcon={<ExpandMoreIcon />}
				sx={{
					minHeight: 40,
					bgcolor: "transparent",
					pl: 1,
					transition: "background-color 0.4s ease",
					"&:hover": { bgcolor: "action.hover" },
					"& .MuiAccordionSummary-content": { my: 0, ml: 0 },
				}}
			>
				<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
					<DomainIcon color="primary" sx={{ fontSize: 18 }} />
					<Typography
						variant="subtitle1"
						component="span"
						onClick={(e) => {
							e.stopPropagation();
							onConfigure(name);
						}}
						onKeyDown={(e) => {
							if (e.key === "Enter" || e.key === " ") {
								e.stopPropagation();
								e.preventDefault();
								onConfigure(name);
							}
						}}
						role="button"
						tabIndex={0}
						sx={{
							fontWeight: 500,
							cursor: "pointer",
							color: "primary.main",
							"&:hover": { textDecoration: "underline" },
						}}
					>
						{name}
					</Typography>
				</Box>
			</AccordionSummary>
			<AccordionDetails
				sx={{ pl: 4, pr: 1.5, py: 1, display: "flex", flexDirection: "column", gap: 2 }}
			>
				<ConfigGridSection title="Attributes" rows={attributeRows} emptyLabel="No attributes" />
				<ConfigGridSection title="Metrics" rows={metricRows} emptyLabel="No metrics" />
			</AccordionDetails>
		</Accordion>
	);
};

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

/**
 * Global Settings body: titled sections on the left, vertical section
 * stepper on the right (same layout as the host wizard).
 *
 * @param {object} props
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot
 * @param {(groupName: string) => void} props.onConfigureGroup navigates to the group detail page
 * @param {(hostId: string) => void} [props.onOpenStandaloneHost] navigates to a standalone resource page
 */
const GlobalSettingsView = ({ snapshot, onConfigureGroup, onOpenStandaloneHost }) => {
	const location = useLocation();
	const summary = React.useMemo(() => summarizeHostsSnapshot(snapshot), [snapshot]);
	const [activeSection, setActiveSection] = React.useState(SECTIONS[0].id);
	const sectionRefs = React.useRef({});
	const suspendSpyRef = React.useRef(false);

	const registerRef = (id) => (el) => {
		sectionRefs.current[id] = el;
	};

	// Scroll spy: highlight the last section whose top passed the viewport threshold.
	React.useEffect(() => {
		const onScroll = () => {
			if (suspendSpyRef.current) return;
			let current = SECTIONS[0].id;
			for (const s of SECTIONS) {
				const el = sectionRefs.current[s.id];
				if (el && el.getBoundingClientRect().top <= 120) {
					current = s.id;
				}
			}
			setActiveSection(current);
		};
		window.addEventListener("scroll", onScroll, { passive: true });
		onScroll();
		return () => window.removeEventListener("scroll", onScroll);
	}, []);

	const scrollToSection = (id) => {
		setActiveSection(id);
		// Keep the clicked step highlighted while the smooth scroll runs.
		suspendSpyRef.current = true;
		sectionRefs.current[id]?.scrollIntoView({ behavior: "smooth", block: "start" });
		window.setTimeout(() => {
			suspendSpyRef.current = false;
		}, 700);
	};

	React.useEffect(() => {
		const hash = location.hash?.replace(/^#/, "");
		if (!hash || !sectionRefs.current[hash]) {
			return undefined;
		}
		const timer = window.setTimeout(() => scrollToSection(hash), 50);
		return () => window.clearTimeout(timer);
	}, [location.hash]);

	const activeIndex = Math.max(
		0,
		SECTIONS.findIndex((s) => s.id === activeSection),
	);

	const standaloneRows = React.useMemo(
		() =>
			summary.standaloneHosts.map((h) => ({
				id: h.hostId,
				hostId: h.hostId,
				hostName: h.displayName,
				hostType: h.hostType ?? "",
			})),
		[summary.standaloneHosts],
	);

	const sectionContent = {
		resources:
			summary.standaloneCount > 0 ? (
				<DataGrid
					rows={standaloneRows}
					columns={STANDALONE_COLUMNS}
					onRowClick={(params) => onOpenStandaloneHost?.(params.row.hostId)}
					disableRowSelectionOnClick
					hideFooter
					autoHeight
					density="compact"
					sx={STANDALONE_GRID_SX}
				/>
			) : null,
		"resource-groups":
			summary.groupCount === 0 ? null : (
				<Box sx={{ display: "flex", flexDirection: "column" }}>
					{summary.groups.map((group) => (
						<ResourceGroupAccordion
							key={group.name}
							name={group.name}
							node={group.node}
							onConfigure={onConfigureGroup}
						/>
					))}
				</Box>
			),
	};

	return (
		<Paper variant="outlined" sx={{ borderRadius: 1, bgcolor: "transparent", overflow: "hidden" }}>
			<Box sx={{ display: "flex" }}>
				{/* ── LEFT — titled sections ── */}
				<Box
					sx={{
						flex: 1,
						minWidth: 0,
						display: "flex",
						flexDirection: "column",
						gap: 2.5,
						p: 3,
					}}
				>
					{SECTIONS.map((section, index) => (
						<SettingsSection
							key={section.id}
							id={section.id}
							label={section.label}
							registerRef={registerRef}
							last={index === SECTIONS.length - 1}
						>
							{sectionContent[section.id] ?? null}
						</SettingsSection>
					))}
				</Box>

				{/* ── RIGHT — section stepper ── */}
				<Box
					sx={{
						width: HOST_CONFIG_STEP_RAIL_WIDTH,
						flexShrink: 0,
						borderLeft: 1,
						borderColor: "divider",
						display: { xs: "none", md: "block" },
					}}
				>
					<Box sx={{ position: "sticky", top: 80, p: 2.5 }}>
						<Typography variant="h6" fontWeight={700} sx={{ mb: 2.5 }}>
							Agent configuration
						</Typography>
						<Stepper nonLinear orientation="vertical" activeStep={activeIndex}>
							{SECTIONS.map((section) => (
								<Step key={section.id} completed={false}>
									<StepButton color="inherit" onClick={() => scrollToSection(section.id)}>
										{section.label}
									</StepButton>
								</Step>
							))}
						</Stepper>
					</Box>
				</Box>
			</Box>
		</Paper>
	);
};

export default GlobalSettingsView;
