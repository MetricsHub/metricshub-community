import * as React from "react";
import {
	Alert,
	Box,
	Button,
	Chip,
	CircularProgress,
	IconButton,
	Stack,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
	Tooltip,
	Typography,
} from "@mui/material";
import { DataGrid, GridRow, useGridApiRef } from "@mui/x-data-grid";
import { alpha, useTheme } from "@mui/material/styles";
import InputAdornment from "@mui/material/InputAdornment";
import AddIcon from "@mui/icons-material/Add";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import SearchIcon from "@mui/icons-material/Search";
import SettingsInputHdmiIcon from "@mui/icons-material/SettingsInputHdmi";
import { ConfigurableConnectorIcon } from "./connector-row-icons";
import ConnectorConfigurationDialog from "./ConnectorConfigurationDialog";
import ConnectorDocumentationLink from "./ConnectorDocumentationLink";
import FieldHelpTooltip from "./FieldHelpTooltip";
import HostConnectorsCatalogDialog from "./HostConnectorsCatalogDialog";
import {
	guidedConfigChoiceToggleButtonSx,
	guidedConfigFieldLabelSx,
	guidedConfigPlaceholderSx,
} from "./guided-config-form-primitives";
import {
	guidedConfigOutlinedPrimaryHoverSx,
	guidedConfigPanelAlertSx,
	guidedConfigRowHoverBg,
} from "./guided-config-ui-tokens";
import { dataGridSx } from "../explorer/views/common/table-styles";
import {
	getAdditionalConnectorChipLabel,
	getAdditionalConnectorChipTooltip,
	getDirectiveChipLabel,
	getDirectiveChipSx,
	getDirectiveChipTooltip,
	DIRECTIVE_CHIP_DELETE_ICON_SX,
	getDirectiveChipDeleteIconSx,
} from "./connector-directive-ui";
import {
	applyAdditionalConnectorsChange,
	collectCatalogTags,
	CONNECTOR_CATEGORY_TABS,
	connectorMatchesCategoryTab,
	connectorMatchesListFilters,
	dedupeConnectorCatalogById,
	filterDirectivesForAdditionalConnectorChips,
	findCatalogConnectorById,
	getConnectorDirectiveRowKey,
	formatInlineConnectorsText,
	getConnectorListDescription,
	getAdditionalConnectorSelectionKind,
	getConnectorSelectionKind,
	shouldDisableConnectorExcludes,
	parseConnectorDirective,
	parseInlineConnectorsText,
	reconcileExcludeDirectives,
	removeConnectorDirectiveByRaw,
	removeDirectivesForValue,
	upsertConnectorDirective,
} from "./connector-utils";
import {
	retryOnAnimationFrames,
	scrollDataGridToRowIndex,
	scrollTableRowBelowStickyHeader,
} from "./host-config-scroll-spy";
import { compareLocale } from "../../utils/alphabetic-sort";
import { EXPLORER_METRIC_FONT_FAMILY } from "../../utils/metric-font";

const CONNECTOR_KINDS = ["force", "exclude", "select"];
const TAG_DIRECTIVE_KINDS = ["include-tag", "exclude-tag"];
const CONFIGURATION_ROW_INDENT_PX = 40;
const CONNECTOR_ROW_ICON_SIZE = 28;
const ROW_TYPE_CONNECTOR = "connector";
const ROW_TYPE_CONFIGURATION = "configuration";

/**
 * Single fixed height shared by every row type so the table stays evenly aligned.
 * Must be applied through getRowHeight (used as-is), not the rowHeight prop, which
 * the DataGrid scales down by the compact-density factor.
 */
const CONNECTOR_GRID_ROW_HEIGHT = 88;
const CONNECTOR_DESCRIPTION_MAX_LINES = 2;

const CONNECTOR_ROW_DESCRIPTION_SX = {
	display: "-webkit-box",
	lineHeight: 1.35,
	overflow: "hidden",
	textOverflow: "ellipsis",
	WebkitLineClamp: CONNECTOR_DESCRIPTION_MAX_LINES,
	WebkitBoxOrient: "vertical",
};

const ROW_FOCUS_SX = { outline: 2, outlineColor: "primary.main", outlineOffset: -2 };
const CONNECTOR_ROW_ICON_BOX_SX = {
	width: CONNECTOR_ROW_ICON_SIZE,
	height: CONNECTOR_ROW_ICON_SIZE,
	borderRadius: 1,
	bgcolor: "action.hover",
	display: "flex",
	alignItems: "center",
	justifyContent: "center",
	flexShrink: 0,
};
/** Keeps the directives panel height stable when switching Directives / Code views. */
const DIRECTIVES_PANEL_BODY_HEIGHT = 72;

const CONNECTORS_LIST_TABLE_HEIGHT = 560;

/** Connectors table column sizing (~60% / 15% / 7% / 22% at typical widths). */
const CONNECTOR_COLUMN_FLEX = 6;
const CONNECTOR_ID_COLUMN_FLEX = 1.5;
const CONNECTOR_ACTIONS_COLUMN_FLEX = 2.5;
const CONNECTOR_CONFIGURATION_COLUMN_WIDTH = 72;

const CONNECTORS_GRID_CONTAINER_SX = {
	height: CONNECTORS_LIST_TABLE_HEIGHT,
	minHeight: CONNECTORS_LIST_TABLE_HEIGHT,
	maxHeight: CONNECTORS_LIST_TABLE_HEIGHT,
	overflow: "hidden",
};

/** How long the "you reached the top" glow stays visible after a wheel-up at the top. */
const TOP_EDGE_PULSE_MS = 500;

const buildConnectorsGridSx = (theme) => ({
	...dataGridSx,
	height: "100%",
	border: 0,
	borderRadius: 0,
	"& .MuiDataGrid-virtualScroller": {
		...dataGridSx["& .MuiDataGrid-virtualScroller"],
		// Keep wheel events at the table boundary from scrolling the whole page.
		overscrollBehavior: "contain",
		boxShadow: "none",
		transition: "box-shadow 240ms ease",
	},
	"&.connectors-grid-top-edge-pulse .MuiDataGrid-virtualScroller": {
		boxShadow: `inset 0 16px 12px -12px ${alpha(theme.palette.primary.main, 0.55)}`,
	},
	"& .MuiDataGrid-columnHeaderTitle": {
		fontWeight: 600,
	},
	"& .MuiDataGrid-cell": {
		...dataGridSx["& .MuiDataGrid-cell"],
		py: 1.25,
		lineHeight: "normal",
	},
	"& .MuiDataGrid-cell[data-field='connector']": {
		alignItems: "stretch",
	},
	"& .MuiDataGrid-cell[data-field='connector'] > *": {
		height: "100%",
		width: "100%",
	},
	"& .MuiDataGrid-cell > *": {
		minWidth: 0,
	},
	"& .connector-grid-row-focused": ROW_FOCUS_SX,
	"& .connector-grid-row-active-emphasis": {
		bgcolor: guidedConfigRowHoverBg(theme, true, false),
		"&:hover": {
			bgcolor: guidedConfigRowHoverBg(theme, true, true),
		},
	},
	"& .connector-grid-row-active": {
		bgcolor: guidedConfigRowHoverBg(theme, false, false),
		"&:hover": {
			bgcolor: guidedConfigRowHoverBg(theme, false, true),
		},
	},
});

const DIRECTIVES_PANEL_BODY_SX = {
	height: DIRECTIVES_PANEL_BODY_HEIGHT,
	minHeight: DIRECTIVES_PANEL_BODY_HEIGHT,
	overflow: "auto",
};

const CONNECTORS_DIRECTIVES_HELP =
	"This section controls which connectors MetricsHub uses for this resource when manual selection is enabled.\n\n" +
	"Pick connectors in the table below, or switch to Code to edit directives directly.\n\n" +
	"Examples:\n" +
	"• Linux — select a connector\n" +
	"• +Windows — force a connector\n" +
	"• !Solaris — exclude a connector\n" +
	"• #HPE — include connectors tagged HPE\n" +
	"• !#System — exclude connectors tagged System";

const directivesContentBoxSx = (showError) => ({
	...DIRECTIVES_PANEL_BODY_SX,
	border: 1,
	borderColor: showError ? "error.main" : "divider",
	borderRadius: 1,
	p: 1,
	bgcolor: "transparent",
});

const DIRECTIVES_CODE_FIELD_SX = {
	...guidedConfigPlaceholderSx,
	"& .MuiInputBase-root": {
		height: 36,
		alignItems: "center",
	},
	"& .MuiInputBase-input": {
		fontFamily: EXPLORER_METRIC_FONT_FAMILY,
		fontSize: "0.85rem",
		py: 0,
	},
};

const DIRECTIVES_VIEW_TOGGLE_SX = {
	...guidedConfigChoiceToggleButtonSx,
	px: 1.25,
	py: 0.25,
	minHeight: 28,
	fontSize: "0.75rem",
	lineHeight: 1.2,
};

const DIRECTIVE_CHIP_SX = {
	height: 30,
	fontSize: "0.8125rem",
	fontWeight: 600,
	"& .MuiChip-label": { px: 1.25 },
	cursor: "pointer",
};

const getRowKeyFromDirective = (directive) => {
	const parsed = parseConnectorDirective(directive);
	return parsed.kind === "include-tag" || parsed.kind === "exclude-tag"
		? `tag:${parsed.value}`
		: `connector:${parsed.value}`;
};

/**
 * @param {string[]} directives
 * @param {string} tag
 * @returns {'include-tag' | 'exclude-tag' | 'none'}
 */
const getTagSelectionKind = (directives, tag) => {
	const target = String(tag ?? "")
		.trim()
		.toLowerCase();
	if (!target) {
		return "none";
	}
	for (const directive of directives || []) {
		const parsed = parseConnectorDirective(directive);
		if (parsed.kind !== "include-tag" && parsed.kind !== "exclude-tag") {
			continue;
		}
		if (parsed.value.toLowerCase() === target) {
			return parsed.kind;
		}
	}
	return "none";
};

/** Browser-style tab (rounded top, filled when active) for the category strip. */
const CATEGORY_BROWSER_TAB_SX = {
	textTransform: "none",
	fontSize: "0.8125rem",
	fontWeight: 600,
	lineHeight: 1.2,
	letterSpacing: 0,
	px: 1.75,
	py: 0.75,
	minHeight: 34,
	color: "text.secondary",
	bgcolor: "transparent",
	border: 0,
	borderRadius: "10px 10px 0 0",
	"&:hover": {
		bgcolor: "action.hover",
		color: "text.primary",
	},
	"&.Mui-selected": {
		bgcolor: "action.selected",
		color: "text.primary",
		"&:hover": {
			bgcolor: "action.selected",
		},
	},
};

const CATEGORY_TAB_STRIP_GROUP_SX = {
	minWidth: 0,
	flexWrap: "wrap",
	alignItems: "flex-end",
	gap: 0.25,
	// Neutralize the grouped-button chrome so each button keeps its tab shape.
	"& .MuiToggleButtonGroup-grouped": {
		border: 0,
		m: 0,
		"&:not(:first-of-type)": {
			borderRadius: "10px 10px 0 0",
			borderLeft: 0,
			m: 0,
		},
		"&:first-of-type": {
			borderRadius: "10px 10px 0 0",
		},
	},
};

const TAG_GRID_ROW_HEIGHT = 56;

const DIRECTIVE_FORCE_TOGGLE_SX = {
	...guidedConfigChoiceToggleButtonSx,
	"&.Mui-selected": {
		bgcolor: "text.primary",
		color: "background.paper",
		"&:hover": {
			bgcolor: "text.secondary",
		},
	},
};

const DIRECTIVE_EXCLUDE_TOGGLE_SX = {
	...guidedConfigChoiceToggleButtonSx,
	"&.Mui-selected": {
		bgcolor: "warning.main",
		color: "common.black",
		"&:hover": {
			bgcolor: "warning.dark",
		},
	},
};

const ConnectorDirectiveToggleGroup = ({ value, onChange, excludeDisabled, allowNone = true }) => (
	<ToggleButtonGroup
		exclusive
		size="small"
		value={value === "none" ? null : value}
		onChange={(_, next) => {
			if (!allowNone && !next && value) {
				return;
			}
			onChange(next ?? "none");
		}}
		sx={{ flexShrink: 0 }}
	>
		<ToggleButton value="select" sx={guidedConfigChoiceToggleButtonSx}>
			Select
		</ToggleButton>
		<ToggleButton value="force" sx={DIRECTIVE_FORCE_TOGGLE_SX}>
			Force
		</ToggleButton>
		<ToggleButton value="exclude" sx={DIRECTIVE_EXCLUDE_TOGGLE_SX} disabled={excludeDisabled}>
			Exclude
		</ToggleButton>
	</ToggleButtonGroup>
);

/** Select / Exclude for a tag row (tags have no "force" directive form). */
const TagDirectiveToggleGroup = ({ value, onChange, excludeDisabled }) => {
	const toggleValue =
		value === "include-tag" ? "select" : value === "exclude-tag" ? "exclude" : null;
	return (
		<ToggleButtonGroup
			exclusive
			size="small"
			value={toggleValue}
			onChange={(_, next) => {
				onChange(next === "select" ? "include-tag" : next === "exclude" ? "exclude-tag" : "none");
			}}
			sx={{ flexShrink: 0 }}
		>
			<ToggleButton value="select" sx={guidedConfigChoiceToggleButtonSx}>
				Select
			</ToggleButton>
			<ToggleButton value="exclude" sx={DIRECTIVE_EXCLUDE_TOGGLE_SX} disabled={excludeDisabled}>
				Exclude
			</ToggleButton>
		</ToggleButtonGroup>
	);
};

const CONFIGURABLE_CONNECTOR_CHIP_SX = {
	height: 20,
	fontSize: "0.625rem",
	fontWeight: 700,
	letterSpacing: "0.04em",
	borderRadius: 0.75,
	bgcolor: (theme) =>
		alpha(theme.palette.primary.main, theme.palette.mode === "dark" ? 0.22 : 0.12),
	color: "primary.main",
	border: "none",
	"& .MuiChip-label": { px: 0.75, py: 0 },
};

const CONNECTOR_LABEL_CONTAINER_SX = {
	height: "100%",
	width: "100%",
	display: "flex",
	flexDirection: "column",
	justifyContent: "center",
	boxSizing: "border-box",
	minWidth: 0,
};

const ConnectorTableRowLabel = ({
	icon,
	title,
	description,
	titleAdornment = null,
	indentPx = 0,
}) => (
	<Box
		sx={{
			...CONNECTOR_LABEL_CONTAINER_SX,
			...(indentPx ? { pl: `${indentPx}px` } : {}),
		}}
	>
		<Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0 }}>
			<Box sx={CONNECTOR_ROW_ICON_BOX_SX}>{icon}</Box>
			<Stack sx={{ minWidth: 0, flex: 1 }}>
				<Stack
					direction="row"
					spacing={0.75}
					alignItems="center"
					sx={{ minWidth: 0, flexWrap: "wrap" }}
					useFlexGap
				>
					<Typography variant="body2" fontWeight={700} noWrap sx={{ minWidth: 0 }}>
						{title}
					</Typography>
					{titleAdornment}
				</Stack>
				{description !== undefined ? (
					<Typography
						variant="caption"
						color="text.secondary"
						sx={{ ...CONNECTOR_ROW_DESCRIPTION_SX, pt: 0.25 }}
					>
						{description}
					</Typography>
				) : null}
			</Stack>
		</Stack>
	</Box>
);

const ConnectorRowLabel = ({ item }) => {
	const id = String(item?.id || "");
	const title = String(item?.displayName || "").trim() || id;
	const description = getConnectorListDescription(item);
	const hasVariables = Boolean(item?.hasVariables);
	return (
		<ConnectorTableRowLabel
			icon={
				hasVariables ? (
					<ConfigurableConnectorIcon sx={{ fontSize: 16 }} />
				) : (
					<SettingsInputHdmiIcon sx={{ fontSize: 16, color: "success.main" }} />
				)
			}
			title={title}
			description={description || "—"}
			titleAdornment={
				hasVariables ? (
					<Chip size="small" label="CONFIGURABLE" sx={CONFIGURABLE_CONNECTOR_CHIP_SX} />
				) : null
			}
		/>
	);
};

const ConfigurationRowLabel = ({ configurationId }) => (
	<ConnectorTableRowLabel
		indentPx={CONFIGURATION_ROW_INDENT_PX}
		icon={<SettingsInputHdmiIcon sx={{ fontSize: 16, color: "success.main" }} />}
		title={configurationId}
	/>
);

const CONFIGURATION_ACTION_BUTTON_SX = {
	...guidedConfigOutlinedPrimaryHoverSx,
	border: 1,
	borderColor: "divider",
	borderRadius: 1,
};

const AddConfigurationButton = ({ connectorId, onClick }) => (
	<Tooltip title="Add configuration" arrow>
		<IconButton
			size="small"
			aria-label={`Add configuration for ${connectorId}`}
			onClick={onClick}
			sx={CONFIGURATION_ACTION_BUTTON_SX}
		>
			<AddIcon fontSize="small" />
		</IconButton>
	</Tooltip>
);

const EditConfigurationButton = ({ configurationId, onClick }) => (
	<Tooltip title={`Edit configuration "${configurationId}"`} arrow>
		<IconButton
			size="small"
			aria-label={`Edit configuration ${configurationId}`}
			onClick={onClick}
			sx={CONFIGURATION_ACTION_BUTTON_SX}
		>
			<EditOutlinedIcon fontSize="small" />
		</IconButton>
	</Tooltip>
);

const DirectiveGridRow = React.forwardRef(function DirectiveGridRow(props, ref) {
	const directiveRowKey = props.row?.directiveRowKey;
	return (
		<GridRow
			{...props}
			ref={ref}
			{...(directiveRowKey ? { "data-directive-row": directiveRowKey } : {})}
		/>
	);
});

/**
 * @param {object[]} filteredConnectors
 * @param {Map<string, string[]>} instancesByTemplate
 * @param {string[]} selectedDirectives
 */
const buildConnectorGridRows = (
	filteredConnectors,
	instancesByTemplate,
	selectedDirectives,
	additionalConnectors,
) => {
	/** @type {object[]} */
	const rows = [];
	for (const item of filteredConnectors) {
		const id = String(item.id || "");
		const hasVariables = Boolean(item?.hasVariables);
		const selection = hasVariables ? "none" : getConnectorSelectionKind(selectedDirectives, id);
		rows.push({
			id: `connector-row:${id}`,
			rowType: ROW_TYPE_CONNECTOR,
			directiveRowKey: `connector:${id}`,
			item,
			connectorId: id,
			selection,
			hasVariables,
		});
		if (hasVariables) {
			for (const configurationId of instancesByTemplate.get(id) || []) {
				const configurationEntry = additionalConnectors?.[configurationId];
				rows.push({
					id: `configuration-row:${id}:${configurationId}`,
					rowType: ROW_TYPE_CONFIGURATION,
					directiveRowKey: `configuration:${configurationId}`,
					item,
					connectorId: id,
					configurationId,
					selection: getAdditionalConnectorSelectionKind(configurationEntry),
					hasVariables: false,
				});
			}
		}
	}
	return rows;
};

const getConnectorGridRowClassName = (row, focusedRowKey) => {
	/** @type {string[]} */
	const classes = [];
	if (row.rowType === ROW_TYPE_CONFIGURATION) {
		classes.push("connector-grid-row-configuration");
	}
	if (row.directiveRowKey === focusedRowKey) {
		classes.push("connector-grid-row-focused");
	}
	const selection = row.selection;
	if (selection && selection !== "none") {
		const emphasis = selection === "force" || selection === "select" || selection === "include-tag";
		classes.push(emphasis ? "connector-grid-row-active-emphasis" : "connector-grid-row-active");
	}
	return classes.join(" ");
};

const ActiveDirectivesPanel = ({
	panelTab,
	onPanelTabChange,
	directives,
	additionalConnectorChips,
	codeText,
	onCodeTextChange,
	onCodeFocus,
	onCodeBlur,
	onRemove,
	onClear,
	onNavigate,
	onNavigateAdditionalConnector,
	onRemoveAdditionalConnector,
	connectorError,
}) => {
	const hasSelections = directives.length > 0 || additionalConnectorChips.length > 0;
	const showDirectiveError = Boolean(connectorError) && !hasSelections;
	return (
		<Box>
			<Stack
				direction="row"
				alignItems="center"
				justifyContent="space-between"
				spacing={1}
				sx={{ mb: 0.75 }}
			>
				<Stack
					direction="row"
					alignItems="center"
					spacing={0.25}
					sx={{ minWidth: 0, flexWrap: "wrap" }}
				>
					<Typography component="span" sx={guidedConfigFieldLabelSx}>
						Connectors Directives
					</Typography>
					<FieldHelpTooltip title={CONNECTORS_DIRECTIVES_HELP} />
					{directives.length > 0 ? (
						<Typography
							component="button"
							type="button"
							variant="body2"
							onClick={onClear}
							sx={{
								ml: 0.75,
								p: 0,
								border: 0,
								bgcolor: "transparent",
								color: "text.secondary",
								fontSize: "0.8125rem",
								fontWeight: 400,
								cursor: "pointer",
								textDecoration: "underline",
								"&:hover": { color: "text.primary" },
							}}
						>
							Clear
						</Typography>
					) : null}
				</Stack>
				<ToggleButtonGroup
					exclusive
					size="small"
					value={panelTab}
					onChange={(_, nextTab) => nextTab && onPanelTabChange(nextTab)}
					sx={{ flexShrink: 0 }}
				>
					<ToggleButton value="directives" sx={DIRECTIVES_VIEW_TOGGLE_SX}>
						Directives
					</ToggleButton>
					<ToggleButton value="code" sx={DIRECTIVES_VIEW_TOGGLE_SX}>
						Code
					</ToggleButton>
				</ToggleButtonGroup>
			</Stack>
			<Box sx={directivesContentBoxSx(showDirectiveError)}>
				{panelTab === "directives" ? (
					hasSelections ? (
						<Stack direction="row" gap={0.75} flexWrap="wrap">
							{directives.map((directive) => {
								const parsed = parseConnectorDirective(directive);
								return (
									<Tooltip key={directive} title={getDirectiveChipTooltip(directive)}>
										<Chip
											label={getDirectiveChipLabel(directive)}
											variant="filled"
											onClick={() => onNavigate(directive)}
											onDelete={(event) => {
												event.stopPropagation();
												onRemove(directive);
											}}
											sx={{
												...DIRECTIVE_CHIP_SX,
												...getDirectiveChipSx(parsed.kind),
												...getDirectiveChipDeleteIconSx(parsed.kind),
											}}
										/>
									</Tooltip>
								);
							})}
							{additionalConnectorChips.map((chip) => {
								const chipKind = chip.forced ? "force" : "select";
								return (
									<Tooltip
										key={chip.instanceId}
										title={getAdditionalConnectorChipTooltip(
											chip.instanceId,
											chip.usesConnector,
											chip.forced,
										)}
									>
										<Chip
											label={getAdditionalConnectorChipLabel(chip.instanceId, chip.forced)}
											variant="filled"
											onClick={() => onNavigateAdditionalConnector?.(chip.instanceId)}
											onDelete={(event) => {
												event.stopPropagation();
												onRemoveAdditionalConnector?.(chip.instanceId);
											}}
											sx={{
												...DIRECTIVE_CHIP_SX,
												...getDirectiveChipSx(chipKind),
												...getDirectiveChipDeleteIconSx(chipKind),
											}}
										/>
									</Tooltip>
								);
							})}
						</Stack>
					) : showDirectiveError ? (
						<Typography variant="body2" color="error" sx={{ lineHeight: 1.45 }}>
							{connectorError}
						</Typography>
					) : (
						<Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.45 }}>
							No directives yet. Pick connectors below, add configurations, or switch detection
							mode.
						</Typography>
					)
				) : (
					<Stack spacing={0.5}>
						<TextField
							placeholder="Linux , +Windows , #HPE , !Solaris"
							value={codeText}
							onChange={(event) => onCodeTextChange(event.target.value)}
							onFocus={onCodeFocus}
							onBlur={onCodeBlur}
							fullWidth
							size="small"
							error={showDirectiveError}
							sx={DIRECTIVES_CODE_FIELD_SX}
							inputProps={{ spellCheck: false }}
						/>
						<Typography variant="caption" color="text.secondary">
							comma separated connectors directives : +Windows, !Solaris, #HPE
						</Typography>
					</Stack>
				)}
			</Box>
			{showDirectiveError && panelTab === "code" ? (
				<Typography variant="caption" color="error" sx={{ display: "block", mt: 0.5 }}>
					{connectorError}
				</Typography>
			) : null}
		</Box>
	);
};

const HostConfigConnectorsSection = ({
	selectedDirectives = [],
	onChange,
	hostType = "",
	protocols = [],
	detectionMode = "automatic",
	onDetectionModeChange,
	onScrollToConnectorsStep,
	connectorError,
	catalog = [],
	annotatedCatalog = [],
	catalogLoading = false,
	catalogError = null,
	additionalConnectors = {},
	onAdditionalConnectorsChange,
	onConnectorsStatePatch,
	configurationValidationAttempt = 0,
	configurationDialogErrors = {},
	highlightConfigurationId = null,
	onHighlightConfigurationIdConsumed,
}) => {
	const isManual = detectionMode === "manual";
	const [directivesPanelTab, setDirectivesPanelTab] = React.useState("directives");
	const [listView, setListView] = React.useState("connectors");
	const [categoryTab, setCategoryTab] = React.useState("all");
	// One search field shared by both views; it filters whichever table is active.
	const [searchQuery, setSearchQuery] = React.useState("");
	const [codeText, setCodeText] = React.useState(() =>
		formatInlineConnectorsText(selectedDirectives),
	);
	const [catalogOpen, setCatalogOpen] = React.useState(false);
	const [focusedRowKey, setFocusedRowKey] = React.useState(null);
	const [pendingRowKey, setPendingRowKey] = React.useState(null);
	const [configurationDialog, setConfigurationDialog] = React.useState(
		/** @type {{ template: object; instanceKey: string | null } | null} */ (null),
	);
	const tableRef = React.useRef(null);
	const gridApiRef = useGridApiRef();
	const stashedExcludesRef = React.useRef([]);
	const codeFieldFocusedRef = React.useRef(false);
	const [topEdgePulse, setTopEdgePulse] = React.useState(false);
	const topEdgePulseTimerRef = React.useRef(
		/** @type {ReturnType<typeof setTimeout> | null} */ (null),
	);

	const handleConnectorsTableWheel = React.useCallback((event) => {
		if (event.deltaY >= 0) {
			return;
		}
		const scroller = tableRef.current?.querySelector(".MuiDataGrid-virtualScroller");
		if (!(scroller instanceof HTMLElement)) {
			return;
		}
		const scrollable = scroller.scrollHeight > scroller.clientHeight + 1;
		if (!scrollable || scroller.scrollTop > 0) {
			return;
		}
		setTopEdgePulse(true);
		if (topEdgePulseTimerRef.current) {
			clearTimeout(topEdgePulseTimerRef.current);
		}
		topEdgePulseTimerRef.current = setTimeout(() => setTopEdgePulse(false), TOP_EDGE_PULSE_MS);
	}, []);

	React.useEffect(
		() => () => {
			if (topEdgePulseTimerRef.current) {
				clearTimeout(topEdgePulseTimerRef.current);
			}
		},
		[],
	);

	const protocolList = React.useMemo(
		() => [...new Set((protocols || []).map(String).filter(Boolean))].sort(),
		[protocols],
	);
	const plainCatalog = React.useMemo(() => dedupeConnectorCatalogById(catalog), [catalog]);
	const allCatalog = React.useMemo(
		() => dedupeConnectorCatalogById(annotatedCatalog),
		[annotatedCatalog],
	);
	const filteredConnectors = React.useMemo(
		() =>
			plainCatalog.filter((item) =>
				connectorMatchesListFilters(item, searchQuery, new Set(), categoryTab),
			),
		[plainCatalog, searchQuery, categoryTab],
	);
	const visibleCategoryTabs = React.useMemo(
		() =>
			CONNECTOR_CATEGORY_TABS.filter(
				(tab) =>
					tab.id === "all" ||
					plainCatalog.some((item) => connectorMatchesCategoryTab(item, tab.id)),
			),
		[plainCatalog],
	);

	// The active category can disappear when the catalog changes (e.g. protocol edits).
	React.useEffect(() => {
		if (categoryTab !== "all" && !visibleCategoryTabs.some((tab) => tab.id === categoryTab)) {
			setCategoryTab("all");
		}
	}, [categoryTab, visibleCategoryTabs]);
	const instancesByTemplate = React.useMemo(() => {
		/** @type {Map<string, string[]>} */
		const map = new Map();
		for (const [instanceId, entry] of Object.entries(additionalConnectors || {})) {
			const templateId = String(entry?.uses ?? instanceId).trim() || instanceId;
			const list = map.get(templateId) || [];
			list.push(instanceId);
			map.set(templateId, list);
		}
		for (const [templateId, list] of map.entries()) {
			map.set(
				templateId,
				list.sort((a, b) => compareLocale(a, b)),
			);
		}
		return map;
	}, [additionalConnectors]);
	const additionalConnectorChips = React.useMemo(
		() =>
			Object.entries(additionalConnectors || {})
				.map(([instanceId, entry]) => ({
					instanceId,
					usesConnector: String(entry?.uses ?? instanceId).trim() || instanceId,
					forced: entry?.force !== false,
				}))
				.sort((a, b) => compareLocale(a.instanceId, b.instanceId)),
		[additionalConnectors],
	);
	const directivesForDisplay = React.useMemo(
		() => filterDirectivesForAdditionalConnectorChips(selectedDirectives, additionalConnectors),
		[additionalConnectors, selectedDirectives],
	);
	const excludeDisabled = React.useMemo(
		() =>
			shouldDisableConnectorExcludes({
				directives: selectedDirectives,
				additionalConnectors,
				selectedVariableConnectorTemplates: [],
			}),
		[additionalConnectors, selectedDirectives],
	);

	React.useEffect(() => {
		if (!codeFieldFocusedRef.current) {
			setCodeText(formatInlineConnectorsText(selectedDirectives));
		}
	}, [selectedDirectives]);

	const applyDirectives = React.useCallback(
		(nextDirectives) => {
			const { active, stashedExcludes } = reconcileExcludeDirectives(
				nextDirectives,
				stashedExcludesRef.current,
			);
			stashedExcludesRef.current = stashedExcludes;
			onChange(active);
		},
		[onChange],
	);

	const handleCodeTextChange = React.useCallback(
		(value) => {
			setCodeText(value);
			applyDirectives(parseInlineConnectorsText(value));
		},
		[applyDirectives],
	);

	const handleDirectivesPanelTabChange = React.useCallback(
		(tab) => {
			if (tab === "code") {
				setCodeText(formatInlineConnectorsText(selectedDirectives));
			}
			setDirectivesPanelTab(tab);
		},
		[selectedDirectives],
	);

	const applyConnectorsPatch = React.useCallback(
		(patch) => {
			if (onConnectorsStatePatch) {
				onConnectorsStatePatch(patch);
				return;
			}
			if (patch.connectors) {
				onChange(patch.connectors);
			}
			if (patch.additionalConnectors && onAdditionalConnectorsChange) {
				onAdditionalConnectorsChange(patch.additionalConnectors);
			}
		},
		[onAdditionalConnectorsChange, onChange, onConnectorsStatePatch],
	);

	const buildConnectorsPatch = React.useCallback(
		(nextAdditionalConnectors, nextConnectors = selectedDirectives) =>
			applyAdditionalConnectorsChange(
				{
					connectors: nextConnectors,
					additionalConnectors,
					connectorDetectionMode: detectionMode,
				},
				nextAdditionalConnectors,
			),
		[additionalConnectors, detectionMode, selectedDirectives],
	);

	const commitAdditionalConnectors = React.useCallback(
		(nextAdditionalConnectors) => {
			applyConnectorsPatch(buildConnectorsPatch(nextAdditionalConnectors));
		},
		[applyConnectorsPatch, buildConnectorsPatch],
	);

	const handleRemoveAdditionalConnector = React.useCallback(
		(instanceId) => {
			const next = { ...(additionalConnectors || {}) };
			delete next[instanceId];
			commitAdditionalConnectors(next);
		},
		[additionalConnectors, commitAdditionalConnectors],
	);

	const openConfigurationDialog = React.useCallback((template, instanceKey = null) => {
		setConfigurationDialog({ template, instanceKey });
	}, []);

	const closeConfigurationDialog = React.useCallback(() => {
		setConfigurationDialog(null);
	}, []);

	const focusConnectorRow = React.useCallback(
		(templateItem, rowKey) => {
			setListView("connectors");
			if (
				templateItem &&
				categoryTab !== "all" &&
				!connectorMatchesCategoryTab(templateItem, categoryTab)
			) {
				setCategoryTab("all");
			}
			setSearchQuery("");
			setPendingRowKey(rowKey);
		},
		[categoryTab],
	);

	React.useEffect(() => {
		if (!highlightConfigurationId) {
			return;
		}
		const entry = additionalConnectors?.[highlightConfigurationId];
		const templateId = String(entry?.uses ?? highlightConfigurationId).trim();
		const template = findCatalogConnectorById(plainCatalog, templateId);
		if (template) {
			focusConnectorRow(template, `configuration:${highlightConfigurationId}`);
		}
		onHighlightConfigurationIdConsumed?.();
	}, [
		additionalConnectors,
		focusConnectorRow,
		highlightConfigurationId,
		onHighlightConfigurationIdConsumed,
		plainCatalog,
	]);

	const updateKind = React.useCallback(
		(value, kind, kinds) => {
			if (kind === "none") {
				applyDirectives(removeDirectivesForValue(selectedDirectives, value, kinds));
				return;
			}
			applyDirectives(upsertConnectorDirective(selectedDirectives, kind, value));
		},
		[applyDirectives, selectedDirectives],
	);

	const updateConfigurationKind = React.useCallback(
		(instanceId, kind) => {
			const entry = additionalConnectors?.[instanceId];
			if (!entry) {
				return;
			}
			const resolvedKind = kind === "none" || kind === "exclude" ? "select" : kind;
			const nextAdditional = {
				...additionalConnectors,
				[instanceId]: {
					...entry,
					force: resolvedKind === "force",
				},
			};
			applyConnectorsPatch(buildConnectorsPatch(nextAdditional));
		},
		[additionalConnectors, applyConnectorsPatch, buildConnectorsPatch],
	);

	const theme = useTheme();
	const connectorGridRows = React.useMemo(
		() =>
			buildConnectorGridRows(
				filteredConnectors,
				instancesByTemplate,
				selectedDirectives,
				additionalConnectors,
			),
		[additionalConnectors, filteredConnectors, instancesByTemplate, selectedDirectives],
	);
	const tagGridRows = React.useMemo(() => {
		/** @type {Map<string, number>} */
		const counts = new Map();
		for (const item of plainCatalog) {
			for (const tag of item.tags || []) {
				const name = String(tag);
				counts.set(name, (counts.get(name) || 0) + 1);
			}
		}
		return collectCatalogTags(plainCatalog).map((tag) => ({
			id: `tag-row:${tag}`,
			directiveRowKey: `tag:${tag}`,
			tag,
			connectorCount: counts.get(tag) || 0,
			selection: getTagSelectionKind(selectedDirectives, tag),
		}));
	}, [plainCatalog, selectedDirectives]);
	const filteredTagRows = React.useMemo(() => {
		const query = searchQuery.trim().toLowerCase();
		if (!query) {
			return tagGridRows;
		}
		return tagGridRows.filter((row) => row.tag.toLowerCase().includes(query));
	}, [tagGridRows, searchQuery]);
	const activeGridRows = listView === "tags" ? filteredTagRows : connectorGridRows;
	const activeGridRowHeight = listView === "tags" ? TAG_GRID_ROW_HEIGHT : CONNECTOR_GRID_ROW_HEIGHT;

	React.useLayoutEffect(() => {
		if (!pendingRowKey) {
			return undefined;
		}

		const tryFocusRow = () => {
			const container = tableRef.current;
			if (!container) {
				return false;
			}

			const rowIndex = activeGridRows.findIndex((row) => row.directiveRowKey === pendingRowKey);
			if (rowIndex < 0) {
				setPendingRowKey(null);
				return true;
			}

			const findRowElement = () =>
				container.querySelector(`[data-directive-row="${CSS.escape(pendingRowKey)}"]`);

			let row = findRowElement();
			if (!(row instanceof HTMLElement)) {
				scrollDataGridToRowIndex(container, rowIndex, activeGridRowHeight, gridApiRef);
				row = findRowElement();
			}

			if (!(row instanceof HTMLElement)) {
				return false;
			}

			scrollTableRowBelowStickyHeader(container, row);
			setFocusedRowKey(pendingRowKey);
			setPendingRowKey(null);
			return true;
		};

		if (tryFocusRow()) {
			return undefined;
		}

		// Give the DataGrid a few frames to render the virtualized row, then drop the
		// pending key so a stale request cannot hijack a later, unrelated re-render.
		return retryOnAnimationFrames(tryFocusRow, 5, () => setPendingRowKey(null));
	}, [pendingRowKey, categoryTab, activeGridRows, activeGridRowHeight, gridApiRef]);

	const connectorGridColumns = React.useMemo(
		() => [
			{
				field: "connector",
				headerName: "Connector",
				flex: CONNECTOR_COLUMN_FLEX,
				minWidth: 260,
				sortable: false,
				disableColumnMenu: true,
				renderCell: (params) =>
					params.row.rowType === ROW_TYPE_CONFIGURATION ? (
						<ConfigurationRowLabel configurationId={params.row.configurationId} />
					) : (
						<ConnectorRowLabel item={params.row.item} />
					),
			},
			{
				field: "connectorId",
				headerName: "Connector ID",
				flex: CONNECTOR_ID_COLUMN_FLEX,
				minWidth: 120,
				sortable: false,
				disableColumnMenu: true,
				renderCell: (params) =>
					params.row.rowType === ROW_TYPE_CONFIGURATION ? null : (
						<ConnectorDocumentationLink
							connectorId={params.row.connectorId}
							label={params.row.connectorId}
						/>
					),
			},
			{
				field: "configuration",
				headerName: "",
				width: CONNECTOR_CONFIGURATION_COLUMN_WIDTH,
				resizable: false,
				sortable: false,
				disableColumnMenu: true,
				headerAlign: "center",
				align: "center",
				renderHeader: () => null,
				renderCell: (params) => {
					if (params.row.rowType === ROW_TYPE_CONNECTOR && params.row.hasVariables) {
						return (
							<AddConfigurationButton
								connectorId={params.row.connectorId}
								onClick={() => openConfigurationDialog(params.row.item)}
							/>
						);
					}
					if (params.row.rowType === ROW_TYPE_CONFIGURATION) {
						return (
							<EditConfigurationButton
								configurationId={params.row.configurationId}
								onClick={() => openConfigurationDialog(params.row.item, params.row.configurationId)}
							/>
						);
					}
					return null;
				},
			},
			{
				field: "actions",
				headerName: "Actions",
				flex: CONNECTOR_ACTIONS_COLUMN_FLEX,
				minWidth: 220,
				sortable: false,
				disableColumnMenu: true,
				headerAlign: "right",
				align: "right",
				renderCell: (params) => {
					if (params.row.rowType === ROW_TYPE_CONNECTOR && params.row.hasVariables) {
						return null;
					}
					const targetId =
						params.row.rowType === ROW_TYPE_CONFIGURATION
							? params.row.configurationId
							: params.row.connectorId;
					const onChange =
						params.row.rowType === ROW_TYPE_CONFIGURATION
							? (next) => updateConfigurationKind(targetId, next)
							: (next) => updateKind(targetId, next, CONNECTOR_KINDS);
					return (
						<ConnectorDirectiveToggleGroup
							value={params.row.selection}
							excludeDisabled={excludeDisabled}
							allowNone={params.row.rowType !== ROW_TYPE_CONFIGURATION}
							onChange={onChange}
						/>
					);
				},
			},
		],
		[excludeDisabled, openConfigurationDialog, updateConfigurationKind, updateKind],
	);
	const tagGridColumns = React.useMemo(
		() => [
			{
				field: "tag",
				headerName: "Tag",
				flex: 3,
				minWidth: 200,
				sortable: false,
				disableColumnMenu: true,
				renderCell: (params) => (
					<Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0 }}>
						<Box sx={CONNECTOR_ROW_ICON_BOX_SX}>
							<Typography
								component="span"
								sx={{ fontSize: 15, fontWeight: 700, color: "primary.main", lineHeight: 1 }}
							>
								#
							</Typography>
						</Box>
						<Typography variant="body2" fontWeight={700} noWrap sx={{ minWidth: 0 }}>
							{params.row.tag}
						</Typography>
					</Stack>
				),
			},
			{
				field: "connectorCount",
				headerName: "Connectors",
				flex: 1,
				minWidth: 110,
				sortable: false,
				disableColumnMenu: true,
				renderCell: (params) => (
					<Typography variant="body2" color="text.secondary">
						{params.row.connectorCount}
					</Typography>
				),
			},
			{
				field: "actions",
				headerName: "Actions",
				flex: 2,
				minWidth: 180,
				sortable: false,
				disableColumnMenu: true,
				headerAlign: "right",
				align: "right",
				renderCell: (params) => (
					<TagDirectiveToggleGroup
						value={params.row.selection}
						excludeDisabled={excludeDisabled}
						onChange={(next) => updateKind(params.row.tag, next, TAG_DIRECTIVE_KINDS)}
					/>
				),
			},
		],
		[excludeDisabled, updateKind],
	);
	const connectorsGridSx = React.useMemo(() => buildConnectorsGridSx(theme), [theme]);
	const getRowClassName = React.useCallback(
		(params) => getConnectorGridRowClassName(params.row, focusedRowKey),
		[focusedRowKey],
	);

	return (
		<Stack spacing={2}>
			<Box>
				<Typography sx={{ ...guidedConfigFieldLabelSx, mb: 0.75 }}>Connector Detection</Typography>
				<ToggleButtonGroup
					value={detectionMode}
					exclusive
					onChange={(_, nextMode) => {
						if (!nextMode) {
							return;
						}
						if (nextMode === "manual" && detectionMode === "automatic") {
							onScrollToConnectorsStep?.();
						}
						onDetectionModeChange?.(nextMode);
					}}
					size="small"
				>
					<ToggleButton value="automatic" sx={guidedConfigChoiceToggleButtonSx}>
						Automatic detection
					</ToggleButton>
					<ToggleButton value="manual" sx={guidedConfigChoiceToggleButtonSx}>
						Manual selection
					</ToggleButton>
				</ToggleButtonGroup>
			</Box>

			{isManual ? (
				<ActiveDirectivesPanel
					panelTab={directivesPanelTab}
					onPanelTabChange={handleDirectivesPanelTabChange}
					directives={directivesForDisplay}
					additionalConnectorChips={additionalConnectorChips}
					codeText={codeText}
					onCodeTextChange={handleCodeTextChange}
					onCodeFocus={() => {
						codeFieldFocusedRef.current = true;
					}}
					onCodeBlur={() => {
						codeFieldFocusedRef.current = false;
						setCodeText(formatInlineConnectorsText(selectedDirectives));
					}}
					onRemove={(directive) =>
						applyDirectives(removeConnectorDirectiveByRaw(selectedDirectives, directive))
					}
					onClear={() => onChange([])}
					onNavigate={(directive) => {
						const parsed = parseConnectorDirective(directive);
						const isTag = parsed.kind === "include-tag" || parsed.kind === "exclude-tag";
						if (isTag) {
							setListView("tags");
							setSearchQuery("");
							setPendingRowKey(`tag:${parsed.value}`);
							return;
						}
						const instanceEntry = additionalConnectors?.[parsed.value];
						if (instanceEntry) {
							const templateId = String(instanceEntry.uses ?? parsed.value).trim();
							const template = findCatalogConnectorById(plainCatalog, templateId);
							focusConnectorRow(template, `configuration:${parsed.value}`);
							return;
						}
						const template = findCatalogConnectorById(plainCatalog, parsed.value);
						const rowKey = template
							? getConnectorDirectiveRowKey(template.id)
							: getRowKeyFromDirective(directive);
						focusConnectorRow(template, rowKey);
					}}
					onNavigateAdditionalConnector={(instanceId) => {
						const entry = additionalConnectors?.[instanceId];
						const templateId = String(entry?.uses ?? instanceId).trim();
						const template = findCatalogConnectorById(plainCatalog, templateId);
						if (template) {
							focusConnectorRow(template, `configuration:${instanceId}`);
						}
					}}
					onRemoveAdditionalConnector={handleRemoveAdditionalConnector}
					connectorError={connectorError}
				/>
			) : null}

			{isManual ? (
				<>
					{protocolList.length === 0 ? (
						<Alert severity="warning" sx={guidedConfigPanelAlertSx}>
							Add at least one protocol on the previous step to see compatible connectors.
						</Alert>
					) : null}
					{catalogError ? (
						<Alert severity="error" sx={guidedConfigPanelAlertSx}>
							{catalogError}
						</Alert>
					) : null}
					{catalogLoading ? (
						<Stack alignItems="center" py={3}>
							<CircularProgress size={32} />
						</Stack>
					) : (
						<>
							<Stack spacing={1}>
								<Box sx={{ borderBottom: 1, borderColor: "divider" }}>
									<ToggleButtonGroup
										exclusive
										size="small"
										value={listView}
										onChange={(_, nextView) => nextView && setListView(nextView)}
										sx={CATEGORY_TAB_STRIP_GROUP_SX}
									>
										<ToggleButton value="connectors" sx={CATEGORY_BROWSER_TAB_SX}>
											Connectors
										</ToggleButton>
										<ToggleButton value="tags" sx={CATEGORY_BROWSER_TAB_SX}>
											Tags
										</ToggleButton>
									</ToggleButtonGroup>
								</Box>
								<Stack
									direction="row"
									alignItems="center"
									gap={1}
									flexWrap={{ xs: "wrap", md: "nowrap" }}
									sx={{ minHeight: 40 }}
								>
									{listView === "connectors" ? (
										<ToggleButtonGroup
											value={categoryTab}
											exclusive
											onChange={(_, nextTab) => nextTab && setCategoryTab(nextTab)}
											size="small"
											sx={{ ...CATEGORY_TAB_STRIP_GROUP_SX, flexShrink: 0 }}
										>
											{visibleCategoryTabs.map((tab) => (
												<ToggleButton key={tab.id} value={tab.id} sx={CATEGORY_BROWSER_TAB_SX}>
													{tab.label}
												</ToggleButton>
											))}
										</ToggleButtonGroup>
									) : null}
									<TextField
										size="small"
										placeholder={listView === "tags" ? "Search tags" : "Search by name or ID"}
										value={searchQuery}
										onChange={(event) => setSearchQuery(event.target.value)}
										sx={{
											...guidedConfigPlaceholderSx,
											// Fixed-size, right-anchored: switching Connectors / Tags or hiding
											// category tabs never moves or resizes it.
											flex: "0 1 400px",
											ml: { xs: 0, md: "auto" },
											minWidth: { xs: "100%", md: 180 },
										}}
										InputProps={{
											startAdornment: (
												<InputAdornment position="start">
													<SearchIcon fontSize="small" />
												</InputAdornment>
											),
										}}
									/>
									<Button
										size="small"
										variant="outlined"
										startIcon={<InfoOutlinedIcon />}
										onClick={() => setCatalogOpen(true)}
										disabled={allCatalog.length === 0}
										sx={{
											...guidedConfigOutlinedPrimaryHoverSx,
											flexShrink: 0,
											whiteSpace: "nowrap",
										}}
									>
										Show all connectors
									</Button>
								</Stack>
							</Stack>
							<Box
								ref={tableRef}
								onWheel={handleConnectorsTableWheel}
								sx={{
									...CONNECTORS_GRID_CONTAINER_SX,
									border: 1,
									borderColor: "divider",
									borderRadius: 1,
								}}
							>
								{listView === "tags" ? (
									filteredTagRows.length === 0 ? (
										<Box
											sx={{
												height: "100%",
												display: "flex",
												alignItems: "center",
												justifyContent: "center",
												px: 2,
											}}
										>
											<Typography variant="body2" color="text.secondary" textAlign="center">
												{searchQuery.trim()
													? "No tags match your search."
													: "No tags available for the compatible connectors."}
											</Typography>
										</Box>
									) : (
										<DataGrid
											apiRef={gridApiRef}
											rows={filteredTagRows}
											columns={tagGridColumns}
											disableRowSelectionOnClick
											hideFooter
											density="compact"
											className={topEdgePulse ? "connectors-grid-top-edge-pulse" : undefined}
											getRowHeight={() => TAG_GRID_ROW_HEIGHT}
											getRowClassName={getRowClassName}
											slots={{ row: DirectiveGridRow }}
											sx={connectorsGridSx}
										/>
									)
								) : filteredConnectors.length === 0 ? (
									<Box
										sx={{
											height: "100%",
											display: "flex",
											alignItems: "center",
											justifyContent: "center",
											px: 2,
										}}
									>
										<Typography variant="body2" color="text.secondary" textAlign="center">
											{searchQuery.trim() || categoryTab !== "all"
												? "No connectors match your filters."
												: "No compatible connectors."}
										</Typography>
									</Box>
								) : (
									<DataGrid
										apiRef={gridApiRef}
										rows={connectorGridRows}
										columns={connectorGridColumns}
										disableRowSelectionOnClick
										hideFooter
										density="compact"
										className={topEdgePulse ? "connectors-grid-top-edge-pulse" : undefined}
										getRowHeight={() => CONNECTOR_GRID_ROW_HEIGHT}
										getRowClassName={getRowClassName}
										slots={{ row: DirectiveGridRow }}
										sx={connectorsGridSx}
									/>
								)}
							</Box>
						</>
					)}
				</>
			) : null}

			<HostConnectorsCatalogDialog
				open={catalogOpen}
				onClose={() => setCatalogOpen(false)}
				connectors={allCatalog}
				hostType={hostType}
				configuredProtocols={protocolList}
			/>

			<ConnectorConfigurationDialog
				open={Boolean(configurationDialog)}
				onClose={closeConfigurationDialog}
				template={configurationDialog?.template ?? null}
				instanceKey={configurationDialog?.instanceKey ?? null}
				additionalConnectors={additionalConnectors}
				onSave={commitAdditionalConnectors}
				onDelete={handleRemoveAdditionalConnector}
				errors={configurationDialogErrors}
				validationAttempt={configurationValidationAttempt}
			/>
		</Stack>
	);
};

export default HostConfigConnectorsSection;
