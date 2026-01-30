import React, { memo } from "react";
import PropTypes from "prop-types";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import { dataGridSx } from "../explorer/views/common/table-styles";

/** Column definitions for key-value DataGrid tables */
const KEY_VALUE_COLUMNS = [
    { field: "property", headerName: "PROPERTY", flex: 1, minWidth: 200 },
    { field: "value", headerName: "VALUE", flex: 2, minWidth: 200 },
];

/**
 * Reusable key-value table component using DataGrid.
 */
const KeyValueTable = memo(({ title, rows }) => {
    if (!rows || rows.length === 0) return null;

    return (
        <Box>
            <Typography variant="h6" sx={{ mb: 2 }}>
                {title}
            </Typography>
            <DataGrid
                rows={rows}
                columns={KEY_VALUE_COLUMNS}
                disableRowSelectionOnClick
                hideFooter
                autoHeight
                density="compact"
                sx={dataGridSx}
            />
        </Box>
    );
});

KeyValueTable.displayName = "KeyValueTable";

KeyValueTable.propTypes = {
    title: PropTypes.string.isRequired,
    rows: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.string.isRequired,
            property: PropTypes.string.isRequired,
            value: PropTypes.string.isRequired,
        })
    ),
};

export default KeyValueTable;
