// src/utils/formatters.js
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import "dayjs/locale/en";
dayjs.extend(relativeTime);

export const formatBytes = (n) =>
	Intl.NumberFormat(undefined, { notation: "compact" }).format(n) + "B";

export const formatDateTime = (s) => {
	const d = dayjs(s);
	return d.isValid() ? d.format("MMM DD, YYYY HH:mm") : s;
};

export const formatRelativeTime = (isoString) => {
	const d = dayjs(isoString);
	return d.isValid() ? d.fromNow() : isoString;
};
