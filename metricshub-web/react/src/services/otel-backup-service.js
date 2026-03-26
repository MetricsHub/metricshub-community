import { otelConfigApi } from "../api/config/otel-config-api";
import { timestampId } from "../utils/backup";
import { encodeBackupFileName, isBackupFileName, parseBackupFileName } from "../utils/backup-names";

/**
 * Create an OTEL backup set.
 * - kind: 'file' | 'all'
 * - For 'file', name must be provided (the OTEL file to back up)
 * - For 'all', backs up each non-backup entry from list
 *
 * @param {Array<{name:string}>} list
 * @param {Record<string, { content?: string }>} filesByName
 * @param {Record<string, string>} originalsByName
 * @param {"file"|"all"} kind
 * @param {string|undefined} name Optional file name when kind==='file'
 * @returns {Promise<{ id: string, count: number }>}
 */
export async function createOtelBackupSet(
	list = [],
	filesByName = {},
	originalsByName = {},
	kind,
	name,
) {
	if (kind !== "file" && kind !== "all") throw new Error("kind must be 'file' or 'all'");

	const id = timestampId(new Date());

	const resolveContent = async (fname) => {
		const cached = filesByName[fname]?.content;
		if (cached != null) return String(cached);
		const orig = originalsByName[fname];
		if (orig != null) return String(orig);
		const fetched = await otelConfigApi.getContent(fname);
		return String(fetched ?? "");
	};

	if (kind === "file") {
		const target = name;
		if (!target) throw new Error("No file selected/name provided for OTEL file backup");
		const content = await resolveContent(target);
		const backupName = encodeBackupFileName(id, target);
		await otelConfigApi.saveOrUpdateBackupFile(backupName, content);
		return { id, count: 1 };
	}

	const originalsOnly = (Array.isArray(list) ? list : []).filter(
		(meta) => !isBackupFileName(meta?.name || ""),
	);
	if (originalsOnly.length === 0) return { id, count: 0 };

	await Promise.all(
		originalsOnly.map(async (meta) => {
			const fname = meta.name;
			const content = await resolveContent(fname);
			const backupName = encodeBackupFileName(id, fname);
			await otelConfigApi.saveOrUpdateBackupFile(backupName, content);
		}),
	);

	return { id, count: originalsOnly.length };
}

/**
 * Prepare restore info for an OTEL backup file.
 *
 * @param {string} backupName
 * @param {boolean} overwrite
 * @param {any} state Root redux state (state.otelConfig)
 * @returns {Promise<{ originalName: string, restoreName: string, content: string }>}
 */
export async function restoreOtelBackupFile(backupName, overwrite, state) {
	if (!backupName) throw new Error("Missing backup file name");
	const parsed = parseBackupFileName(backupName);
	if (!parsed) throw new Error("Not a backup file");
	const { originalName } = parsed;

	const cached = state?.otelConfig?.filesByName?.[backupName]?.content;
	const content =
		cached != null ? String(cached) : String(await otelConfigApi.getBackupFileContent(backupName));

	let restoreName = originalName;
	if (!overwrite) {
		const list = Array.isArray(state?.otelConfig?.list) ? state.otelConfig.list : [];
		const exists = list.some((f) => f.name === originalName);
		if (exists) {
			const id = timestampId();
			const parts = originalName.split("/");
			const base = parts.pop();
			const dot = base.lastIndexOf(".");
			const withSuffix =
				dot > 0
					? `${base.slice(0, dot)}.restored-${id}${base.slice(dot)}`
					: `${base}.restored-${id}`;
			restoreName = [...parts, withSuffix].join("/");
		}
	}

	return { originalName, restoreName, content };
}
