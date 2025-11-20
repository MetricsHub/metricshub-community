import * as React from "react";
import { Box, Typography, Table, TableBody, TableCell, TableHead, TableRow, Paper } from "@mui/material";

const renderRow = (resource) => {
    const attrs = resource.attributes ?? {};
    return (
        <TableRow key={resource.name} hover>
            <TableCell>{resource.name}</TableCell>
            <TableCell>{attrs["host.name"] ?? ""}</TableCell>
            <TableCell>{attrs["host.type"] ?? ""}</TableCell>
            <TableCell>{attrs["os.type"] ?? ""}</TableCell>
        </TableRow>
    );
};

const ResourcesData = ({ resources }) => {
    const allResources = resources ?? [];

    return (
        <Box>
            <Typography variant="h6" gutterBottom>
                Resources
            </Typography>
            <Paper variant="outlined">
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>Key</TableCell>
                            <TableCell>host.name</TableCell>
                            <TableCell>host.type</TableCell>
                            <TableCell>os.type</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {allResources.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={4}>No resources</TableCell>
                            </TableRow>
                        ) : (
                            allResources.map((r) => renderRow(r))
                        )}
                    </TableBody>
                </Table>
            </Paper>
            <Box mt={1}>
                <Typography variant="body2">
                    Clicking a resource navigates to the Resource page.
                </Typography>
            </Box>
        </Box>
    );
};

export default ResourcesData;

