import * as React from "react";
import { Alert, Button, CircularProgress, Stack } from "@mui/material";
import NetworkCheckIcon from "@mui/icons-material/NetworkCheck";
import { getHostNames } from "./host-config-utils";
import { alignHostnameOverrides } from "../../utils/host-name-overrides";
import { PROTOCOL_OPTIONS } from "./protocol-definitions";
import { runProtocolCheck } from "./protocol-test";
import ProtocolTestHostnameDialog from "./ProtocolTestHostnameDialog";

const COMPACT_ALERT_SX = {
	py: 0,
	width: "fit-content",
	maxWidth: "100%",
	flexShrink: 0,
	alignItems: "center",
	"& .MuiAlert-icon": { py: 0.25, mr: 0.75 },
	"& .MuiAlert-message": {
		py: 0.25,
		fontSize: "0.75rem",
		lineHeight: 1.4,
	},
};

/**
 * On-demand protocol test button for guided configuration protocol steps.
 *
 * @param {object} props
 * @param {string} props.protocol protocol id (e.g. ssh)
 * @param {string} [props.hostName] host.name form value
 * @param {string} [props.hostId] resource id
 * @param {Record<string, unknown>} props.protocolValues current protocol form values
 */
const ProtocolTestButton = ({ protocol, hostName, hostId, protocolValues }) => {
	const [testing, setTesting] = React.useState(false);
	const [result, setResult] = React.useState(null);
	const [hostnamePickerOpen, setHostnamePickerOpen] = React.useState(false);
	const abortControllerRef = React.useRef(/** @type {AbortController | null} */ (null));

	const cancelTest = React.useCallback(() => {
		abortControllerRef.current?.abort();
		abortControllerRef.current = null;
		setTesting(false);
		setResult({
			severity: "warning",
			message: "Connection test cancelled.",
		});
	}, []);

	const runTest = React.useCallback(
		async (hostname) => {
			abortControllerRef.current?.abort();
			const controller = new AbortController();
			abortControllerRef.current = controller;

			setTesting(true);
			setResult(null);
			const testResult = await runProtocolCheck({
				protocol,
				protocolValues,
				hostname,
				hostId,
				hostName,
				signal: controller.signal,
			});
			if (abortControllerRef.current === controller) {
				abortControllerRef.current = null;
			}
			if (controller.signal.aborted) {
				return;
			}
			setTesting(false);
			if (testResult) {
				setResult(testResult);
			}
		},
		[hostId, hostName, protocol, protocolValues],
	);

	// Runner handed to the multi-host dialog: same check, per-host abort signal.
	const runHostnameTest = React.useCallback(
		(hostname, signal) =>
			runProtocolCheck({ protocol, protocolValues, hostname, hostId, hostName, signal }),
		[hostId, hostName, protocol, protocolValues],
	);

	React.useEffect(
		() => () => {
			abortControllerRef.current?.abort();
		},
		[],
	);

	// Protocol-level hostnames override the resource ones: they are the hosts actually contacted.
	const candidateHostnames = React.useMemo(() => {
		const resourceHostnames = getHostNames(hostName);
		if (resourceHostnames.length <= 1) {
			const protocolHostnames = getHostNames(protocolValues?.hostname);
			return protocolHostnames.length > 0 ? protocolHostnames : resourceHostnames;
		}
		// Effective per-host targets: a blank override falls back to its host.name
		// entry; duplicates collapse (the dialog list is keyed by hostname).
		const slots = alignHostnameOverrides(protocolValues?.hostname, resourceHostnames.length);
		return getHostNames(slots.map((slot, index) => slot || resourceHostnames[index]));
	}, [protocolValues?.hostname, hostName]);

	const handleTestClick = () => {
		if (candidateHostnames.length === 0) {
			setResult({
				severity: "warning",
				message: "Specify host.name before testing this protocol.",
			});
			return;
		}
		if (candidateHostnames.length > 1) {
			setHostnamePickerOpen(true);
			return;
		}
		void runTest(candidateHostnames[0]);
	};

	return (
		<>
			<Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
				<Button
					variant="outlined"
					size="small"
					startIcon={
						testing ? <CircularProgress size={16} color="inherit" /> : <NetworkCheckIcon />
					}
					onClick={handleTestClick}
					disabled={testing}
					sx={{ flexShrink: 0 }}
				>
					{testing ? "Testing…" : "Test connection"}
				</Button>
				{testing ? (
					<Button size="small" variant="text" onClick={cancelTest} sx={{ flexShrink: 0 }}>
						Cancel
					</Button>
				) : null}
				{result ? (
					<Alert severity={result.severity} sx={COMPACT_ALERT_SX} onClose={() => setResult(null)}>
						{result.message}
					</Alert>
				) : null}
			</Stack>
			<ProtocolTestHostnameDialog
				open={hostnamePickerOpen}
				hostnames={candidateHostnames}
				protocolLabel={PROTOCOL_OPTIONS.find((option) => option.id === protocol)?.label || protocol}
				onClose={() => setHostnamePickerOpen(false)}
				runTest={runHostnameTest}
			/>
		</>
	);
};

export default ProtocolTestButton;
