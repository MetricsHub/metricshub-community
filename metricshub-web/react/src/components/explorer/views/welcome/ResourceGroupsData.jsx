import * as React from "react";
import { Box, Typography, Table, TableBody, TableCell, TableHead, TableRow, Paper } from "@mui/material";

const countResources = (group) => {
    const resources = group?.resources ?? [];
    return Array.isArray(resources) ? resources.length : 0;
};

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

const ResourceGroupsData = ({ resourceGroups }) => {
    const groups = resourceGroups ?? [];

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

