import * as React from "react";
import { Snackbar, Alert } from "@mui/material";

/**
 * Global Snackbar provider and hook.
 *
 * Usage:
 *   const { show } = useSnackbar();
 *   show("Hello World", { severity: "success" });
 */
const SnackbarContext = React.createContext({
	show: () => {},
});

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

export function useSnackbar() {
	return React.useContext(SnackbarContext);
}
