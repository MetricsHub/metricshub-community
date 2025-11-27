import * as React from "react";
import { TableRow, TableCell } from "@mui/material";

/**
 * Render rows for a simple key/value attributes table.
 *
 * @param {Record<string, unknown> | undefined | null} attributes
 * @param {number} [colSpan]
 * @returns {React.ReactElement | React.ReactElement[]}
 */
export const renderAttributesRows = (attributes, colSpan = 2) => {
	const entries = Object.entries(attributes ?? {});
	if (entries.length === 0) {
		return React.createElement(
			TableRow,
			null,
			React.createElement(TableCell, { colSpan }, "No attributes"),
		);
	}

	return entries.map(([key, value]) =>
		React.createElement(
			TableRow,
			{ key },
			React.createElement(TableCell, null, key),
			React.createElement(TableCell, null, String(value)),
		),
	);
};
