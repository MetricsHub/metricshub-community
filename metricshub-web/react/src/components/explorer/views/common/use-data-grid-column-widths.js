import * as React from "react";

/**
 * Persist and reapply user-resized DataGrid column widths.
 *
 * @template {import("@mui/x-data-grid").GridColDef} T
 * @param {T[]} columns - Base column definitions.
 * @param {{ storageKey?: string }} [options] - Optional persistence settings.
 * @returns {{
 *  columns: T[],
 *  onColumnWidthChange: (params: import("@mui/x-data-grid").GridColumnResizeParams) => void,
 *  resetColumnWidths: () => void
 * }}
 */
export const useDataGridColumnWidths = (columns, options = {}) => {
    const { storageKey } = options;
    const [columnWidths, setColumnWidths] = React.useState({});

    React.useEffect(() => {
        if (!storageKey || typeof window === "undefined") {
            setColumnWidths({});
            return;
        }
        try {
            const raw = window.localStorage.getItem(storageKey);
            if (raw) {
                const parsed = JSON.parse(raw);
                setColumnWidths(parsed && typeof parsed === "object" ? parsed : {});
            } else {
                setColumnWidths({});
            }
        } catch {
            setColumnWidths({});
        }
    }, [storageKey]);

    const columnsWithWidths = React.useMemo(() => {
        if (!columnWidths || Object.keys(columnWidths).length === 0) {
            return columns;
        }
        return columns.map((col) => {
            const width = columnWidths[col.field];
            if (!width) return col;
            return {
                ...col,
                width,
                flex: undefined,
            };
        });
    }, [columns, columnWidths]);

    const handleColumnWidthChange = React.useCallback((params) => {
        setColumnWidths((prev) => ({
            ...prev,
            [params.colDef.field]: params.width,
        }));
    }, []);

    React.useEffect(() => {
        if (!storageKey || typeof window === "undefined") return;
        try {
            if (!columnWidths || Object.keys(columnWidths).length === 0) {
                window.localStorage.removeItem(storageKey);
                return;
            }
            window.localStorage.setItem(storageKey, JSON.stringify(columnWidths));
        } catch {
            // Ignore storage failures (quota, private mode, etc.)
        }
    }, [storageKey, columnWidths]);

    const resetColumnWidths = React.useCallback(() => {
        setColumnWidths({});
        if (!storageKey || typeof window === "undefined") return;
        try {
            window.localStorage.removeItem(storageKey);
        } catch {
            // Ignore storage failures
        }
    }, [storageKey]);

    return {
        columns: columnsWithWidths,
        onColumnWidthChange: handleColumnWidthChange,
        resetColumnWidths,
    };
};
