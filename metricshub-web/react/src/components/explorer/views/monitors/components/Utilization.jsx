import * as React from "react";
import { Box } from "@mui/material";

export const buildUtilizationParts = (entries) => {
    const parts = entries
        .map(({ key, value: raw }) => {
            const value = typeof raw === "number" ? Math.max(0, Math.min(raw, 1)) : 0;
            return { key, value };
        })
        .filter((p) => p.value > 0);

    if (parts.length === 0) return [];

    const total = parts.reduce((sum, p) => sum + p.value, 0) || 1;

    return parts.map((p) => ({
        key: p.key,
        value: p.value,
        pct: Math.round((p.value / total) * 100),
    }));
};

export const renderUtilizationContent = (key, raw) => {
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

export const colorLabelFromKey = (key) => {
    const braceStart = key.indexOf("{");
    const braceEnd = key.lastIndexOf("}");
    if (braceStart !== -1 && braceEnd > braceStart) {
        const insideBraces = key.slice(braceStart + 1, braceEnd);
        const quoteStart = insideBraces.indexOf("\"");
        const quoteEnd = insideBraces.lastIndexOf("\"");
        if (quoteStart !== -1 && quoteEnd > quoteStart) {
            return insideBraces.slice(quoteStart + 1, quoteEnd).toLowerCase();
        }
        return insideBraces.toLowerCase();
    }
    return key.substring(key.lastIndexOf(".") + 1).toLowerCase();
};

export const colorFor = (name) => {
    const n = name.toLowerCase();
    if (n.includes("used")) return (theme) => theme.palette.primary.main;
    if (n.includes("free")) return (theme) => theme.palette.action.disabled;
    if (n.includes("cache")) return (theme) => theme.palette.warning.main;
    if (n.includes("idle")) return (theme) => theme.palette.action.disabled;
    if (n.includes("system")) return (theme) => theme.palette.error.main;
    if (n.includes("user")) return (theme) => theme.palette.info.main;
    return (theme) => theme.palette.grey[500];
};

import HoverInfo from "./HoverInfo";

export const getPriority = (label) => {
    if (label.includes("user")) return 10;
    if (label.includes("system")) return 20;
    if (label.includes("used")) return 30;
    if (label.includes("cache")) return 40;
    if (label.includes("free")) return 90;
    if (label.includes("idle")) return 100;
    return 50;
};

export const compareUtilizationParts = (a, b) => {
    const labelA = colorLabelFromKey(a.key);
    const labelB = colorLabelFromKey(b.key);

    const pA = getPriority(labelA);
    const pB = getPriority(labelB);

    if (pA !== pB) return pA - pB;

    return a.pct - b.pct;
};

export const UtilizationStack = ({ parts }) => {
    if (!Array.isArray(parts) || parts.length === 0) return null;

    const sortedParts = [...parts].sort(compareUtilizationParts);

    return (
        <Box
            sx={{
                position: "relative",
                height: 16,
                borderRadius: 1,
                overflow: "hidden",
                bgcolor: "action.hover",
                display: "flex",
            }}
        >
            {sortedParts.map((p) => {
                const label = colorLabelFromKey(p.key);
                return (
                    <HoverInfo
                        key={p.key}
                        label={label}
                        value={p.value}
                        sx={{ width: `${p.pct}%` }}
                    >
                        <Box
                            sx={{
                                width: "100%",
                                height: "100%",
                                bgcolor: colorFor(label),
                            }}
                        />
                    </HoverInfo>
                );
            })}
        </Box>
    );
};
