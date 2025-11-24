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
} from "@mui/material";

const renderMetricsRows = (metrics) => {
	const list = metrics ?? [];
	if (list.length === 0) {
		return (
			<TableRow>
				<TableCell colSpan={2}>No metrics</TableCell>
			</TableRow>
		);
	}

	return list.map((m) => (
		<TableRow key={m.key}>
			<TableCell>{m.key}</TableCell>
			<TableCell>{m.value}</TableCell>
		</TableRow>
	));
};

const ResourceGroupMetrics = ({ metrics }) => {
	const rows = React.useMemo(() => metrics ?? [], [metrics]);

	return (
		<Box>
			<Typography variant="h6" gutterBottom>
				Metrics
			</Typography>
			<Paper variant="outlined">
				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Key</TableCell>
							<TableCell>Value</TableCell>
						</TableRow>
					</TableHead>
					<TableBody>{renderMetricsRows(rows)}</TableBody>
				</Table>
			</Paper>
		</Box>
	);
};

export default ResourceGroupMetrics;
