import * as React from "react";
import { SnackbarContext } from "../contexts/global-snackbar-context";

/**
 * Hook to access the global snackbar API.
 *
 * @returns {{ show: (message: string, options?: {severity?: 'success'|'info'|'warning'|'error', autoHideDuration?: number}) => void }}
 */
export function useSnackbar() {
	return React.useContext(SnackbarContext);
}
