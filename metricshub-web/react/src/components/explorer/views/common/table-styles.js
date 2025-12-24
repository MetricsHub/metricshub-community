export const sectionTitleSx = {
	display: "flex",
	alignItems: "center",
	gap: 0.75,
	fontWeight: 600,
	mb: 1,
};

export const truncatedCellSx = {
	whiteSpace: "nowrap",
	overflow: "hidden",
	textOverflow: "ellipsis",
};

export const dataGridSx = {
	border: 1,
	borderColor: "divider",
	borderRadius: 1,
	backgroundColor: "transparent",
	"& .MuiDataGrid-columnHeaders": {
		backgroundColor: (theme) =>
			theme.palette.mode === "dark" ? theme.palette.neutral[800] : theme.palette.neutral[100],
		color: "text.secondary",
		fontSize: "0.75rem",
		textTransform: "none",
		letterSpacing: "1px",
		"& .metric-header": {
			letterSpacing: "0px !important",
			"& .MuiDataGrid-columnHeaderTitle": {
				letterSpacing: "0px !important",
			},
		},
	},
	"& .MuiDataGrid-row": {
		"&:hover": {
			backgroundColor: "action.hover",
		},
	},
	"& .MuiDataGrid-cell": {
		borderBottom: 1,
		borderColor: "divider",
		display: "flex",
		alignItems: "center",
	},
	"& .MuiDataGrid-footerContainer": {
		borderTop: 1,
		borderColor: "divider",
	},
	"& .MuiDataGrid-virtualScroller": {
		backgroundColor: "transparent",
	},
};
