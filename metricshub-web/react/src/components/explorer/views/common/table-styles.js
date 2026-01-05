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
	transition: "all 0.4s ease",
	"& .MuiDataGrid-columnHeaders": {
		transition: "all 0.4s ease",
		color: "text.secondary",
		fontSize: "0.75rem",
		textTransform: "none",
		letterSpacing: "1px",
		"& .MuiDataGrid-columnHeadersInner": {
			transition: "all 0.4s ease",
		},
		"& .metric-header": {
			letterSpacing: "0px !important",
			"& .MuiDataGrid-columnHeaderTitle": {
				letterSpacing: "0px !important",
				transition: "all 0.4s ease",
			},
		},
	},
	"& .MuiDataGrid-columnHeader": {
		transition: "all 0.4s ease",
		"& .MuiDataGrid-columnHeaderTitleContainer": {
			transition: "all 0.4s ease",
		},
		"& .MuiDataGrid-columnHeaderTitle": {
			transition: "all 0.4s ease",
		},
	},
	"& .MuiDataGrid-columnHeaderRow": {
		transition: "all 0.4s ease",
	},
	"& .MuiDataGrid-row": {
		transition: "all 0.4s ease",
		"&:hover": {
			backgroundColor: "action.hover",
		},
	},
	"& .MuiDataGrid-cell": {
		borderBottom: 1,
		borderColor: "divider",
		transition: "all 0.4s ease",
		display: "flex",
		alignItems: "center",
		color: "text.primary",
		"& *": {
			transition: "all 0.4s ease",
		},
	},
	"& .MuiDataGrid-footerContainer": {
		borderTop: 1,
		borderColor: "divider",
		transition: "all 0.4s ease",
	},
	"& .MuiDataGrid-virtualScroller": {
		backgroundColor: "transparent",
		transition: "all 0.4s ease",
	},
};
