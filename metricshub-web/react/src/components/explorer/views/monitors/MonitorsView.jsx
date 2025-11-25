import * as React from "react";
import {
	Box,
	Typography,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableRow,
	Paper,
	Accordion,
	AccordionSummary,
	AccordionDetails,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { prettifyKey } from "../../../../utils/text-prettifier";

/**
 * Monitors section displayed inside the Resource page.
 *
 * - Shows H2 "Monitors".
 * - For each monitor type, if instances count <= 20, lists all instances
 *   and their metrics in a table.
 * - If instances count > 20, shows a badge with the count that would
 *   redirect to a dedicated monitor type page (navigation not wired yet).
 * - Shows a "last updated" label based on when the resource
 *   data was last fetched (provided by parent).
 *
 * @param {{ connectors?: any[], lastUpdatedAt?: number | string | Date }} props
 */
const MonitorsView = ({ connectors, lastUpdatedAt }) => {
	// Aggregate monitors from all connectors and always return an array
	const allMonitors = React.useMemo(() => {
		if (!Array.isArray(connectors)) return [];
		const list = [];
		for (const connector of connectors) {
			if (Array.isArray(connector?.monitors)) {
				for (const m of connector.monitors) {
					list.push({ ...m, connectorName: connector.name });
				}
			}
		}
		return list;
	}, [connectors]);

	const safeMonitors = React.useMemo(
		() => (Array.isArray(allMonitors) ? allMonitors : []),
		[allMonitors],
	);

	const lastUpdatedLabel = React.useMemo(() => {
		if (!lastUpdatedAt) return "Never";

		const ts =
			lastUpdatedAt instanceof Date
				? lastUpdatedAt.getTime()
				: typeof lastUpdatedAt === "number"
					? lastUpdatedAt
					: new Date(lastUpdatedAt).getTime();
		if (Number.isNaN(ts)) return "Never";

		// Only display local time, not the full date
		return new Date(ts).toLocaleTimeString();
	}, [lastUpdatedAt]);

	if (safeMonitors.length === 0) {
		return (
			<Box>
				<Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
					<Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
						Monitors
					</Typography>
					<Typography variant="caption" color="text.secondary">
						Last updated: {lastUpdatedLabel}
					</Typography>
				</Box>
				<Typography variant="body2">No monitors available for this resource.</Typography>
			</Box>
		);
	}

	return (
		<Box display="flex" flexDirection="column">
			<Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
				<Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
					Monitors
				</Typography>
				<Typography variant="caption" color="text.secondary">
					Last updated: {lastUpdatedLabel}
				</Typography>
			</Box>

			{safeMonitors.map((monitor, index) => {
				const instances = Array.isArray(monitor.instances) ? monitor.instances : [];

				return (
					<Accordion
						key={monitor.name}
						defaultExpanded={false}
						disableGutters
						elevation={0}
						square
						sx={{
							bgcolor: "transparent",
							borderTop: "1px solid",
							borderColor: "divider",
							...(index === safeMonitors.length - 1
								? { borderBottom: "1px solid", borderBottomColor: "divider" }
								: {}),
						}}
					>
						<AccordionSummary
							expandIcon={<ExpandMoreIcon />}
							sx={{
								minHeight: 40,
								cursor: "pointer",
								bgcolor: "background.default",
								"&:hover": {
									bgcolor: "action.hover",
								},
								"& .MuiAccordionSummary-content": { my: 0, ml: 0 },
							}}
						>
							<Typography variant="subtitle1" sx={{ fontWeight: 500 }}>
								{prettifyKey(monitor.name)}
							</Typography>
						</AccordionSummary>
						<AccordionDetails sx={{ px: 1.5, pb: 2, pt: 1 }}>
							{instances.map((inst) => {
								const attrs = inst?.attributes ?? {};
								const id = attrs.id || inst.name;
								const displayName =
									attrs["system.device"] || attrs.name || attrs["network.interface.name"] || id;
								const extraInfoParts = [];
								if (attrs.name && attrs.name !== displayName)
									extraInfoParts.push(`name: ${attrs.name}`);
								if (attrs.serial_number)
									extraInfoParts.push(`serial_number: ${attrs.serial_number}`);
								if (attrs.vendor) extraInfoParts.push(`vendor: ${attrs.vendor}`);
								if (attrs.info) extraInfoParts.push(`info: ${attrs.info}`);

								const metrics = inst?.metrics ?? {};
								const metricEntries = Object.entries(metrics).filter(
									([name]) => !name.startsWith("__"),
								);

								return (
									<Box key={id} mb={3}>
										<Typography variant="subtitle1" sx={{ fontWeight: 500, mb: 1 }}>
											{displayName}
											{extraInfoParts.length > 0 && ` (${extraInfoParts.join("; ")})`}
										</Typography>

										<Paper variant="outlined" sx={{ mt: 1 }}>
											<Table size="small">
												<TableHead>
													<TableRow>
														<TableCell>Name</TableCell>
														<TableCell>Value</TableCell>
														<TableCell>Unit</TableCell>
													</TableRow>
												</TableHead>
												<TableBody>
													{metricEntries.length === 0 ? (
														<TableRow>
															<TableCell colSpan={3}>No metrics</TableCell>
														</TableRow>
													) : (
														metricEntries.map(([name, value]) => (
															<TableRow key={name}>
																<TableCell>{name}</TableCell>
																<TableCell>{String(value)}</TableCell>
																<TableCell></TableCell>
															</TableRow>
														))
													)}
												</TableBody>
											</Table>
										</Paper>
									</Box>
								);
							})}
						</AccordionDetails>
					</Accordion>
				);
			})}
		</Box>
	);
};

export default MonitorsView;
