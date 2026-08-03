import * as React from "react";
import { uiConfigApi } from "../../api/ui-config";

/**
 * Formats a duration in seconds into MetricsHub's compact form ("2m", "30s", "1h30m").
 *
 * @param {unknown} totalSeconds
 * @returns {string} empty string when the input is not a usable number
 */
export const formatDurationSeconds = (totalSeconds) => {
	const seconds = Number(totalSeconds);
	if (!Number.isFinite(seconds) || seconds < 0) {
		return "";
	}
	if (seconds === 0) {
		return "0s";
	}
	const h = Math.floor(seconds / 3600);
	const m = Math.floor((seconds % 3600) / 60);
	const s = Math.floor(seconds % 60);
	return [h ? `${h}h` : "", m ? `${m}m` : "", s ? `${s}s` : ""].join("");
};

/**
 * Effective default values a new resource inherits, given its placement:
 * agent settings overridden by the selected resource group. Used to show real
 * inherited defaults as field placeholders instead of generic examples.
 *
 * @param {string} targetType "group" | "standalone" | ""
 * @param {string} resourceGroup group name when targetType is "group"
 * @returns {object | null} the defaults DTO, or null while loading / on error
 */
export const useResourceDefaults = (targetType, resourceGroup) => {
	const groupName = targetType === "group" ? String(resourceGroup || "").trim() : "";
	const [defaults, setDefaults] = React.useState(null);

	React.useEffect(() => {
		let cancelled = false;
		uiConfigApi
			.getResourceDefaults(groupName || undefined)
			.then((data) => {
				if (!cancelled) {
					setDefaults(data);
				}
			})
			.catch(() => {
				if (!cancelled) {
					setDefaults(null);
				}
			});
		return () => {
			cancelled = true;
		};
	}, [groupName]);

	return defaults;
};
