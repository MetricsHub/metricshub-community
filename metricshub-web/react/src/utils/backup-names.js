const BACKUP_PREFIX = "backup-";
const BACKUP_DELIMITER = "__";
const SLASH_SENTINEL = "__slash__";

/**
 * Encode an original config filename into a flat backup filename.
 * @param {string} id timestamp-like identifier (e.g. 20251016-104205)
 * @param {string} originalName original config file path/name
 * @returns {string} encoded backup filename
 */
export function encodeBackupFileName(id, originalName) {
	if (!id) throw new Error("Backup id is required");
	const safe = String(originalName ?? "").replace(/[\\/]/g, SLASH_SENTINEL);
	return `${BACKUP_PREFIX}${id}${BACKUP_DELIMITER}${safe}`;
}

const BACKUP_REGEX = /^backup-(\d{8}-\d{6})__(.+)$/;

/**
 * Parse a backup filename produced by encodeBackupFileName.
 * @param {string} fileName
 * @returns {{ id: string, originalName: string, safeSegment: string } | null}
 */
export function parseBackupFileName(fileName) {
	const match = BACKUP_REGEX.exec(fileName ?? "");
	if (!match) return null;
	const [, id, safeSegment] = match;
	const originalName = safeSegment.replace(new RegExp(SLASH_SENTINEL, "g"), "/");
	return { id, originalName, safeSegment };
}

/**
 * Returns true if the given filename follows the backup naming convention.
 * @param {string} fileName
 */
export function isBackupFileName(fileName) {
	return parseBackupFileName(fileName) !== null;
}

export const backupNameConstants = {
	PREFIX: BACKUP_PREFIX,
	DELIMITER: BACKUP_DELIMITER,
	SLASH_SENTINEL,
	REGEX: BACKUP_REGEX,
};
