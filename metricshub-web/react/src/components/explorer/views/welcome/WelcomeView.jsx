import * as React from "react";
import { useSelector } from "react-redux";
import { Box, CircularProgress, Typography, Divider } from "@mui/material";
import { selectExplorerHierarchy, selectExplorerLoading, selectExplorerError } from "../../../../store/slices/explorer-slice";
import AgentData from "./AgentData";
import ResourceGroupsData from "./ResourceGroupsData";
import ResourcesData from "./ResourcesData";

const collectAllResources = (hierarchy) => {
    const direct = hierarchy.resources ?? [];
    const fromGroups = (hierarchy.resourceGroups ?? []).flatMap((g) => g.resources ?? []);
    return [...direct, ...fromGroups];
};

const WelcomeView = () => {
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

    const resourceGroups = hierarchy.resourceGroups ?? [];
    const allResources = collectAllResources(hierarchy);

    return (
        <Box p={2} display="flex" flexDirection="column" gap={4}>
            <AgentData agent={hierarchy} />
            <Divider />
            <ResourceGroupsData resourceGroups={resourceGroups} />
            <Divider />
            <ResourcesData resources={allResources} />
        </Box>
    );
};

export default WelcomeView;

