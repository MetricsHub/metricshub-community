import { createSlice } from "@reduxjs/toolkit";

const initialState = {
    content: "",
    filename: "",
    isValid: true,
    error: "",
    isDirty: false,
};

const configEditorSlice = createSlice({
    name: "configEditor",
    initialState,
    reducers: {
        setContent(state, action) {
            state.content = action.payload;
            state.isDirty = true;
        },
        setFilename(state, action) {
            state.filename = action.payload;
            state.isDirty = true;
        },
        setValidation(state, action) {
            const { isValid, error = "" } = action.payload;
            state.isValid = isValid;
            state.error = error;
        },
        markSaved(state) {
            state.isDirty = false;
        },
        resetEditor() {
            return initialState;
        },
    },
});

export const {
    setContent,
    setFilename,
    setValidation,
    markSaved,
    resetEditor,
} = configEditorSlice.actions;

export default configEditorSlice.reducer;
