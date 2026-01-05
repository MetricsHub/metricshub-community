import * as React from "react";
import {
	Box,
	Typography,
	Accordion,
	AccordionSummary,
	AccordionDetails,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import MetricsTable from "./MetricsTable";
import CountBadge from "./CountBadge";

/**
 * Accordion for displaying metrics with a count bubble.
 *
 * @param {object} props - Component props
 * @param {Record<string, any> | Array<any>} props.metrics - Metrics to display
 * @returns {JSX.Element | null}
 */
const MetricsAccordion = ({ metrics }) => {
	const [expanded, setExpanded] = React.useState(false);

	const metricsList = React.useMemo(() => {
		if (!metrics) return [];
		if (Array.isArray(metrics)) return metrics;
		return Object.entries(metrics).map(([name, value]) => ({ name, value }));
	}, [metrics]);

	const count = metricsList.length;

	const handleToggle = React.useCallback((event, isExpanded) => {
		setExpanded(isExpanded);
	}, []);

	if (count === 0) {
		return null;
	}

	return (
		<Accordion
			expanded={expanded}
			onChange={handleToggle}
			TransitionProps={{ unmountOnExit: true }}
			disableGutters
			elevation={0}
			square
			sx={{
				bgcolor: "transparent",
				borderTop: "1px solid",
				borderColor: "divider",
				"&:before": {
					display: "none",
				},
			}}
		>
			<AccordionSummary
				expandIcon={<ExpandMoreIcon />}
				sx={{
					minHeight: 48,
					cursor: "pointer",
					bgcolor: "action.hover",
					"&:hover": {
						bgcolor: "action.selected",
					},
					"& .MuiAccordionSummary-content": { my: 0, ml: 0 },
				}}
			>
				<Box sx={{ display: "flex", alignItems: "center", width: "100%", pr: 2 }}>
					<Box sx={{ display: "flex", alignItems: "center" }}>
						<Typography
							variant="h6"
							sx={{
								fontWeight: 600,
								flexShrink: 0,
							}}
						>
							Metrics
						</Typography>
						<CountBadge count={count} title="Number of metrics" sx={{ ml: 1 }} />
					</Box>
				</Box>
			</AccordionSummary>
			<AccordionDetails sx={{ p: 0 }}>
				<Box sx={{ p: 2 }}>
					<MetricsTable metrics={metrics} />
				</Box>
			</AccordionDetails>
		</Accordion>
	);
};

export default React.memo(MetricsAccordion);
