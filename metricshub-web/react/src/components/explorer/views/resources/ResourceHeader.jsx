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
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import { renderAttributesRows } from "../common/ExplorerTableHelpers";

/**
 * Header section for a single resource, showing its id and attributes.
 *
 * @param {{ resource?: any }} props
 * @returns {JSX.Element | null}
 */
const ResourceHeader = ({ resource }) => {
    if (!resource) return null;

    const attributes = resource.attributes ?? {};
    const id = resource.id || resource.key || resource.name;

    return (
        <Box display="flex" flexDirection="column" gap={3}>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
                <Typography
                    variant="h4"
                    gutterBottom
                    sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
                >
                    <NodeTypeIcons type="resource" />
                    {id}
                </Typography>
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
        </Box>
    );
};

export default ResourceHeader;
