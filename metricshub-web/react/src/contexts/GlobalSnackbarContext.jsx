import * as React from "react";
import { Snackbar, Alert } from "@mui/material";

/**
 * React context providing a simple global snackbar API.
 * Consumers access it via the `useSnackbar` hook and call `show(message, options)`
 * to display a MUI Snackbar/Alert at the bottom-center of the viewport.
 */
export const SnackbarContext = React.createContext({
	show: () => {},
});

/**
 * GlobalSnackbarProvider renders a single application-wide snackbar and exposes a `show`
 * function through the `SnackbarContext`. Wrap your app with this provider to allow
 * any descendant component to trigger snackbars without prop drilling.
 *
 * @param {{ children: React.ReactNode }} props React children to render within the provider.
 * @returns {JSX.Element} Provider element wrapping the app and rendering the snackbar portal.
 */
export default function GlobalSnackbarProvider({ children }) {
	const [state, setState] = React.useState({
		open: false,
		message: "",
		severity: "info",
		autoHideDuration: 6000,
	});

	const show = React.useCallback((message, options = {}) => {
		const { severity = "info", autoHideDuration } = options;
		if (!message) return;
		setState((s) => ({
			...s,
			open: true,
			message,
			severity,
			autoHideDuration: autoHideDuration ?? s.autoHideDuration,
		}));
	}, []);

	const handleClose = (_e, reason) => {
		if (reason === "clickaway") return;
		setState((s) => ({ ...s, open: false }));
	};

	const value = React.useMemo(() => ({ show }), [show]);

	return (
		<SnackbarContext.Provider value={value}>
			{children}
			<Snackbar
				open={state.open}
				autoHideDuration={state.autoHideDuration}
				onClose={handleClose}
				anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
			>
				<Alert
					onClose={handleClose}
					severity={state.severity}
					variant="filled"
					sx={{ width: "100%" }}
				>
					{state.message}
				</Alert>
			</Snackbar>
		</SnackbarContext.Provider>
	);
}
