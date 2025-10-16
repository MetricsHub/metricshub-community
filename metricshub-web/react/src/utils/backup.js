export const pad2 = (n) => String(n).padStart(2, "0");

export function timestampId(d = new Date()) {
	const yyyy = d.getFullYear();
	const mm = pad2(d.getMonth() + 1);
	const dd = pad2(d.getDate());
	const hh = pad2(d.getHours());
	const mi = pad2(d.getMinutes());
	const ss = pad2(d.getSeconds());
	return `${yyyy}${mm}${dd}-${hh}${mi}${ss}`; // e.g. 20251016-104205
}
