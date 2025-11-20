import * as React from "react";
import { Box, Typography, Table, TableBody, TableCell, TableHead, TableRow, Paper } from "@mui/material";

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

const AgentData = ({ agent }) => {
    if (!agent) {
        return null;
    }

    const attributes = agent.attributes ?? {};
    const version = attributes.version || attributes.cc_version || "";
    const title = "MetricsHub Community";

    return (
        <Box display="flex" flexDirection="column" gap={3}>
            <Box>
                <Typography variant="h4" gutterBottom>
                    {title}
                </Typography>
                {version && (
                    <Typography variant="subtitle1">Version: {version}</Typography>
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

