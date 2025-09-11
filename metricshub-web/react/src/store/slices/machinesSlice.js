import { createSlice } from "@reduxjs/toolkit";

const initialState = {
    // Tree-style data. Replace later with API results.
    // id must be strings for TreeView.
    nodes: [
        {
            id: "grp-europe",
            name: "Europe",
            children: [
                { id: "host-berlin-1", name: "berlin-1" },
                { id: "host-paris-2", name: "paris-2" },
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
                    ],
                },
                {
                    id: "dc-west",
                    name: "us-west (DC-2)",
                    children: [
                        { id: "host-sf-1", name: "sf-1" },
                        { id: "host-la-2", name: "la-2" },
                    ],
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
