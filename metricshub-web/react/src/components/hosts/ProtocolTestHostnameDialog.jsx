import * as React from "react";
import {
	Alert,
	Box,
	Button,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	IconButton,
	List,
	ListItemButton,
	ListItemText,
	TextField,
	Typography,
} from "@mui/material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CloseIcon from "@mui/icons-material/Close";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import NetworkCheckIcon from "@mui/icons-material/NetworkCheck";
import SearchIcon from "@mui/icons-material/Search";
import { searchFieldSx } from "./guided-config-form-primitives";
import { ProtocolFieldStartAdornment } from "./protocol-form-primitives";
import { scrollbarSx } from "../split-screen/SplitScreen";

/**
 * @param {{ status: "testing" | "success" | "error" | "warning" } | undefined} result
 */
const HostStatusIcon = ({ result }) => {
	if (!result) {
		return <NetworkCheckIcon sx={{ fontSize: 18, color: "action.disabled" }} />;
	}
	if (result.status === "testing") {
		return <CircularProgress size={16} />;
	}
	if (result.status === "success") {
		return <CheckCircleIcon color="success" sx={{ fontSize: 20 }} />;
	}
	return (
		<ErrorOutlineIcon
			color={result.status === "warning" ? "warning" : "error"}
			sx={{ fontSize: 20 }}
		/>
	);
};

/**
 * Connection test panel for multi-host resources: pick a hostname to test it, or
 * test them all in parallel. Each row reports its own status.
 *
 * @param {object} props
 * @param {boolean} props.open
 * @param {string[]} props.hostnames
 * @param {string} [props.protocolLabel] display name of the tested protocol (e.g. "Ping (ICMP)")
 * @param {() => void} props.onClose
 * @param {(hostname: string, signal: AbortSignal) => Promise<{ severity: string, message: string } | null>} props.runTest
 */
const ProtocolTestHostnameDialog = ({
	open,
	hostnames = [],
	protocolLabel = "",
	onClose,
	runTest,
}) => {
	const [query, setQuery] = React.useState("");
	const [results, setResults] = React.useState(
		/** @type {Record<string, { status: string, message?: string }>} */ ({}),
	);
	const [configWarning, setConfigWarning] = React.useState(/** @type {string | null} */ (null));
	const abortControllerRef = React.useRef(/** @type {AbortController | null} */ (null));

	React.useEffect(() => {
		if (!open) {
			abortControllerRef.current?.abort();
			abortControllerRef.current = null;
			setQuery("");
			setResults({});
			setConfigWarning(null);
		}
	}, [open]);

	React.useEffect(
		() => () => {
			abortControllerRef.current?.abort();
		},
		[],
	);

	const filteredHostnames = React.useMemo(() => {
		const needle = query.trim().toLowerCase();
		if (!needle) {
			return hostnames;
		}
		return hostnames.filter((hostname) => hostname.toLowerCase().includes(needle));
	}, [hostnames, query]);

	const getSessionController = () => {
		if (!abortControllerRef.current || abortControllerRef.current.signal.aborted) {
			abortControllerRef.current = new AbortController();
		}
		return abortControllerRef.current;
	};

	const testHostname = async (hostname, controller) => {
		setResults((prev) => ({ ...prev, [hostname]: { status: "testing" } }));
		const result = await runTest(hostname, controller.signal);
		if (controller.signal.aborted) {
			return;
		}
		if (!result) {
			setResults((prev) => {
				const next = { ...prev };
				delete next[hostname];
				return next;
			});
			return;
		}
		// The protocol configuration is host-independent: a validation warning applies
		// to every host, so surface it once above the list instead of per row.
		if (result.severity === "warning" && result.message !== "Connection timed out.") {
			setConfigWarning(result.message);
			setResults((prev) => {
				const next = { ...prev };
				delete next[hostname];
				return next;
			});
			return;
		}
		setConfigWarning(null);
		setResults((prev) => ({
			...prev,
			[hostname]: {
				status:
					result.severity === "success"
						? "success"
						: result.severity === "warning"
							? "warning"
							: "error",
				message: result.message,
			},
		}));
	};

	const testingCount = Object.values(results).filter((r) => r.status === "testing").length;

	const handleTestAll = () => {
		const controller = getSessionController();
		setConfigWarning(null);
		hostnames.forEach((hostname) => {
			void testHostname(hostname, controller);
		});
	};

	const handleTestOne = (hostname) => {
		const controller = getSessionController();
		void testHostname(hostname, controller);
	};

	return (
		<Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
			<Box sx={{ display: "flex", alignItems: "flex-start", px: 2.5, pt: 2, pb: 1 }}>
				<Box sx={{ flex: 1, minWidth: 0 }}>
					<Typography variant="h6" sx={{ lineHeight: 1.3 }}>
						Test connection
					</Typography>
					<Typography variant="body2" color="text.secondary">
						{protocolLabel ? `${protocolLabel} — ` : ""}
						{hostnames.length} hostnames
					</Typography>
				</Box>
				<IconButton size="small" onClick={onClose} aria-label="Close" sx={{ mt: -0.5, mr: -0.75 }}>
					<CloseIcon fontSize="small" />
				</IconButton>
			</Box>
			{/* Search and warnings stay put; only the hostname list below scrolls. */}
			<Box sx={{ px: 2.5, pb: 1 }}>
				<TextField
					fullWidth
					hiddenLabel
					size="small"
					placeholder="Search hostnames…"
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
				{configWarning ? (
					<Alert severity="warning" sx={{ mt: 1, py: 0.25 }} onClose={() => setConfigWarning(null)}>
						{configWarning}
					</Alert>
				) : null}
			</Box>
			<DialogContent
				sx={(theme) => ({
					...scrollbarSx(theme),
					height: "auto",
					maxHeight: 320,
					px: 2.5,
					py: 0.5,
				})}
			>
				{filteredHostnames.length > 0 ? (
					<List dense disablePadding>
						{filteredHostnames.map((hostname) => {
							const result = results[hostname];
							return (
								<ListItemButton
									key={hostname}
									onClick={() => handleTestOne(hostname)}
									disabled={result?.status === "testing"}
									sx={{ borderRadius: 1, px: 1.25, py: 0.75 }}
								>
									<ListItemText
										primary={hostname}
										secondary={result?.status !== "testing" ? result?.message : undefined}
										slotProps={{
											primary: { sx: { fontWeight: 500 } },
											secondary: { sx: { fontSize: "0.75rem" } },
										}}
									/>
									<Box sx={{ display: "flex", alignItems: "center", ml: 1.5, flexShrink: 0 }}>
										<HostStatusIcon result={result} />
									</Box>
								</ListItemButton>
							);
						})}
					</List>
				) : (
					<Typography variant="body2" color="text.secondary" sx={{ px: 1.25, py: 1 }}>
						No hostnames match your search.
					</Typography>
				)}
			</DialogContent>
			<DialogActions sx={{ px: 2.5, pb: 2, pt: 0.5 }}>
				<Button onClick={onClose} size="small" color="inherit">
					Close
				</Button>
				<Button
					variant="contained"
					size="small"
					onClick={handleTestAll}
					disabled={testingCount > 0}
					startIcon={
						testingCount > 0 ? <CircularProgress size={14} color="inherit" /> : <NetworkCheckIcon />
					}
				>
					{testingCount > 0 ? "Testing…" : "Test all"}
				</Button>
			</DialogActions>
		</Dialog>
	);
};

export default ProtocolTestHostnameDialog;
