import * as React from "react";
import { useSelector } from "react-redux";
import { Box, CircularProgress, Divider, Typography } from "@mui/material";
import {
    selectExplorerHierarchy,
    selectExplorerLoading,
    selectExplorerError,
} from "../../../../store/slices/explorer-slice";
import ResourceHeader from "./ResourceHeader";
import ResourceMetrics from "./ResourceMetrics";

/**
 * Single resource focused page.
 *
 * @param {{
 *   resourceName?: string,
 *   resourceGroupName?: string,
 * }} props
 * @returns {JSX.Element | null}
 */
const ResourceView = ({ resourceName, resourceGroupName }) => {
    const hierarchy = useSelector(selectExplorerHierarchy);
    const loading = useSelector(selectExplorerLoading);
    const error = useSelector(selectExplorerError);

    if (loading && !hierarchy) {
        return (
            <Box display="flex" justifyContent="center" alignItems="center" height="100%">
                <CircularProgress />
            </Box>
        );
    }

    if (error && !hierarchy) {
        return (
            <Box p={2}>
                <Typography color="error">{error}</Typography>
            </Box>
        );
    }

    if (!hierarchy) {
        return (
            <Box p={2}>
                <Typography>No data available.</Typography>
            </Box>
        );
    }

    const decodedName = resourceName ? decodeURIComponent(resourceName) : null;

    let resource = null;
    const resourceGroups = hierarchy.resourceGroups || [];
    const topLevelResources = hierarchy.resources || [];

    if (decodedName) {
        if (resourceGroupName) {
            const decodedGroup = decodeURIComponent(resourceGroupName);
            const group =
                resourceGroups.find((g) => g.name === decodedGroup || g.id === decodedGroup) || null;
            if (group && Array.isArray(group.resources)) {
                resource =
                    group.resources.find(
                        (r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
                    ) || null;
            }
        } else {
            resource =
                topLevelResources.find(
                    (r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
                ) || null;
        }
    }

    if (!resource) {
        return (
            <Box p={2}>
                <Typography>No resource selected.</Typography>
            </Box>
        );
    }

    const metrics = resource.metrics || [];

    return (
        <Box p={2} display="flex" flexDirection="column" gap={4}>
            <ResourceHeader resource={resource} />
            <Divider />
            <ResourceMetrics metrics={metrics} />
        </Box>
    );
};

export default ResourceView;
