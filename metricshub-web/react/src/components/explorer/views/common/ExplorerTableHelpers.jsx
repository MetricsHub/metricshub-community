import * as React from "react";
import { TableRow, TableCell } from "@mui/material";
import { emptyStateCellSx } from "./table-styles";

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
		return (
			<TableRow>
				<TableCell colSpan={colSpan} sx={emptyStateCellSx}>
					No attributes
				</TableCell>
			</TableRow>
		);
	}

	return entries.map(([key, value]) => (
		<TableRow key={key}>
			<TableCell>{key}</TableCell>
			<TableCell>{String(value)}</TableCell>
		</TableRow>
	));
};
