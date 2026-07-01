import * as React from "react";
import { Alert, Button, CircularProgress, Stack } from "@mui/material";
import NetworkCheckIcon from "@mui/icons-material/NetworkCheck";
import { uiConfigApi } from "../../api/ui-config";
import { getHostNames } from "./host-config-utils";
import { buildProtocolConfigFromForm, collectProtocolConfigErrors } from "./protocol-definitions";
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

const isAbortError = (error) =>
	error?.code === "ERR_CANCELED" || error?.name === "CanceledError" || error?.name === "AbortError";

/**
 * On-demand protocol test button for guided configuration protocol steps.
 *
 * @param {object} props
 * @param {string} props.protocol protocol id (e.g. ssh)
 * @param {string} [props.hostName] host.name wizard value
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
			const validationErrors = collectProtocolConfigErrors(protocol, protocolValues, {
				hostId,
				hostName,
			});
			const firstError = Object.values(validationErrors)[0];
			if (firstError) {
				setResult({
					severity: "warning",
					message: firstError,
				});
				return;
			}

			abortControllerRef.current?.abort();
			const controller = new AbortController();
			abortControllerRef.current = controller;

			setTesting(true);
			setResult(null);
			try {
				const response = await uiConfigApi.checkProtocol(
					{
						hostname,
						protocol,
						protocolConfig: {
							[protocol]: buildProtocolConfigFromForm(protocol, protocolValues),
						},
					},
					{ signal: controller.signal },
				);

				if (controller.signal.aborted) {
					return;
				}

				if (response.timedOut) {
					setResult({
						severity: "warning",
						message: "Connection timed out.",
					});
					return;
				}

				if (response.errorMessage) {
					setResult({
						severity: "error",
						message: "Protocol test failed.",
					});
					return;
				}

				if (response.hostUp === 1) {
					const suffix =
						response.responseTimeMs != null ? ` (${Math.round(response.responseTimeMs)} ms)` : "";
					setResult({
						severity: "success",
						message: `Connection successful${suffix}`,
					});
					return;
				}

				setResult({
					severity: "error",
					message: "Protocol test failed.",
				});
			} catch (error) {
				if (controller.signal.aborted || isAbortError(error)) {
					return;
				}
				setResult({
					severity: "error",
					message: "Protocol test failed.",
				});
			} finally {
				if (abortControllerRef.current === controller) {
					abortControllerRef.current = null;
				}
				if (!controller.signal.aborted) {
					setTesting(false);
				}
			}
		},
		[hostId, hostName, protocol, protocolValues],
	);

	React.useEffect(
		() => () => {
			abortControllerRef.current?.abort();
		},
		[],
	);

	const handleTestClick = () => {
		const hostnames = getHostNames(hostName);
		if (hostnames.length === 0) {
			setResult({
				severity: "warning",
				message: "Specify host.name before testing this protocol.",
			});
			return;
		}
		if (hostnames.length > 1) {
			setHostnamePickerOpen(true);
			return;
		}
		void runTest(hostnames[0]);
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
				hostnames={getHostNames(hostName)}
				onClose={() => setHostnamePickerOpen(false)}
				onSelect={(hostname) => void runTest(hostname)}
			/>
		</>
	);
};

export default ProtocolTestButton;
