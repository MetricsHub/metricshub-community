import * as React from "react";
import {
    Box,
    Typography,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Accordion,
    AccordionSummary,
    AccordionDetails,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { prettifyKey } from "../../../../utils/text-prettifier";
import DashboardTable from "../common/DashboardTable";

/**
 * Monitors section displayed inside the Resource page.
 *
 * - Shows H2 "Monitors".
 * - For each monitor type, if instances count <= 20, lists all instances
 *   and their metrics in a table.
 * - If instances count > 20, shows a badge with the count that would
 *   redirect to a dedicated monitor type page (navigation not wired yet).
 * - Shows a "last updated" label based on when the resource
 *   data was last fetched (provided by parent).
 *
 * @param {{ connectors?: any[], lastUpdatedAt?: number | string | Date }} props
 */
const MonitorsView = ({ connectors, lastUpdatedAt }) => {
    // Natural sort for metric names like cpu0, cpu1, cpu10, ...
    const naturalMetricCompare = React.useCallback((a, b) => {
        const nameA = a[0];
        const nameB = b[0];
        const re = /(.*?)(\d+)$/;
        const ma = nameA.match(re);
        const mb = nameB.match(re);
        if (!ma || !mb || ma[1] !== mb[1]) {
            // Fallback to plain string compare when no common prefix+index
            return nameA.localeCompare(nameB);
        }
        const idxA = parseInt(ma[2], 10);
        const idxB = parseInt(mb[2], 10);
        if (Number.isNaN(idxA) || Number.isNaN(idxB)) {
            return nameA.localeCompare(nameB);
        }
        if (idxA === idxB) return 0;
        return idxA < idxB ? -1 : 1;
    }, []);
    // Aggregate monitors from all connectors and always return an array
    const allMonitors = React.useMemo(() => {
        if (!Array.isArray(connectors)) return [];
        const list = [];
        for (const connector of connectors) {
            if (Array.isArray(connector?.monitors)) {
                for (const m of connector.monitors) {
                    list.push({ ...m, connectorName: connector.name });
                }
            }
        }
        return list;
    }, [connectors]);

    const safeMonitors = React.useMemo(
        () => (Array.isArray(allMonitors) ? allMonitors : []),
        [allMonitors],
    );

    // Decide whether we can pivot a monitor into one or more
    // "instances as rows, metrics as columns" tables, grouped by
    // a common base metric name (e.g. system.cpu.time.*, system.cpu.utilization.*).
    const buildPivotGroups = React.useCallback(
        (instances) => {
            if (!Array.isArray(instances) || instances.length <= 1) return [];

            const perInstanceEntries = instances.map((inst) => {
                const metrics = inst?.metrics ?? {};
                return Object.entries(metrics)
                    .filter(([name]) => !name.startsWith("__"))
                    .sort(naturalMetricCompare);
            });

            const metricKeySets = perInstanceEntries.map((entries) => entries.map(([name]) => name));
            const [firstKeys = []] = metricKeySets;
            if (!firstKeys.length) return [];

            const allSameKeys = metricKeySets.every(
                (keys) => keys.length === firstKeys.length && keys.every((k, i) => k === firstKeys[i]),
            );
            if (!allSameKeys) return [];

            // Derive groups by base name before the last dot, if any.
            const groupsMap = new Map();
            for (const key of firstKeys) {
                const lastDot = key.lastIndexOf(".");
                const base = lastDot > 0 ? key.slice(0, lastDot) : key;
                if (!groupsMap.has(base)) {
                    groupsMap.set(base, []);
                }
                groupsMap.get(base).push(key);
            }

            return Array.from(groupsMap.entries()).map(([baseName, metricKeys]) => ({
                baseName,
                metricKeys,
            }));
        },
        [naturalMetricCompare],
    );

    const renderUtilizationContent = (key, raw) => {
        if (typeof raw !== "number" || !key.includes(".utilization")) {
            return raw != null ? String(raw) : "";
        }

        const clamped = Math.max(0, Math.min(raw, 1));
        const pct = Math.round(clamped * 100);

        return (
            <Box
                sx={{
                    position: "relative",
                    height: 16,
                    borderRadius: 1,
                    bgcolor: "action.hover",
                    overflow: "hidden",
                }}
            >
                <Box
                    sx={{
                        position: "absolute",
                        inset: 0,
                        width: `${pct}%`,
                        background: (theme) =>
                            `linear-gradient(90deg, ${theme.palette.success.main}, ${theme.palette.warning.main}, ${theme.palette.error.main})`,
                        transition: "width 0.2s ease-out",
                    }}
                />
                <Box
                    sx={{
                        position: "relative",
                        zIndex: 1,
                        fontSize: 11,
                        textAlign: "center",
                        color: "common.white",
                    }}
                >
                    {pct}%
                </Box>
            </Box>
        );
    };

    const lastUpdatedLabel = React.useMemo(() => {
        if (!lastUpdatedAt) return "Never";

        const ts =
            lastUpdatedAt instanceof Date
                ? lastUpdatedAt.getTime()
                : typeof lastUpdatedAt === "number"
                    ? lastUpdatedAt
                    : new Date(lastUpdatedAt).getTime();
        if (Number.isNaN(ts)) return "Never";

        // Only display local time, not the full date
        return new Date(ts).toLocaleTimeString();
    }, [lastUpdatedAt]);

    if (safeMonitors.length === 0) {
        return (
            <Box>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                    <Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
                        Monitors
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        Last updated: {lastUpdatedLabel}
                    </Typography>
                </Box>
                <Typography variant="body2">No monitors available for this resource.</Typography>
            </Box>
        );
    }

    const PivotGroupSection = ({ group, sortedInstances }) => {
        const braceIndex = group.baseName.indexOf("{");
        const displayBaseName = braceIndex === -1 ? group.baseName : group.baseName.slice(0, braceIndex);
        const [open, setOpen] = React.useState(true);

        return (
            <Box key={group.baseName} mb={2}>
                <Box
                    sx={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        cursor: "pointer",
                        mb: 0.5,
                        px: 0.75,
                        py: 0.25,
                        borderRadius: 1,
                        bgcolor: "action.hover",
                        transition: "background-color 0.15s ease-in-out",
                        "&:hover": {
                            bgcolor: "action.selected",
                        },
                    }}
                    onClick={() => setOpen((prev) => !prev)}
                >
                    <Typography
                        variant="subtitle2"
                        sx={{
                            fontWeight: 500,
                            display: "flex",
                            alignItems: "center",
                            columnGap: 1,
                        }}
                    >
                        {displayBaseName}
                    </Typography>
                    <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ display: "flex", alignItems: "center", columnGap: 0.5 }}
                    >
                        {open ? "Hide" : "Show"}
                        <Box
                            component="span"
                            sx={{
                                display: "inline-block",
                                transform: open ? "rotate(90deg)" : "rotate(0deg)",
                                transition: "transform 0.15s ease-in-out",
                                fontSize: 14,
                            }}
                        >
                            ▶
                        </Box>
                    </Typography>
                </Box>

                {open && (
                    <DashboardTable stickyHeader={false}>
                        <TableHead>
                            <TableRow>
                                <TableCell>Instance</TableCell>
                                {group.metricKeys.map((key) => {
                                    const braceStart = key.indexOf("{");
                                    const braceEnd = key.lastIndexOf("}");
                                    let colLabel = key;

                                    if (braceStart !== -1 && braceEnd > braceStart) {
                                        const insideBraces = key.slice(braceStart + 1, braceEnd);
                                        const quoteStart = insideBraces.indexOf('"');
                                        const quoteEnd = insideBraces.lastIndexOf('"');
                                        if (quoteStart !== -1 && quoteEnd > quoteStart) {
                                            colLabel = insideBraces.slice(quoteStart + 1, quoteEnd);
                                        } else {
                                            colLabel = insideBraces;
                                        }
                                    }

                                    return <TableCell key={key}>{colLabel}</TableCell>;
                                })}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {sortedInstances.map((inst, rowIndex) => {
                                const attrs = inst?.attributes ?? {};
                                const id = attrs.id || inst.name;
                                const displayName =
                                    attrs["system.device"] || attrs.name || attrs["network.interface.name"] || id;
                                const metrics = inst?.metrics ?? {};
                                return (
                                    <TableRow key={id || rowIndex}>
                                        <TableCell>{displayName}</TableCell>
                                        {group.metricKeys.map((key) => (
                                            <TableCell key={key}>
                                                {renderUtilizationContent(key, metrics[key])}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </DashboardTable>
                )}
            </Box>
        );
    };

    return (
        <Box display="flex" flexDirection="column">
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
                    Monitors
                </Typography>
                <Typography variant="caption" color="text.secondary">
                    Last updated: {lastUpdatedLabel}
                </Typography>
            </Box>

            {safeMonitors.map((monitor, index) => {
                const instances = Array.isArray(monitor.instances) ? monitor.instances : [];
                const pivotGroups = buildPivotGroups(instances);
                const sortedInstances = [...instances].sort((a, b) => {
                    const attrsA = a?.attributes ?? {};
                    const attrsB = b?.attributes ?? {};
                    const idA = attrsA.id ?? a.name ?? "";
                    const idB = attrsB.id ?? b.name ?? "";
                    const numA = Number(idA);
                    const numB = Number(idB);
                    if (!Number.isNaN(numA) && !Number.isNaN(numB)) {
                        return numA - numB;
                    }
                    return String(idA).localeCompare(String(idB));
                });

                return (
                    <Accordion
                        key={monitor.name}
                        defaultExpanded={false}
                        disableGutters
                        elevation={0}
                        square
                        sx={{
                            bgcolor: "transparent",
                            borderTop: "1px solid",
                            borderColor: "divider",
                            ...(index === safeMonitors.length - 1
                                ? { borderBottom: "1px solid", borderBottomColor: "divider" }
                                : {}),
                        }}
                    >
                        <AccordionSummary
                            expandIcon={<ExpandMoreIcon />}
                            sx={{
                                minHeight: 40,
                                cursor: "pointer",
                                bgcolor: "background.default",
                                "&:hover": {
                                    bgcolor: "action.hover",
                                },
                                "& .MuiAccordionSummary-content": { my: 0, ml: 0 },
                            }}
                        >
                            <Typography
                                variant="subtitle1"
                                sx={{
                                    fontWeight: 500,
                                    display: "flex",
                                    alignItems: "center",
                                    columnGap: 1,
                                }}
                            >
                                {prettifyKey(monitor.name)}
                                <Box
                                    component="span"
                                    sx={{
                                        ml: 1,
                                        px: 1,
                                        minWidth: 24,
                                        textAlign: "center",
                                        borderRadius: 999,
                                        fontSize: 12,
                                        fontWeight: 500,
                                        bgcolor: "action.selected",
                                        color: "text.primary",
                                    }}
                                >
                                    {instances.length}
                                </Box>
                            </Typography>
                        </AccordionSummary>
                        <AccordionDetails sx={{ px: 1.5, pb: 2, pt: 1 }}>
                            {pivotGroups.length > 0 ? (
                                pivotGroups.map((group) => (
                                    <PivotGroupSection
                                        key={group.baseName}
                                        group={group}
                                        sortedInstances={sortedInstances}
                                    />
                                ))
                            ) : (
                                sortedInstances.map((inst) => {
                                    const attrs = inst?.attributes ?? {};
                                    const id = attrs.id || inst.name;
                                    const displayName =
                                        attrs["system.device"] || attrs.name || attrs["network.interface.name"] || id;
                                    const extraInfoParts = [];
                                    if (attrs.name && attrs.name !== displayName)
                                        extraInfoParts.push(`name: ${attrs.name}`);
                                    if (attrs.serial_number)
                                        extraInfoParts.push(`serial_number: ${attrs.serial_number}`);
                                    if (attrs.vendor) extraInfoParts.push(`vendor: ${attrs.vendor}`);
                                    if (attrs.info) extraInfoParts.push(`info: ${attrs.info}`);

                                    const metrics = inst?.metrics ?? {};
                                    const metricEntries = Object.entries(metrics)
                                        .filter(([name]) => !name.startsWith("__"))
                                        .sort(naturalMetricCompare);

                                    return (
                                        <Box key={id} mb={3}>
                                            <Typography variant="subtitle1" sx={{ fontWeight: 500, mb: 1 }}>
                                                {displayName}
                                                {extraInfoParts.length > 0 && ` (${extraInfoParts.join("; ")})`}
                                            </Typography>

                                            <DashboardTable stickyHeader={false}>
                                                <TableHead>
                                                    <TableRow>
                                                        <TableCell>Name</TableCell>
                                                        <TableCell>Value</TableCell>
                                                        <TableCell>Unit</TableCell>
                                                    </TableRow>
                                                </TableHead>
                                                <TableBody>
                                                    {metricEntries.length === 0 ? (
                                                        <TableRow>
                                                            <TableCell colSpan={3}>No metrics</TableCell>
                                                        </TableRow>
                                                    ) : (
                                                        metricEntries.map(([name, value]) => (
                                                            <TableRow key={name}>
                                                                <TableCell>{name}</TableCell>
                                                                <TableCell>
                                                                    {renderUtilizationContent(name, value)}
                                                                </TableCell>
                                                                <TableCell></TableCell>
                                                            </TableRow>
                                                        ))
                                                    )}
                                                </TableBody>
                                            </DashboardTable>
                                        </Box>
                                    );
                                })
                            )}
                        </AccordionDetails>
                    </Accordion>
                );
            })}
        </Box>
    );
};

export default MonitorsView;
