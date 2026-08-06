import { dataGridSx, sectionTitleSx } from "../explorer/views/common/table-styles";

export { sectionTitleSx, dataGridSx };

/**
 * Explorer {@link dataGridSx} plus hosts-only tweaks for protocol chips.
 * Do not override backgrounds — theme + transparent scroller match Explorer.
 */
export const hostsDataGridSx = {
	...dataGridSx,
	"& .MuiDataGrid-cell": {
		...dataGridSx["& .MuiDataGrid-cell"],
		py: 0.75,
	},
	"& .hosts-resource-table-protocols-cell": {
		alignItems: "flex-start",
		py: 1,
	},
};
