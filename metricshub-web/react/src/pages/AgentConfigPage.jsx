import * as React from "react";
import { useNavigate } from "react-router-dom";
import { Box, CircularProgress, Alert } from "@mui/material";
import { uiConfigApi } from "../api/ui-config";
import GlobalSettingsView from "../components/hosts/GlobalSettingsView";
import { HOST_CONFIG_FORM_CONTENT_MAX_WIDTH } from "../components/hosts/host-config-form-layout";
import { paths } from "../paths";

/**
 * Standalone agent configuration page (accessible at /agent/config).
 * Renders the same GlobalSettingsView that was previously embedded in
 * the guided-config section, without the resource-group tree panel.
 */
const AgentConfigPage = () => {
	const navigate = useNavigate();
	const [snapshot, setSnapshot] = React.useState({ resources: {}, resourceGroups: {} });
	const [loading, setLoading] = React.useState(true);
	const [error, setError] = React.useState(null);

	React.useEffect(() => {
		let cancelled = false;
		const load = async () => {
			setLoading(true);
			setError(null);
			try {
				const data = await uiConfigApi.getHosts();
				if (!cancelled) {
					setSnapshot(data || { resources: {}, resourceGroups: {} });
				}
			} catch (e) {
				if (!cancelled) {
					setError(e?.message || "Failed to load configuration");
				}
			} finally {
				if (!cancelled) {
					setLoading(false);
				}
			}
		};
		load();
		return () => {
			cancelled = true;
		};
	}, []);

	if (loading) {
		return (
			<Box display="flex" justifyContent="center" alignItems="center" p={6}>
				<CircularProgress />
			</Box>
		);
	}

	if (error) {
		return (
			<Box p={3}>
				<Alert severity="error">{error}</Alert>
			</Box>
		);
	}

	return (
		<Box sx={{ p: { xs: 2, sm: 3 }, maxWidth: HOST_CONFIG_FORM_CONTENT_MAX_WIDTH, mx: "auto" }}>
			<GlobalSettingsView
				snapshot={snapshot}
				onConfigureGroup={(groupName) => navigate(paths.hostsResourceGroup(groupName))}
				onOpenStandaloneHost={(hostId) => navigate(paths.hostsStandaloneResource(hostId))}
			/>
		</Box>
	);
};

export default AgentConfigPage;
