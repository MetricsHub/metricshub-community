import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import DashboardTable from "../../common/DashboardTable";
import { renderUtilizationContent, UtilizationStack, colorFor, colorLabelFromKey, buildUtilizationParts, getPriority } from "./Utilization";

const PivotGroupSection = ({ group, sortedInstances }) => {
    const braceIndex = group.baseName.indexOf("{");
    const displayBaseName = braceIndex === -1 ? group.baseName : group.baseName.slice(0, braceIndex);
    const [open, setOpen] = React.useState(false);

    const isUtilizationGroup = group.baseName.includes(".utilization");

    const legendItems = React.useMemo(() => {
        if (!isUtilizationGroup) return [];
        const seen = new Set();
        const items = [];

        group.metricKeys.forEach((key) => {
            const label = colorLabelFromKey(key);
            if (seen.has(label)) return;
            seen.add(label);
            items.push(label);
        });

        return items.sort((a, b) => getPriority(a) - getPriority(b));
    }, [group, isUtilizationGroup]);

    const renderHeader = () => {
        if (!isUtilizationGroup) {
            return (
                <TableHead>
                    <TableRow>
                        <TableCell>Instance</TableCell>
                        {group.metricKeys.map((key) => {
                            const braceStart = key.indexOf("{");
                            const braceEnd = key.lastIndexOf("}");
                            let colLabel = key;

                            if (braceStart !== -1 && braceEnd > braceStart) {
                                const insideBraces = key.slice(braceStart + 1, braceEnd);
                                const quoteStart = insideBraces.indexOf("\"");
                                const quoteEnd = insideBraces.lastIndexOf("\"");
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
            );
        }

        return (
            <TableHead>
                <TableRow>
                    <TableCell>Instance</TableCell>
                    <TableCell>Utilization</TableCell>
                </TableRow>
            </TableHead>
        );
    };

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
                <Box sx={{ display: "flex", alignItems: "center", columnGap: 1, flexWrap: "wrap" }}>
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

                    {isUtilizationGroup && legendItems.length > 0 && (
                        <Box
                            sx={{
                                display: "flex",
                                flexWrap: "wrap",
                                alignItems: "center",
                                gap: 0.5,
                            }}
                        >
                            {legendItems.map((label) => (
                                <Box
                                    key={label}
                                    sx={{ display: "flex", alignItems: "center", gap: 0.5, fontSize: 10 }}
                                >
                                    <Box
                                        sx={{
                                            width: 10,
                                            height: 10,
                                            borderRadius: 0.5,
                                            bgcolor: colorFor(label),
                                        }}
                                    />
                                    <Box component="span">{label}</Box>
                                </Box>
                            ))}
                        </Box>
                    )}
                </Box>

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
                    {renderHeader()}
                    <TableBody>
                        {sortedInstances.map((inst, rowIndex) => {
                            const attrs = inst?.attributes ?? {};
                            const id = attrs.id || inst.name;
                            const displayName =
                                attrs["system.device"] || attrs.name || attrs["network.interface.name"] || id;
                            const metrics = inst?.metrics ?? {};

                            if (isUtilizationGroup) {
                                const entries = group.metricKeys.map((key) => ({ key, value: metrics[key] }));
                                const parts = buildUtilizationParts(entries);
                                return (
                                    <TableRow key={id || rowIndex}>
                                        <TableCell>{displayName}</TableCell>
                                        <TableCell>
                                            <UtilizationStack parts={parts} />
                                        </TableCell>
                                    </TableRow>
                                );
                            }

                            return (
                                <TableRow key={id || rowIndex}>
                                    <TableCell>{displayName}</TableCell>
                                    {group.metricKeys.map((key) => (
                                        <TableCell key={key}>{renderUtilizationContent(key, metrics[key])}</TableCell>
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

export default PivotGroupSection;
