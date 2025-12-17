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
import HoverInfo from "../monitors/components/HoverInfo";

/**
 * Accordion for displaying metrics with a count bubble.
 *
 * @param {{ metrics: Record<string, any> | Array<any>, maxNameLength?: number }} props
 * @returns {JSX.Element | null}
 */
const MetricsAccordion = ({
    metrics,
    maxNameLength,
}) => {
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
            disableGutters
            elevation={0}
            square
            sx={{
                bgcolor: "transparent",
                borderTop: "1px solid",
                borderColor: "divider",
                borderBottom: "1px solid",
                borderBottomColor: "divider",
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
                    <Typography
                        variant="h6"
                        sx={{
                            fontWeight: 600,
                            flexShrink: 0,
                            ...(maxNameLength ? { width: `${maxNameLength + 1}ch` } : {}),
                        }}
                    >
                        Metrics
                    </Typography>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                        <HoverInfo
                            title="Number of metrics"
                            sx={{ display: "flex", alignItems: "center" }}
                        >
                            <Box
                                component="span"
                                sx={{
                                    minWidth: 24,
                                    px: 1,
                                    display: "flex",
                                    justifyContent: "center",
                                    alignItems: "center",
                                    borderRadius: 999,
                                    fontSize: 12,
                                    fontWeight: 500,
                                    bgcolor: "primary.main",
                                    color: "primary.contrastText",
                                }}
                            >
                                {count}
                            </Box>
                        </HoverInfo>
                    </Box>
                </Box>
            </AccordionSummary>
            <AccordionDetails sx={{ p: 0 }}>
                <Box sx={{ p: 2 }}>
                    <MetricsTable
                        metrics={metrics}
                    />
                </Box>
            </AccordionDetails>
        </Accordion>
    );
};

export default React.memo(MetricsAccordion);
