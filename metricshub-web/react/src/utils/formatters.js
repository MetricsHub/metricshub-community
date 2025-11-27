// src/utils/formatters.js
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import "dayjs/locale/en";
dayjs.extend(relativeTime);

export const formatBytes = (n) => {
	if (n === 0) return "0 B";
	const k = 1024;
	const sizes = ["B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"];
	const i = Math.floor(Math.log(n) / Math.log(k));
	return parseFloat((n / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
};

export const formatMetricValue = (value, unit) => {
	if (value == null) return "";
	if (typeof value !== "number") return String(value);

	const u = unit ? unit.toLowerCase() : "";

	// Handle bytes
	if (u === "by" || u === "b" || u === "byte" || u === "bytes") {
		return formatBytes(value);
	}

	// Handle other units with compact notation if large
	if (Math.abs(value) >= 1000) {
		return Intl.NumberFormat("en-US", {
			notation: "compact",
			maximumFractionDigits: 2,
		}).format(value);
	}

	return String(value);
};

export const formatRelativeTime = (isoString) => {
	const d = dayjs(isoString);
	if (!d.isValid()) return isoString;
	const diff = dayjs().diff(d);
	if (diff >= 0 && diff < 5000) return "now";
	return d.fromNow();
};
