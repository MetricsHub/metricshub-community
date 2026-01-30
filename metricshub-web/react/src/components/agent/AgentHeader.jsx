import React, { useMemo, memo } from "react";
import PropTypes from "prop-types";
import { Box, Typography, Stack, Button, CircularProgress } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Autorenew";
import RestartAltIcon from "@mui/icons-material/RestartAlt";

const AgentHeader = memo(({
    osType,
    title,
    version,
    loading,
    restarting,
    isReadOnly,
    onRefresh,
    onRestart
}) => {
    // Memoized OS icon
    const osIcon = useMemo(() => {
        if (typeof osType !== "string") return null;
        const lower = osType.toLowerCase();
        if (lower.includes("windows")) {
            return (
                <Box component="img" src="/windows.svg" alt="Windows" sx={{ width: 48, height: 48 }} />
            );
        }
        if (lower.includes("linux")) {
            return <Box component="img" src="/linux.svg" alt="Linux" sx={{ width: 64, height: 64 }} />;
        }
        return null;
    }, [osType]);

    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                flexWrap: "wrap",
                gap: 2,
            }}
        >
            <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                {osIcon}
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 600 }}>
                        {title}
                    </Typography>
                    {version && (
                        <Typography variant="body1" color="text.secondary">
                            Version {version}
                        </Typography>
                    )}
                </Box>
            </Box>
            <Stack direction="row" spacing={1}>
                <Button
                    variant="outlined"
                    color="inherit"
                    startIcon={loading ? <CircularProgress size={16} /> : <RefreshIcon />}
                    onClick={onRefresh}
                    disabled={loading}
                >
                    Refresh
                </Button>
                <Button
                    variant="contained"
                    color="warning"
                    startIcon={
                        restarting ? <CircularProgress size={16} color="inherit" /> : <RestartAltIcon />
                    }
                    onClick={onRestart}
                    disabled={restarting || isReadOnly}
                >
                    Restart Agent
                </Button>
            </Stack>
        </Box>
    );
});

AgentHeader.displayName = "AgentHeader";

AgentHeader.propTypes = {
    osType: PropTypes.string,
    title: PropTypes.string.isRequired,
    version: PropTypes.string,
    loading: PropTypes.bool,
    restarting: PropTypes.bool,
    isReadOnly: PropTypes.bool,
    onRefresh: PropTypes.func.isRequired,
    onRestart: PropTypes.func.isRequired,
};

export default AgentHeader;
