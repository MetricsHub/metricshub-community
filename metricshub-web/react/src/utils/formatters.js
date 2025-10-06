// src/utils/formatters.js
export const formatBytes = (n) =>
  Intl.NumberFormat(undefined, { notation: "compact" }).format(n) + "B";

export const formatDateTime = (s) => {
  const d = new Date(s);
  return Number.isNaN(d.getTime())
    ? s
    : new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      }).format(d);
};

export const formatRelativeTime = (isoString) => {
  const d = new Date(isoString);
  if (Number.isNaN(d.getTime())) return isoString;

  const diffMs = Date.now() - d.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHr = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHr / 24);

  if (diffSec < 60) return "just now";
  if (diffMin < 60) return `${diffMin} min ago`;
  if (diffHr < 24) return `${diffHr}h ago`;
  if (diffDay < 7) return `${diffDay}d ago`;

  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(d);
};