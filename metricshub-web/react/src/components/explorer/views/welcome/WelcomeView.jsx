import * as React from "react";
import { useSelector } from "react-redux";
import { Box, CircularProgress, Typography, Divider } from "@mui/material";
import { selectExplorerHierarchy, selectExplorerLoading, selectExplorerError } from "../../../../store/slices/explorer-slice";
import AgentData from "./AgentData";
import ResourceGroupsData from "./ResourceGroupsData";
import ResourcesData from "./ResourcesData";

/**
 * Default explorer welcome page displaying agent summary, groups and rogue resources.
 * @returns {JSX.Element}
 */
const WelcomeView = () => {
    const hierarchy = useSelector(selectExplorerHierarchy);
    const loading = useSelector(selectExplorerLoading);
    const error = useSelector(selectExplorerError);
    const resourceGroups = hierarchy?.resourceGroups ?? [];
    // "Rogue" resources are those not attached to any resource group.
    const rogueResources = hierarchy?.resources ?? [];
    const totalResourcesInGroups = resourceGroups.reduce((acc, g) => acc + (g.resources?.length || 0), 0);
    const totalResources = totalResourcesInGroups + rogueResources.length;

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

    return (
        <Box p={2} display="flex" flexDirection="column" gap={4}>
            <AgentData agent={hierarchy} totalResources={totalResources} />
            <Divider />
            <ResourceGroupsData resourceGroups={resourceGroups} />
            <Divider />
            <ResourcesData resources={rogueResources} />
        </Box>
    );
};

export default WelcomeView;

