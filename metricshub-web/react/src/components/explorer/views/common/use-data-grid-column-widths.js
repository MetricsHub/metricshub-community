import * as React from "react";

/**
 * Persist and reapply user-resized DataGrid column widths.
 *
 * @template {import("@mui/x-data-grid").GridColDef} T
 * @param {T[]} columns - Base column definitions.
 * @returns {{
 *  columns: T[],
 *  onColumnWidthChange: (params: import("@mui/x-data-grid").GridColumnResizeParams) => void,
 *  resetColumnWidths: () => void
 * }}
 */
export const useDataGridColumnWidths = (columns) => {
    const [columnWidths, setColumnWidths] = React.useState({});

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

    const resetColumnWidths = React.useCallback(() => {
        setColumnWidths({});
    }, []);

    return {
        columns: columnsWithWidths,
        onColumnWidthChange: handleColumnWidthChange,
        resetColumnWidths,
    };
};
