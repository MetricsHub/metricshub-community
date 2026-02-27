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
