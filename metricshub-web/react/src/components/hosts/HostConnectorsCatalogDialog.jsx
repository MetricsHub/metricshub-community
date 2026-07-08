import * as React from "react";
import {
	Accordion,
	AccordionDetails,
	AccordionSummary,
	Box,
	Button,
	Chip,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	InputAdornment,
	Stack,
	Table,
	TableBody,
	TableCell,
	TableContainer,
	TableHead,
	TableRow,
	TextField,
	Tooltip,
	Typography,
} from "@mui/material";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import HighlightOffIcon from "@mui/icons-material/HighlightOff";
import SearchIcon from "@mui/icons-material/Search";
import ConnectorDocumentationLink from "./ConnectorDocumentationLink";
import { collectCatalogFilterOptions, connectorMatchesCatalogFilters } from "./connector-utils";
import { guidedConfigPlaceholderSx } from "./guided-config-form-primitives";
import { formatHostTypeLabel } from "./protocol-definitions";

/** lg (1200px) + 400px for a wider connectors table. */
const DIALOG_MAX_WIDTH_PX = 1600;

/** Fixed column widths for the catalog table (tableLayout: fixed). */
const CATALOG_TABLE_COLUMNS = {
	compat: 48,
	name: 380,
	id: 220,
	tags: 140,
	hostType: 110,
	protocols: 110,
	platforms: 140,
};

const FILTER_DIMENSIONS = [
	{ key: "tags", label: "Tags", accordionLabel: "Tags" },
	{ key: "platforms", label: "Platform", accordionLabel: "Platforms" },
	{ key: "protocols", label: "Protocol", accordionLabel: "Protocols" },
	{ key: "hostTypes", label: "host.type", accordionLabel: "host.type" },
];

const FILTER_ACCORDION_SX = {
	border: 1,
	borderColor: "divider",
	borderRadius: 1,
	bgcolor: "transparent",
	boxShadow: "none",
	"&:before": { display: "none" },
	// No Mui-expanded margin override: disableGutters already suppresses the default
	// expansion margins, and overriding margin here would also cancel the parent
	// Stack's spacing, collapsing the gap above an expanded accordion.
};

/**
 * @returns {{ tags: Set<string>; platforms: Set<string>; protocols: Set<string>; hostTypes: Set<string> }}
 */
const createEmptyCatalogFilters = () => ({
	tags: new Set(),
	platforms: new Set(),
	protocols: new Set(),
	hostTypes: new Set(),
});

/**
 * @param {string} dimensionKey
 * @param {string} value
 * @returns {string}
 */
const formatFilterValue = (dimensionKey, value) =>
	dimensionKey === "hostTypes" ? formatHostTypeLabel(value) : value;

/**
 * Full connector catalog with compatibility explanations and multi-dimension filters.
 *
 * @param {object} props
 * @param {boolean} props.open
 * @param {() => void} props.onClose
 * @param {object[]} props.connectors
 * @param {string} props.hostType
 * @param {string[]} props.configuredProtocols
 */
const HostConnectorsCatalogDialog = ({
	open,
	onClose,
	connectors = [],
	hostType = "",
	configuredProtocols = [],
}) => {
	const [searchQuery, setSearchQuery] = React.useState("");
	const [selectedFilters, setSelectedFilters] = React.useState(createEmptyCatalogFilters);

	React.useEffect(() => {
		if (!open) {
			setSearchQuery("");
			setSelectedFilters(createEmptyCatalogFilters());
		}
	}, [open]);

	const filterOptions = React.useMemo(() => collectCatalogFilterOptions(connectors), [connectors]);

	const filteredConnectors = React.useMemo(
		() =>
			connectors.filter((item) =>
				connectorMatchesCatalogFilters(item, searchQuery, selectedFilters),
			),
		[connectors, searchQuery, selectedFilters],
	);

	const activeFilterChips = React.useMemo(() => {
		/** @type {{ dimensionKey: string; dimensionLabel: string; value: string; displayValue: string }[]} */
		const chips = [];
		for (const dimension of FILTER_DIMENSIONS) {
			for (const value of selectedFilters[dimension.key]) {
				chips.push({
					dimensionKey: dimension.key,
					dimensionLabel: dimension.label,
					value,
					displayValue: formatFilterValue(dimension.key, value),
				});
			}
		}
		return chips;
	}, [selectedFilters]);

	const hasActiveFilters = activeFilterChips.length > 0;

	const toggleFilterValue = (dimensionKey, value) => {
		setSelectedFilters((prev) => {
			const next = { ...prev, [dimensionKey]: new Set(prev[dimensionKey]) };
			if (next[dimensionKey].has(value)) {
				next[dimensionKey].delete(value);
			} else {
				next[dimensionKey].add(value);
			}
			return next;
		});
	};

	const removeFilterValue = (dimensionKey, value) => {
		setSelectedFilters((prev) => {
			const next = { ...prev, [dimensionKey]: new Set(prev[dimensionKey]) };
			next[dimensionKey].delete(value);
			return next;
		});
	};

	const clearAllFilters = () => {
		setSelectedFilters(createEmptyCatalogFilters());
	};

	return (
		<Dialog
			open={open}
			onClose={onClose}
			fullWidth
			maxWidth={false}
			sx={{
				"& .MuiDialog-paper": {
					width: "100%",
					maxWidth: DIALOG_MAX_WIDTH_PX,
				},
			}}
		>
			<DialogTitle>All connectors</DialogTitle>
			<DialogContent>
				<Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
					Compatibility is evaluated for host.type <strong>{formatHostTypeLabel(hostType)}</strong>{" "}
					and protocols{" "}
					<strong>{configuredProtocols.length > 0 ? configuredProtocols.join(", ") : "—"}</strong>.
					Connectors with automatic detection disabled are listed when they match this resource.
				</Typography>

				<Box sx={{ mb: 2 }}>
					<Typography variant="caption" color="text.secondary" sx={{ mb: 0.75, display: "block" }}>
						Search
					</Typography>
					<TextField
						size="small"
						hiddenLabel
						placeholder="Search by name, ID, or tag…"
						value={searchQuery}
						onChange={(e) => setSearchQuery(e.target.value)}
						fullWidth
						sx={guidedConfigPlaceholderSx}
						InputProps={{
							startAdornment: (
								<InputAdornment position="start">
									<SearchIcon fontSize="small" color="action" />
								</InputAdornment>
							),
						}}
					/>
				</Box>

				<Box
					sx={{
						display: "flex",
						flexDirection: { xs: "column", md: "row" },
						gap: 2,
						alignItems: "stretch",
						minHeight: 0,
					}}
				>
					<Box
						sx={{
							width: { xs: "100%", md: 280 },
							flexShrink: 0,
							borderRight: { xs: 0, md: 1 },
							borderBottom: { xs: 1, md: 0 },
							borderColor: "divider",
							pr: { xs: 0, md: 2 },
							pb: { xs: 2, md: 0 },
							display: "flex",
							flexDirection: "column",
							maxHeight: { xs: 320, md: 560 },
							overflow: "auto",
						}}
					>
						{hasActiveFilters ? (
							<Box sx={{ mb: 1.5 }}>
								<Typography
									variant="caption"
									color="text.secondary"
									sx={{ mb: 0.75, display: "block" }}
								>
									Active filters
								</Typography>
								<Stack direction="row" flexWrap="wrap" gap={0.75}>
									{activeFilterChips.map(
										({ dimensionKey, dimensionLabel, value, displayValue }) => (
											<Chip
												key={`${dimensionKey}:${value}`}
												label={`${dimensionLabel}: ${displayValue}`}
												size="small"
												color="primary"
												onDelete={() => removeFilterValue(dimensionKey, value)}
												sx={{
													maxWidth: "100%",
													"& .MuiChip-label": {
														overflow: "hidden",
														textOverflow: "ellipsis",
													},
												}}
											/>
										),
									)}
									<Chip
										label="Clear all"
										size="small"
										variant="outlined"
										onClick={clearAllFilters}
									/>
								</Stack>
							</Box>
						) : null}

						<Stack spacing={0.75}>
							{FILTER_DIMENSIONS.map((dimension) => {
								const values = filterOptions[dimension.key] || [];
								const selectedInDimension = selectedFilters[dimension.key];
								return (
									<Accordion key={dimension.key} disableGutters sx={FILTER_ACCORDION_SX}>
										<AccordionSummary
											expandIcon={<ExpandMoreIcon fontSize="small" />}
											sx={{
												minHeight: 40,
												"& .MuiAccordionSummary-content": { my: 0.75 },
											}}
										>
											<Typography variant="body2" fontWeight={600}>
												{dimension.accordionLabel}
												{selectedInDimension.size > 0 ? (
													<Box
														component="span"
														sx={{ ml: 0.75, color: "primary.main", fontWeight: 700 }}
													>
														({selectedInDimension.size})
													</Box>
												) : null}
											</Typography>
										</AccordionSummary>
										<AccordionDetails sx={{ pt: 0, pb: 1.25 }}>
											{values.length === 0 ? (
												<Typography variant="caption" color="text.secondary">
													No values in the catalog.
												</Typography>
											) : (
												<Stack direction="row" flexWrap="wrap" gap={0.75}>
													{values.map((value) => {
														const isSelected = selectedInDimension.has(value);
														return (
															<Chip
																key={value}
																label={formatFilterValue(dimension.key, value)}
																size="small"
																variant={isSelected ? "filled" : "outlined"}
																color={isSelected ? "primary" : "default"}
																onClick={() => toggleFilterValue(dimension.key, value)}
															/>
														);
													})}
												</Stack>
											)}
										</AccordionDetails>
									</Accordion>
								);
							})}
						</Stack>
					</Box>

					<Box sx={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
						<Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: "block" }}>
							{filteredConnectors.length} shown · {connectors.length} total
						</Typography>
						<TableContainer sx={{ flex: 1, maxHeight: { xs: 360, md: 540 } }}>
							<Table size="small" stickyHeader sx={{ tableLayout: "fixed" }}>
								<TableHead>
									<TableRow>
										<TableCell width={CATALOG_TABLE_COLUMNS.compat} />
										<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.name }}>Name</TableCell>
										<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.id }}>ID</TableCell>
										<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.tags }}>Tags</TableCell>
										<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.hostType }}>host.type</TableCell>
										<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.protocols }}>Protocols</TableCell>
										<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.platforms }}>Platforms</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{filteredConnectors.length === 0 ? (
										<TableRow>
											<TableCell colSpan={7}>
												<Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
													No connectors match your search or filters.
												</Typography>
											</TableCell>
										</TableRow>
									) : (
										filteredConnectors.map((item) => {
											const id = String(item.id || "");
											const appliesTo =
												item.appliesToHostTypes?.length > 0
													? item.appliesToHostTypes.map(formatHostTypeLabel).join(", ")
													: "—";
											const protocols =
												item.requiredProtocols?.length > 0
													? item.requiredProtocols.join(", ")
													: "—";
											const reasons = item.incompatibilityReasons || [];
											return (
												<TableRow key={id} hover>
													<TableCell>
														<Tooltip
															title={
																item.compatible
																	? "Compatible with this resource"
																	: reasons.join(" ") || "Not compatible"
															}
														>
															{item.compatible ? (
																<CheckCircleOutlineIcon fontSize="small" color="success" />
															) : (
																<HighlightOffIcon fontSize="small" color="disabled" />
															)}
														</Tooltip>
													</TableCell>
													<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.name }}>
														<Stack
															direction="row"
															spacing={0.5}
															alignItems="flex-start"
															sx={{ minWidth: 0 }}
														>
															<ConnectorDocumentationLink connectorId={id} iconOnly />
															<Box sx={{ minWidth: 0, flex: 1 }}>
																<Typography variant="body2" noWrap title={item.displayName || id}>
																	{item.displayName || id}
																</Typography>
																{!item.compatible && reasons.length > 0 && (
																	<Typography
																		variant="caption"
																		color="text.secondary"
																		noWrap
																		title={reasons[0]}
																		display="block"
																	>
																		{reasons[0]}
																	</Typography>
																)}
															</Box>
														</Stack>
													</TableCell>
													<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.id }}>
														<Typography variant="body2" fontFamily="monospace" noWrap title={id}>
															{id}
														</Typography>
													</TableCell>
													<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.tags }}>
														<Stack direction="row" flexWrap="wrap" gap={0.5}>
															{(item.tags || []).length === 0 ? (
																<Typography variant="caption" color="text.secondary">
																	—
																</Typography>
															) : (
																item.tags.map((tag) => (
																	<Chip key={tag} label={tag} size="small" variant="outlined" />
																))
															)}
														</Stack>
													</TableCell>
													<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.hostType }}>
														{appliesTo}
													</TableCell>
													<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.protocols }}>
														{protocols}
													</TableCell>
													<TableCell sx={{ width: CATALOG_TABLE_COLUMNS.platforms }}>
														<Stack direction="row" flexWrap="wrap" gap={0.5}>
															{(item.platforms || []).length === 0 ? (
																<Typography variant="caption" color="text.secondary">
																	—
																</Typography>
															) : (
																item.platforms.map((p) => (
																	<Chip key={p} label={p} size="small" variant="outlined" />
																))
															)}
														</Stack>
													</TableCell>
												</TableRow>
											);
										})
									)}
								</TableBody>
							</Table>
						</TableContainer>
					</Box>
				</Box>
			</DialogContent>
			<DialogActions>
				<Button onClick={onClose}>Close</Button>
			</DialogActions>
		</Dialog>
	);
};

export default HostConnectorsCatalogDialog;
