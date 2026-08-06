import * as React from "react";
import {
	Box,
	Button,
	Chip,
	IconButton,
	Stack,
	TextField,
	Tooltip,
	Typography,
} from "@mui/material";
import { filledInputNoLabelSx, kvEditorBorderedContainerSx } from "./guided-config-form-primitives";
import { EXPLORER_METRIC_FONT_FAMILY } from "../../utils/metric-font";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";

// Stable per-row IDs so React keys don't reshuffle when the user reorders or
// edits rows. Module-level counter; ids are not persisted.
let _rowIdCounter = 0;

/**
 * Builds a fresh row object with a stable internal id.
 *
 * @param {string} [key]
 * @param {string} [value]
 * @returns {{ id: string; key: string; value: string }}
 */
export const makeKvRow = (key = "", value = "") => ({
	id: `kv-${++_rowIdCounter}`,
	key,
	value,
});

/**
 * Converts a plain object into editor rows. Useful when seeding the editor
 * from an existing config (attributes/metrics already saved on the backend).
 *
 * @param {Record<string, unknown> | undefined | null} obj
 * @returns {{ id: string; key: string; value: string }[]}
 */
export const objectToKvRows = (obj) => {
	if (!obj || typeof obj !== "object") {
		return [];
	}
	return Object.entries(obj).map(([key, value]) =>
		makeKvRow(key, value === undefined || value === null ? "" : String(value)),
	);
};

/**
 * Converts editor rows back into a plain string→string object. Empty keys are
 * dropped; duplicate keys are last-wins.
 *
 * @param {{ key: string; value: string }[]} rows
 * @returns {Record<string, string>}
 */
export const kvRowsToObject = (rows) =>
	Object.fromEntries(
		(rows || []).filter((r) => r && r.key && r.key.trim()).map((r) => [r.key.trim(), r.value]),
	);

/**
 * Converts editor rows into a plain string→number object. Non-numeric values
 * are silently skipped. Empty keys are dropped.
 *
 * @param {{ key: string; value: string }[]} rows
 * @returns {Record<string, number>}
 */
export const kvRowsToNumericObject = (rows) => {
	/** @type {Record<string, number>} */
	const out = {};
	for (const r of rows || []) {
		const k = r?.key?.trim();
		if (!k) continue;
		const n = parseFloat(r.value);
		if (!Number.isNaN(n)) {
			out[k] = n;
		}
	}
	return out;
};

/** Width reserved for the optional per-row help icon (small IconButton). */
const HELP_ICON_SLOT_WIDTH = 34;

/** Width reserved for the per-row delete control. */
const DELETE_ICON_SLOT_WIDTH = 34;

const HelpIcon = ({ text, primary = false }) => (
	<Tooltip
		title={<Box sx={{ whiteSpace: "pre-wrap", fontSize: "0.75rem", lineHeight: 1.6 }}>{text}</Box>}
		placement="left"
		arrow
	>
		<IconButton
			size="small"
			tabIndex={-1}
			sx={{
				flexShrink: 0,
				mt: 0.25,
				color: primary ? "primary.main" : "text.secondary",
				"&:hover": { color: primary ? "primary.dark" : "text.primary" },
			}}
		>
			<HelpOutlineIcon sx={{ fontSize: 16 }} />
		</IconButton>
	</Tooltip>
);

/**
 * Editable list of key/value rows with Add and per-row Delete controls.
 *
 * @param {object} props
 * @param {{ id: string; key: string; value: string }[]} props.rows
 * @param {(rows: { id: string; key: string; value: string }[]) => void} props.onRowsChange
 * @param {string} [props.keyLabel]
 * @param {string} [props.valueLabel]
 * @param {string} [props.addLabel]
 * @param {"decimal" | "numeric" | "text"} [props.valueInputMode] hints the keyboard on mobile
 * @param {Record<string, string>} [props.helpByKey] when a row's key matches, show a tooltip help icon
 * @param {{ key: string; value: string }[]} [props.defaultRows] well-known rows the editor can restore.
 *   Each entry whose key is missing from `rows` is offered as a small "+ key" chip below the Add button.
 *   Clicking the chip appends the row with the given value.
 * @param {boolean} [props.disabled]
 * @param {boolean} [props.labelsAbove] when true, column headers stay above fields (no floating labels)
 * @param {string} [props.sectionTitle] optional heading shown above the key/value editor
 * @param {boolean} [props.monospaceKeys] use Explorer metric monospace font for key field inputs
 * @param {boolean} [props.bordered] wrap rows in a rounded bordered container
 * @param {boolean} [props.primaryHelpIcons] render row help icons in primary blue (site metrics)
 */
const KeyValueRowsEditor = ({
	rows,
	onRowsChange,
	keyLabel = "Key",
	valueLabel = "Value",
	addLabel = "Add row",
	valueInputMode,
	helpByKey,
	defaultRows,
	disabled = false,
	labelsAbove = false,
	sectionTitle,
	monospaceKeys = false,
	bordered = false,
	primaryHelpIcons = false,
}) => {
	const currentKeys = React.useMemo(
		() => new Set((rows || []).map((r) => r.key?.trim()).filter(Boolean)),
		[rows],
	);
	const missingDefaults = React.useMemo(
		() => (defaultRows || []).filter((d) => d.key && !currentKeys.has(d.key.trim())),
		[defaultRows, currentKeys],
	);
	const updateRow = (id, field, newValue) => {
		onRowsChange(rows.map((r) => (r.id === id ? { ...r, [field]: newValue } : r)));
	};

	const addRow = () => {
		onRowsChange([...(rows || []), makeKvRow()]);
	};

	const removeRow = (id) => {
		onRowsChange((rows || []).filter((r) => r.id !== id));
	};

	const fieldSx = labelsAbove
		? { flex: 1, minWidth: 0, ...filledInputNoLabelSx }
		: { flex: 1, minWidth: 0 };

	const keyFieldSx = monospaceKeys
		? {
				...fieldSx,
				"& .MuiInputBase-input": {
					fontFamily: EXPLORER_METRIC_FONT_FAMILY,
					fontSize: "0.85rem",
				},
			}
		: fieldSx;

	const reserveHelpSlot = Boolean(helpByKey);
	const actionsSlotWidth = reserveHelpSlot
		? HELP_ICON_SLOT_WIDTH + DELETE_ICON_SLOT_WIDTH
		: DELETE_ICON_SLOT_WIDTH;

	const columnHeaders =
		labelsAbove && (rows || []).length > 0 ? (
			<Stack direction="row" spacing={1} alignItems="center" sx={{ px: 0.25 }}>
				<Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ flex: 1 }}>
					{keyLabel}
				</Typography>
				<Typography variant="caption" fontWeight={700} color="text.secondary" sx={{ flex: 1 }}>
					{valueLabel}
				</Typography>
				<Box sx={{ width: actionsSlotWidth, flexShrink: 0 }} />
			</Stack>
		) : null;

	const editorContent = (
		<>
			{columnHeaders}
			{(rows || []).map((row) => {
				const help = helpByKey?.[row.key];
				return (
					<Stack key={row.id} direction="row" spacing={1} alignItems="flex-start">
						<TextField
							label={labelsAbove ? undefined : keyLabel}
							hiddenLabel={labelsAbove}
							placeholder={labelsAbove ? `Enter ${keyLabel.toLowerCase()}` : undefined}
							size="small"
							value={row.key}
							onChange={(e) => updateRow(row.id, "key", e.target.value)}
							disabled={disabled}
							sx={keyFieldSx}
						/>
						<TextField
							label={labelsAbove ? undefined : valueLabel}
							hiddenLabel={labelsAbove}
							placeholder={labelsAbove ? `Enter ${valueLabel.toLowerCase()}` : undefined}
							size="small"
							value={row.value}
							onChange={(e) => updateRow(row.id, "value", e.target.value)}
							disabled={disabled}
							inputProps={valueInputMode ? { inputMode: valueInputMode } : undefined}
							sx={fieldSx}
						/>
						<Stack
							direction="row"
							alignItems="flex-start"
							sx={{ flexShrink: 0, width: actionsSlotWidth }}
						>
							{reserveHelpSlot ? (
								<Box
									sx={{
										width: HELP_ICON_SLOT_WIDTH,
										display: "flex",
										justifyContent: "center",
										flexShrink: 0,
									}}
								>
									{help ? <HelpIcon text={help} primary={primaryHelpIcons} /> : null}
								</Box>
							) : null}
							<Box
								sx={{
									width: DELETE_ICON_SLOT_WIDTH,
									display: "flex",
									justifyContent: "center",
									flexShrink: 0,
								}}
							>
								<IconButton
									size="small"
									color="error"
									onClick={() => removeRow(row.id)}
									aria-label={`Remove ${row.key || "row"}`}
									disabled={disabled}
									sx={{ mt: 0.25 }}
								>
									<DeleteOutlineIcon fontSize="small" />
								</IconButton>
							</Box>
						</Stack>
					</Stack>
				);
			})}
			<Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
				<Button startIcon={<AddIcon />} size="small" onClick={addRow} disabled={disabled}>
					{addLabel}
				</Button>
				{missingDefaults.map((d) => (
					<Tooltip key={d.key} title={`Restore the default "${d.key}" row`} arrow>
						<Chip
							size="small"
							variant="outlined"
							icon={<AddIcon sx={{ fontSize: 14 }} />}
							label={d.key}
							onClick={() => {
								onRowsChange([...(rows || []), makeKvRow(d.key, d.value ?? "")]);
							}}
							disabled={disabled}
							clickable
							sx={
								monospaceKeys
									? {
											"& .MuiChip-label": {
												fontFamily: EXPLORER_METRIC_FONT_FAMILY,
												fontSize: "0.8rem",
											},
										}
									: undefined
							}
						/>
					</Tooltip>
				))}
			</Stack>
		</>
	);

	return (
		<Box>
			{sectionTitle ? (
				<Typography variant="body2" fontWeight={600} sx={{ display: "block", mb: 0.75 }}>
					{sectionTitle}
				</Typography>
			) : null}
			{bordered ? (
				<Box sx={kvEditorBorderedContainerSx}>
					<Stack spacing={1.5}>{editorContent}</Stack>
				</Box>
			) : (
				<Stack spacing={1.5}>{editorContent}</Stack>
			)}
		</Box>
	);
};

export default KeyValueRowsEditor;
