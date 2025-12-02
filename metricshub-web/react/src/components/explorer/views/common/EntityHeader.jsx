import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "./DashboardTable";
import { renderAttributesRows } from "./ExplorerTableHelpers.jsx";
import { sectionTitleSx } from "./table-styles";

/**
 * Generic header section for an entity (Resource, Resource Group, Agent),
 * showing its title/icon and attributes.
 *
 * @param {{
 *   title: React.ReactNode,
 *   iconType?: string,
 *   attributes?: Record<string, unknown>,
 *   children?: React.ReactNode
 * }} props
 * @returns {JSX.Element | null}
 */
const EntityHeader = ({ title, iconType, attributes, children }) => {
    const hasAttributes = attributes && Object.keys(attributes).length > 0;

    return (
        <Box display="flex" flexDirection="column" gap={3}>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
                <Typography
                    variant="h4"
                    gutterBottom
                    sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
                >
                    {iconType && <NodeTypeIcons type={iconType} fontSize="large" />}
                    {title}
                </Typography>
                {children}
            </Box>

            {hasAttributes && (
                <Box>
                    <Typography variant="h6" gutterBottom sx={sectionTitleSx}>
                        Attributes
                    </Typography>
                    <DashboardTable>
                        <TableHead>
                            <TableRow>
                                <TableCell>Key</TableCell>
                                <TableCell>Value</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>{renderAttributesRows(attributes)}</TableBody>
                    </DashboardTable>
                </Box>
            )}
        </Box>
    );
};

export default EntityHeader;
