import * as React from "react";
import {
	Button,
	FormControl,
	InputAdornment,
	InputLabel,
	MenuItem,
	Paper,
	Select,
	Stack,
	TextField,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";

/**
 * Dashboard-style filters for host lists inside a group or standalone section.
 *
 * @param {object} props
 * @param {string} props.search
 * @param {(value: string) => void} props.onSearchChange
 * @param {string} props.protocolFilter use "all" for no filter
 * @param {(value: string) => void} props.onProtocolFilterChange
 * @param {string} props.sortBy
 * @param {(value: string) => void} props.onSortChange
 * @param {string[]} props.availableProtocols
 * @param {() => void} props.onClearFilters
 * @param {React.ReactNode} [props.actions] trailing action buttons
 */
const HostsHostFilterBar = ({
	search,
	onSearchChange,
	protocolFilter,
	onProtocolFilterChange,
	sortBy,
	onSortChange,
	availableProtocols,
	onClearFilters,
	actions = null,
}) => (
	<Paper variant="outlined" elevation={0} sx={{ p: 1.5, borderRadius: 1 }}>
		<Stack spacing={1.5}>
			<Stack
				direction={{ xs: "column", sm: "row" }}
				spacing={1}
				alignItems={{ xs: "stretch", sm: "center" }}
				flexWrap="wrap"
				useFlexGap
			>
				<TextField
					size="small"
					placeholder="Search resources…"
					value={search}
					onChange={(e) => onSearchChange(e.target.value)}
					sx={{ minWidth: 200, flex: 1 }}
					InputProps={{
						startAdornment: (
							<InputAdornment position="start">
								<SearchIcon fontSize="small" color="action" />
							</InputAdornment>
						),
					}}
				/>
				<FormControl size="small" sx={{ minWidth: 160 }}>
					<InputLabel>Protocol</InputLabel>
					<Select
						label="Protocol"
						value={protocolFilter}
						onChange={(e) => onProtocolFilterChange(String(e.target.value))}
					>
						<MenuItem value="all">All protocols</MenuItem>
						{availableProtocols.map((p) => (
							<MenuItem key={p} value={p}>
								{p}
							</MenuItem>
						))}
					</Select>
				</FormControl>
				<FormControl size="small" sx={{ minWidth: 160 }}>
					<InputLabel>Sort by</InputLabel>
					<Select
						label="Sort by"
						value={sortBy}
						onChange={(e) => onSortChange(String(e.target.value))}
					>
						<MenuItem value="name-asc">Display name (A–Z)</MenuItem>
						<MenuItem value="name-desc">Display name (Z–A)</MenuItem>
						<MenuItem value="id-asc">Resource ID (A–Z)</MenuItem>
						<MenuItem value="id-desc">Resource ID (Z–A)</MenuItem>
					</Select>
				</FormControl>
				<Button size="small" onClick={onClearFilters}>
					Clear filters
				</Button>
				{actions && (
					<Stack
						direction="row"
						spacing={1}
						flexWrap="wrap"
						useFlexGap
						sx={{ ml: { sm: "auto" }, width: { xs: "100%", sm: "auto" } }}
					>
						{actions}
					</Stack>
				)}
			</Stack>
		</Stack>
	</Paper>
);

export default HostsHostFilterBar;
