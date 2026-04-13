import * as React from "react";
import { useBeforeUnload } from "react-router-dom";
import { useAppSelector } from "../../hooks/store";

/**
 * Global guard that warns about unsaved configuration changes.
 * - On browser/tab close: uses native beforeunload prompt (Stay/Leave only)
 * - On in-app navigation: shows custom dialog with Stay / Discard / Save (when possible)
 */
export default function UnsavedChangesGuard() {
	const dirtyByName = useAppSelector((s) => s.config?.dirtyByName) || {};
	const hasAnyDirty = Object.values(dirtyByName).some(Boolean);

	// Show native Stay/Leave prompt when closing/reloading the tab or window
	useBeforeUnload(
		React.useCallback(
			(e) => {
				if (hasAnyDirty) {
					e.preventDefault();
					e.returnValue = ""; // required by some browsers
				}
			},
			[hasAnyDirty],
		),
	);

	return null;
}
