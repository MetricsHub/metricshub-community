import * as React from "react";
import { Box, Typography, Table, TableBody, TableCell, TableHead, TableRow, Paper } from "@mui/material";

/**
 * Count resources in a resource group.
 * @param {{ resources?: unknown[] }} group
 * @returns {number}
 */
const countResources = (group) => {
    const resources = group?.resources ?? [];
    return Array.isArray(resources) ? resources.length : 0;
};

/**
 * Build the display label for a resource group using its attributes.
 * @param {{ name?: string, attributes?: Record<string, unknown> }} group
 * @returns {string}
 */
const buildGroupLabel = (group) => {
    const attrs = group?.attributes ?? {};
    const parts = [];
    if (attrs.env) {
        parts.push(`(env: ${attrs.env})`);
    }
    if (attrs.owner) {
        parts.push(`(owner: ${attrs.owner})`);
    }
    if (parts.length === 0) {
        return group?.name || "";
    }
    return [group?.name || "", ...parts].join(" ").trim();
};

/**
 * Table displaying resource groups handled by the agent.
 * @param {{ resourceGroups?: Array<{ name: string, attributes?: Record<string, unknown>, resources?: unknown[] }> }} props
 * @returns {JSX.Element}
 */
const ResourceGroupsData = ({ resourceGroups }) => {
    const groups = React.useMemo(() => resourceGroups ?? [], [resourceGroups]);

    return (
        <Box>
            <Typography variant="h6" gutterBottom>
                Resource Groups
            </Typography>
            <Paper variant="outlined">
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>Key</TableCell>
                            <TableCell>Resources</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {groups.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={2}>No resource groups</TableCell>
                            </TableRow>
                        ) : (
                            groups.map((g) => (
                                <TableRow key={g.name} hover>
                                    <TableCell>{buildGroupLabel(g)}</TableCell>
                                    <TableCell>{countResources(g)}</TableCell>
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
            </Paper>
        </Box>
    );
};

export default ResourceGroupsData;

