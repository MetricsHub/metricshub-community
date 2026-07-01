import * as React from "react";
import {
	Dialog,
	DialogContent,
	DialogTitle,
	List,
	ListItemButton,
	ListItemText,
	TextField,
	Typography,
} from "@mui/material";

/**
 * Hostname picker for protocol connection tests on multi-host resources.
 *
 * @param {object} props
 * @param {boolean} props.open
 * @param {string[]} props.hostnames
 * @param {() => void} props.onClose
 * @param {(hostname: string) => void} props.onSelect
 */
const ProtocolTestHostnameDialog = ({ open, hostnames = [], onClose, onSelect }) => {
	const [query, setQuery] = React.useState("");

	React.useEffect(() => {
		if (!open) {
			setQuery("");
		}
	}, [open]);

	const filteredHostnames = React.useMemo(() => {
		const needle = query.trim().toLowerCase();
		if (!needle) {
			return hostnames;
		}
		return hostnames.filter((hostname) => hostname.toLowerCase().includes(needle));
	}, [hostnames, query]);

	return (
		<Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
			<DialogTitle>Select hostname to test</DialogTitle>
			<DialogContent sx={{ pt: 0.5 }}>
				<TextField
					fullWidth
					size="small"
					placeholder="Search hostnames…"
					value={query}
					onChange={(e) => setQuery(e.target.value)}
					autoFocus
					sx={{ mb: 1.5 }}
				/>
				{filteredHostnames.length > 0 ? (
					<List dense disablePadding>
						{filteredHostnames.map((hostname) => (
							<ListItemButton
								key={hostname}
								onClick={() => {
									onSelect(hostname);
									onClose();
								}}
								sx={{ borderRadius: 1 }}
							>
								<ListItemText primary={hostname} />
							</ListItemButton>
						))}
					</List>
				) : (
					<Typography variant="body2" color="text.secondary">
						No hostnames match your search.
					</Typography>
				)}
			</DialogContent>
		</Dialog>
	);
};

export default ProtocolTestHostnameDialog;
