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

export const formatSI = (n, unit) => {
	if (n === 0) return `0 ${unit}`;
	const k = 1000;
	const sizes = ["", "K", "M", "G", "T", "P", "E", "Z", "Y"];
	const i = Math.floor(Math.log(Math.abs(n)) / Math.log(k));

	// Handle very small numbers or numbers < 1000
	if (i < 0) return n.toFixed(2) + (unit ? ` ${unit}` : "");
	if (i === 0) return n.toFixed(2) + (unit ? ` ${unit}` : "");

	const value = parseFloat((n / Math.pow(k, i)).toFixed(2));
	const prefix = sizes[i];

	// If unit is provided, attach prefix to unit (e.g. kJ).
	// If no unit, just return value + prefix (e.g. 10K)
	return unit ? `${value} ${prefix}${unit}` : `${value} ${prefix}`;
};

export const formatMetricValue = (value, unit) => {
	if (value == null) return "";
	if (typeof value !== "number") return String(value);

	const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";
	const u = cleanUnit.toLowerCase();

	// Handle bytes
	if (u === "by" || u === "b" || u === "byte" || u === "bytes") {
		return formatBytes(value);
	}

	// Handle seconds as human-readable time
	if (u === "s" || u === "sec" || u === "second" || u === "seconds") {
		if (value < 1) return `${value} s`;
		const totalSeconds = Math.floor(value);
		const days = Math.floor(totalSeconds / 86400);
		const hours = Math.floor((totalSeconds % 86400) / 3600);
		const minutes = Math.floor((totalSeconds % 3600) / 60);
		const seconds = totalSeconds % 60;
		let parts = [];
		if (days > 0) parts.push(`${days}d`);
		if (hours > 0) parts.push(`${hours}h`);
		if (minutes > 0) parts.push(`${minutes}m`);
		if (seconds > 0 || parts.length === 0) parts.push(`${seconds}s`);
		return parts.join(" ");
	}

	// Use generic SI formatter for everything else
	return formatSI(value, cleanUnit);
};

export const formatRelativeTime = (isoString) => {
	const d = dayjs(isoString);
	if (!d.isValid()) return isoString;
	const diff = dayjs().diff(d);
	if (diff >= 0 && diff < 5000) return "now";
	return d.fromNow();
};
