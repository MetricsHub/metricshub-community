// src/components/ErrorBoundary.jsx
import React from "react";
import { Alert, AlertTitle } from "@mui/material";

export default class ErrorBoundary extends React.Component {
    constructor(props) { super(props); this.state = { hasError: false, err: null }; }
    static getDerivedStateFromError(err) { return { hasError: true, err }; }
    componentDidCatch(err, info) { console.error("ErrorBoundary:", err, info); }
    render() {
        if (this.state.hasError) {
            return (
                <Alert severity="error">
                    <AlertTitle>Something crashed</AlertTitle>
                    {String(this.state.err?.message || this.state.err || "Unknown error")}
                </Alert>
            );
        }
        return this.props.children;
    }
}
