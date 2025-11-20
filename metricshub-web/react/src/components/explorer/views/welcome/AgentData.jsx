import * as React from "react";
import { Box, Typography, Table, TableBody, TableCell, TableHead, TableRow, Paper } from "@mui/material";

/**
 * Render rows for agent attributes.
 * @param {Record<string, unknown>} attributes
 * @returns {JSX.Element[] | JSX.Element}
 */
const renderAttributesRows = (attributes) => {
    const entries = Object.entries(attributes ?? {});
    if (entries.length === 0) {
        return (
            <TableRow>
                <TableCell colSpan={2}>No attributes</TableCell>
            </TableRow>
        );
    }

    return entries.map(([key, value]) => (
        <TableRow key={key}>
            <TableCell>{key}</TableCell>
            <TableCell>{String(value)}</TableCell>
        </TableRow>
    ));
};

/**
 * Render rows for agent metrics.
 * @param {Record<string, { value?: unknown, unit?: string, lastUpdate?: string }>} metrics
 * @returns {JSX.Element[] | JSX.Element}
 */
const renderMetricsRows = (metrics) => {
    const entries = Object.entries(metrics ?? {});
    if (entries.length === 0) {
        return (
            <TableRow>
                <TableCell colSpan={4}>No metrics</TableCell>
            </TableRow>
        );
    }

    return entries.map(([name, m]) => {
        const metric = m || {};
        return (
            <TableRow key={name}>
                <TableCell>{name}</TableCell>
                <TableCell>{metric.value ?? ""}</TableCell>
                <TableCell>{metric.unit ?? ""}</TableCell>
                <TableCell>{metric.lastUpdate ?? ""}</TableCell>
            </TableRow>
        );
    });
};

/**
 * Agent header, attributes and metrics section for the welcome page.
 * @param {{ agent: { attributes?: Record<string, unknown>, metrics?: Record<string, any> } | null, totalResources?: number }} props
 * @returns {JSX.Element | null}
 */
const AgentData = ({ agent, totalResources }) => {
    const attributes = React.useMemo(() => agent?.attributes ?? {}, [agent]);
    const version = attributes.version || attributes.cc_version || "";
    const title = "MetricsHub Community";

    if (!agent) {
        return null;
    }

    return (
        <Box display="flex" flexDirection="column" gap={3}>
            <Box>
                <Typography variant="h4" gutterBottom>
                    {title}
                </Typography>
                {version && (
                    <Typography variant="subtitle1">Version: {version}</Typography>
                )}
                {typeof totalResources === "number" && (
                    <Typography variant="subtitle1">
                        Total resources: {totalResources}
                    </Typography>
                )}
            </Box>

            <Box>
                <Typography variant="h6" gutterBottom>
                    Attributes
                </Typography>
                <Paper variant="outlined">
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Key</TableCell>
                                <TableCell>Value</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>{renderAttributesRows(attributes)}</TableBody>
                    </Table>
                </Paper>
            </Box>

            <Box>
                <Typography variant="h6" gutterBottom>
                    Metrics
                </Typography>
                <Paper variant="outlined">
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Name</TableCell>
                                <TableCell>Value</TableCell>
                                <TableCell>Unit</TableCell>
                                <TableCell>Last Update</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>{renderMetricsRows(agent.metrics)}</TableBody>
                    </Table>
                </Paper>
            </Box>
        </Box>
    );
};

export default AgentData;

