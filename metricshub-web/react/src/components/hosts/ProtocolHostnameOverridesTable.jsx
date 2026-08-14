import * as React from "react";
import {
	Box,
	Chip,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TablePagination,
	TableRow,
	TextField,
	Typography,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import { alignHostnameOverrides, countConfiguredOverrides } from "../../utils/host-name-overrides";
import { filledInputNoLabelSx, searchFieldSx } from "./guided-config-form-primitives";
import {
	ProtocolFieldLabelRow,
	ProtocolFieldStartAdornment,
	protocolTextFieldProps,
} from "./protocol-form-primitives";

/** Rows above which the search field and pagination appear. */
const SEARCH_THRESHOLD = 5;

const ROWS_PER_PAGE_OPTIONS = [5, 10, 25, 50];

const headerCellSx = {
	bgcolor: "background.paper",
	color: "text.secondary",
	fontWeight: 600,
	fontSize: "0.8125rem",
	py: 0.75,
	borderBottomColor: "divider",
};

const bodyCellSx = {
	py: 0.5,
	borderBottomColor: "divider",
};

/**
 * One mapping row. Memoized so typing in a cell re-renders only that row.
 *
 * @param {object} props
 * @param {number} props.index zero-based position in the host.name list
 * @param {string} props.hostEntry the host.name entry this row overrides
 * @param {string} props.slotValue current override ("" = use hostEntry)
 * @param {(index: number, value: string) => void} props.onSlotChange
 * @param {(index: number, event: React.ClipboardEvent) => void} props.onSlotPaste
 */
const OverrideRow = React.memo(({ index, hostEntry, slotValue, onSlotChange, onSlotPaste }) => (
	<TableRow hover>
		<TableCell align="right" sx={{ ...bodyCellSx, color: "text.secondary", width: 48 }}>
			{index + 1}
		</TableCell>
		<TableCell sx={{ ...bodyCellSx, maxWidth: 0, width: "40%" }}>
			<Typography variant="body2" noWrap title={hostEntry} sx={{ fontWeight: 500 }}>
				{hostEntry}
			</Typography>
		</TableCell>
		<TableCell sx={bodyCellSx}>
			<TextField
				{...protocolTextFieldProps}
				fullWidth
				placeholder={hostEntry}
				value={slotValue}
				onChange={(e) => onSlotChange(index, e.target.value)}
				onPaste={(e) => onSlotPaste(index, e)}
				slotProps={{
					htmlInput: { "aria-label": `Hostname override for ${hostEntry}` },
				}}
			/>
		</TableCell>
	</TableRow>
));
OverrideRow.displayName = "OverrideRow";

/**
 * Inline mapping table for the protocol hostname override of a multi-host
 * resource: one row per host.name entry, in resource order (the order is the
 * positional contract — see PostConfigDeserializer.normalizeProtocolHostnames).
 * A blank cell means "use the host.name entry itself". The search only filters
 * the display; the underlying slots and their indices never move.
 *
 * @param {object} props
 * @param {string[]} props.hostNames resource host.name entries, in resource order
 * @param {string | string[]} props.value current override value from the form state
 * @param {(next: string[]) => void} props.onChange emits the full-length slot array
 * @param {string} props.label
 * @param {boolean} [props.required]
 * @param {string} [props.helpTooltip]
 * @param {boolean} [props.error]
 * @param {React.ReactNode} [props.helperText]
 */
const ProtocolHostnameOverridesTable = ({
	hostNames,
	value,
	onChange,
	label,
	required = false,
	helpTooltip,
	error = false,
	helperText,
}) => {
	const [query, setQuery] = React.useState("");
	const [page, setPage] = React.useState(0);
	const [rowsPerPage, setRowsPerPage] = React.useState(10);

	const slots = React.useMemo(
		() => alignHostnameOverrides(value, hostNames.length),
		[value, hostNames.length],
	);
	const configuredCount = countConfiguredOverrides(value, hostNames.length);

	// Latest slots in a ref so the per-row change handler stays referentially
	// stable and memoized rows only re-render when their own cell changes.
	const slotsRef = React.useRef(slots);
	slotsRef.current = slots;

	const handleSlotChange = React.useCallback(
		(index, raw) => {
			// Hostnames never contain whitespace, and `,`/`;` are the multi-value
			// separators of the stored configuration: block all three at input.
			const sanitized = raw.replace(/[;,\s]/g, "");
			const next = [...slotsRef.current];
			next[index] = sanitized;
			onChange(next);
		},
		[onChange],
	);

	const needle = query.trim().toLowerCase();

	const handleSlotPaste = React.useCallback(
		(index, event) => {
			const pasted = event.clipboardData.getData("text");
			if (!/[;,\s]/.test(pasted)) {
				return;
			}
			// A multi-value paste (spreadsheet column, comma list) fills consecutive
			// rows from the target cell — but only over the real, unfiltered order:
			// spreading across a filtered view would target rows the user can't see.
			event.preventDefault();
			const tokens = pasted
				.split(/[;,\s]+/)
				.map((token) => token.trim())
				.filter(Boolean);
			if (tokens.length === 0) {
				return;
			}
			const next = [...slotsRef.current];
			if (needle) {
				next[index] = tokens[0];
			} else {
				tokens.slice(0, next.length - index).forEach((token, offset) => {
					next[index + offset] = token;
				});
			}
			onChange(next);
		},
		[needle, onChange],
	);
	const visibleRows = React.useMemo(() => {
		const rows = hostNames.map((hostEntry, index) => ({ hostEntry, index }));
		if (!needle) {
			return rows;
		}
		return rows.filter(
			({ hostEntry, index }) =>
				hostEntry.toLowerCase().includes(needle) ||
				(slots[index] || "").toLowerCase().includes(needle),
		);
	}, [hostNames, needle, slots]);

	// Pagination pages the (possibly filtered) rows; row numbers stay absolute.
	// Never exceed the last page when the filter shrinks the set, and jump back
	// to the first page whenever the search text changes.
	React.useEffect(() => {
		setPage(0);
	}, [needle]);
	const paginated = hostNames.length > SEARCH_THRESHOLD;
	const lastPage = Math.max(0, Math.ceil(visibleRows.length / rowsPerPage) - 1);
	const safePage = Math.min(page, lastPage);
	const pagedRows = paginated
		? visibleRows.slice(safePage * rowsPerPage, safePage * rowsPerPage + rowsPerPage)
		: visibleRows;

	return (
		<Box>
			<ProtocolFieldLabelRow
				label={label}
				required={required}
				description="Leave a row blank to use the host.name entry."
				helpTooltip={helpTooltip}
				trailing={
					<Chip
						size="small"
						color={configuredCount === hostNames.length ? "primary" : "default"}
						variant="outlined"
						label={`${configuredCount}/${hostNames.length} configured`}
						sx={{ fontWeight: 600 }}
					/>
				}
			/>
			{hostNames.length > SEARCH_THRESHOLD ? (
				<Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
					<TextField
						fullWidth
						hiddenLabel
						size="small"
						placeholder="Search hosts…"
						value={query}
						onChange={(e) => setQuery(e.target.value)}
						sx={searchFieldSx}
						slotProps={{
							input: {
								startAdornment: (
									<ProtocolFieldStartAdornment>
										<SearchIcon fontSize="small" sx={{ color: "text.secondary" }} />
									</ProtocolFieldStartAdornment>
								),
							},
						}}
					/>
					{needle ? (
						<Typography
							variant="body2"
							color="text.secondary"
							sx={{ whiteSpace: "nowrap", flexShrink: 0 }}
						>
							{visibleRows.length} of {hostNames.length}
						</Typography>
					) : null}
				</Box>
			) : null}
			<Box
				sx={{
					border: 1,
					borderColor: error ? "error.main" : "divider",
					borderRadius: 2,
					overflow: "hidden",
					...filledInputNoLabelSx,
				}}
			>
				<Table size="small" aria-label={label}>
					<TableHead>
						<TableRow>
							<TableCell align="right" sx={{ ...headerCellSx, width: 48 }}>
								#
							</TableCell>
							<TableCell sx={{ ...headerCellSx, width: "40%" }}>host.name</TableCell>
							<TableCell sx={headerCellSx}>{label}</TableCell>
						</TableRow>
					</TableHead>
					<TableBody>
						{pagedRows.length > 0 ? (
							pagedRows.map(({ hostEntry, index }) => (
								<OverrideRow
									key={index}
									index={index}
									hostEntry={hostEntry}
									slotValue={slots[index] || ""}
									onSlotChange={handleSlotChange}
									onSlotPaste={handleSlotPaste}
								/>
							))
						) : (
							<TableRow>
								<TableCell colSpan={3} sx={{ ...bodyCellSx, py: 1.5 }}>
									<Typography variant="body2" color="text.secondary">
										No hosts match your search.
									</Typography>
								</TableCell>
							</TableRow>
						)}
					</TableBody>
				</Table>
				{paginated ? (
					<TablePagination
						component="div"
						count={visibleRows.length}
						page={safePage}
						onPageChange={(_event, nextPage) => setPage(nextPage)}
						rowsPerPage={rowsPerPage}
						onRowsPerPageChange={(event) => {
							setRowsPerPage(parseInt(String(event.target.value), 10));
							setPage(0);
						}}
						rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
						labelRowsPerPage="Rows per page"
						sx={{ borderTop: 1, borderColor: "divider" }}
					/>
				) : null}
			</Box>
			{helperText ? (
				<Typography
					variant="caption"
					color={error ? "error.main" : "text.secondary"}
					sx={{ display: "block", mt: 0.5, mx: 1.75 }}
				>
					{helperText}
				</Typography>
			) : null}
		</Box>
	);
};

export default React.memo(ProtocolHostnameOverridesTable);
