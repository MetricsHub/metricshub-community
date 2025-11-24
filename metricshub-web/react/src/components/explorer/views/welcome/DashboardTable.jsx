import * as React from "react";
import { alpha } from "@mui/material/styles";
import { Paper, Table, TableContainer } from "@mui/material";

const containerSx = {
	borderRadius: 1, // subtle rounding
	boxShadow: (theme) => theme.shadows[3],
	border: (theme) => `1px solid ${alpha(theme.palette.divider, 0.5)}`,
	backgroundColor: (theme) => theme.palette.background.paper,
	overflow: "hidden",
};

const tableSx = {
	"& .MuiTableCell-root": {
		py: 1,
		px: 2,
		lineHeight: 1.45,
		borderBottom: (theme) => `1px solid ${alpha(theme.palette.divider, 0.6)}`,
	},
	"& .MuiTableHead-root .MuiTableCell-root": {
		position: "sticky",
		top: 0,
		zIndex: 2,
		backgroundColor: (theme) =>
			theme.palette.mode === "light" ? alpha(theme.palette.grey[50], 0.95) : "#7B838C",
		fontWeight: 600,
		fontSize: "0.9rem",
		letterSpacing: "0.01em",
		color: "#656D77",
		boxShadow: (theme) => `inset 0 -1px 0 ${alpha(theme.palette.divider, 0.55)}`,
	},
	"& .MuiTableBody-root .MuiTableCell-root": {
		fontSize: "0.9rem",
	},
	"& .MuiTableBody-root .MuiTableRow-root:nth-of-type(odd)": {
		backgroundColor: (theme) =>
			theme.palette.mode === "dark"
				? alpha(theme.palette.common.white, 0.04)
				: alpha(theme.palette.action.hover, 0.1),
	},
	"& .MuiTableBody-root .MuiTableRow-root:hover": {
		backgroundColor: (theme) =>
			theme.palette.mode === "dark"
				? alpha(theme.palette.common.white, 0.08)
				: alpha(theme.palette.action.hover, 0.18),
		transition: "background-color 120ms ease",
	},
	"& .MuiTableBody-root .MuiTableRow-root:last-of-type .MuiTableCell-root": {
		borderBottom: "none",
	},
};

const DashboardTable = ({ children, containerProps = {}, sx = {}, ...rest }) => (
	<TableContainer
		component={Paper}
		square={false}
		elevation={1}
		{...containerProps}
		sx={{ ...containerSx, ...(containerProps.sx || {}) }}
	>
		<Table stickyHeader size="small" sx={{ ...tableSx, ...sx }} {...rest}>
			{children}
		</Table>
	</TableContainer>
);

export default DashboardTable;
