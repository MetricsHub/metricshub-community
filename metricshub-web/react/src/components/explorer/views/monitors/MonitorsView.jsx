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
	Divider,
	Accordion,
	AccordionSummary,
	AccordionDetails,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";

/**
 * Monitors section displayed inside the Resource page.
 *
 * - Shows H2 "Monitors".
 * - For each monitor type, if instances count <= 20, lists all instances
 *   and their metrics in a table.
 * - If instances count > 20, shows a badge with the count that would
 *   redirect to a dedicated monitor type page (navigation not wired yet).
 *
 * @param {{ connectors?: any[] }} props
 */
const MonitorsView = ({ connectors }) => {
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

	const safeMonitors = Array.isArray(allMonitors) ? allMonitors : [];

	if (safeMonitors.length === 0) {
		return (
			<Box>
				<Typography variant="h5" gutterBottom>
					Monitors
				</Typography>
				<Typography variant="body2">No monitors available for this resource.</Typography>
			</Box>
		);
	}

	return (
		<Box display="flex" flexDirection="column" gap={3}>
			<Typography variant="h5" gutterBottom>
				Monitors
			</Typography>

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
								px: 1.5,
								minHeight: 40,
								cursor: "pointer",
								bgcolor: "background.default",
								"&:hover": {
									bgcolor: "action.hover",
								},
								"& .MuiAccordionSummary-content": { my: 0 },
							}}
						>
							<Typography variant="subtitle1" sx={{ fontWeight: 500 }}>
								{monitor.name}
							</Typography>
						</AccordionSummary>
						<AccordionDetails sx={{ px: 1.5, pb: 2 }}>
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
														<TableCell>Last Update</TableCell>
													</TableRow>
												</TableHead>
												<TableBody>
													{metricEntries.length === 0 ? (
														<TableRow>
															<TableCell colSpan={4}>No metrics</TableCell>
														</TableRow>
													) : (
														metricEntries.map(([name, value]) => (
															<TableRow key={name}>
																<TableCell>{name}</TableCell>
																<TableCell>{String(value)}</TableCell>
																<TableCell></TableCell>
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
