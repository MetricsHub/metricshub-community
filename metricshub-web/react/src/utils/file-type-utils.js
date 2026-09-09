import { isBackupFileName } from "./backup-names";

/**
 * Returns true if the file name is a Velocity template (.vm), optionally with .draft suffix.
 * @param {string} fileName
 * @returns {boolean}
 */
export function isVmFile(fileName) {
	if (!fileName) return false;
	const lower = fileName.toLowerCase();
	if (lower.endsWith(".draft")) {
		return lower.slice(0, -6).endsWith(".vm");
	}
	return lower.endsWith(".vm");
}

/**
 * Returns the file type category for display purposes.
 * @param {string} fileName
 * @returns {"vm"|"backup"|"file"}
 */
export function getFileType(fileName) {
	if (isBackupFileName(fileName)) return "backup";
	if (isVmFile(fileName)) return "vm";
	return "file";
}

/**
 * Removes a trailing ".draft" suffix from a configuration file name.
 * A draft and its saved counterpart designate the same configuration file, so
 * name comparisons must ignore the suffix.
 * @param {string} fileName The file name.
 * @returns {string} The file name without its ".draft" suffix.
 */
export function stripDraftSuffix(fileName) {
	return String(fileName ?? "").replace(/\.draft$/i, "");
}

/**
 * Tells whether two configuration file names designate the same file, ignoring
 * the ".draft" suffix and the case (file systems MetricsHub runs on are case
 * insensitive, so "Hosts.vm" and "hosts.vm" would collide).
 * @param {string} left  The first file name.
 * @param {string} right The second file name.
 * @returns {boolean} true when both names designate the same file.
 */
export function isSameConfigFile(left, right) {
	return stripDraftSuffix(left).toLowerCase() === stripDraftSuffix(right).toLowerCase();
}
