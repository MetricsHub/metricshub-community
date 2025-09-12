import { createSlice } from "@reduxjs/toolkit";

function makeHosts(prefix, count) {
	return Array.from({ length: count }, (_, i) => ({
		id: `${prefix}-${i + 1}`,
		name: `${prefix}-${i + 1}`,
	}));
}

const initialState = {
	nodes: [
		{
			id: "grp-europe",
			name: "Europe",
			children: [
				{ id: "host-berlin-1", name: "berlin-1" },
				{ id: "host-paris-2", name: "paris-2" },
				// Add many hosts to force overflow
				{
					id: "eu-lab",
					name: "eu-lab",
					children: makeHosts("eu-host", 40), // <- tweak count as you like
				},
			],
		},
		{
			id: "grp-usa",
			name: "USA",
			children: [
				{
					id: "dc-east",
					name: "us-east (DC-1)",
					children: [
						{ id: "host-ny-1", name: "ny-1" },
						{ id: "host-ny-2", name: "ny-2" },
						...makeHosts("ny-node", 25),
					],
				},
				{
					id: "dc-west",
					name: "us-west (DC-2)",
					children: [
						{ id: "host-sf-1", name: "sf-1" },
						{ id: "host-la-2", name: "la-2" },
						...makeHosts("west-node", 25),
					],
				},
			],
		},
		{
			id: "grp-apac",
			name: "APAC",
			children: [
				{
					id: "dc-tokyo",
					name: "tokyo (DC-3)",
					children: makeHosts("tokyo", 30),
				},
				{
					id: "dc-sg",
					name: "singapore (DC-4)",
					children: makeHosts("sg", 30),
				},
			],
		},
	],
	selectedId: null,
};

const machinesSlice = createSlice({
	name: "machines",
	initialState,
	reducers: {
		setMachines(state, action) {
			state.nodes = action.payload;
		},
		selectMachine(state, action) {
			state.selectedId = action.payload;
		},
	},
});

export const { setMachines, selectMachine } = machinesSlice.actions;
export const machinesReducer = machinesSlice.reducer;
