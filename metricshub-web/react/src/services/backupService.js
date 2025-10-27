import { configApi } from "../api/config";
import { timestampId } from "../utils/backup";
import { encodeBackupFileName, isBackupFileName, parseBackupFileName } from "../utils/backupNames";

/**
 * Create a backup set.
 * - kind: 'file' | 'all'
 * - For 'file', name must be provided (the file to back up)
 * - For 'all', backs up each non-backup entry from list
 *
 * @param {Array<{name:string}>} list
 * @param {Record<string, { content?: string }>} filesByName
 * @param {Record<string, string>} originalsByName
 * @param {"file"|"all"} kind
 * @param {string|undefined} name Optional file name when kind==='file'
 * @returns {Promise<{ id: string, count: number }>} id used in backup filenames and count of originals backed up
 */
export async function createBackupSet(
    list = [],
    filesByName = {},
    originalsByName = {},
    kind,
    name,
) {
    if (kind !== "file" && kind !== "all") throw new Error("kind must be 'file' or 'all'");

    const id = timestampId(new Date()); // e.g. 20251016-150220

    // Resolve content: prefer editor cache, then originals, else fetch from backend
    const resolveContent = async (fname) => {
        const cached = filesByName[fname]?.content;
        if (cached != null) return String(cached);
        const orig = originalsByName[fname];
        if (orig != null) return String(orig);
        const fetched = await configApi.getContent(fname);
        return String(fetched ?? "");
    };

    if (kind === "file") {
        const target = name;
        if (!target) throw new Error("No file selected/name provided for file backup");
        const content = await resolveContent(target);
        const backupName = encodeBackupFileName(id, target);
        await configApi.saveOrUpdateBackupFile(backupName, content);
        return { id, count: 1 };
    }

    // kind === 'all'
    const originalsOnly = (Array.isArray(list) ? list : []).filter(
        (meta) => !isBackupFileName(meta?.name || ""),
    );
    if (originalsOnly.length === 0) return { id, count: 0 };

    await Promise.all(
        originalsOnly.map(async (meta) => {
            const fname = meta.name;
            const content = await resolveContent(fname);
            const backupName = encodeBackupFileName(id, fname);
            await configApi.saveOrUpdateBackupFile(backupName, content);
        }),
    );

    return { id, count: originalsOnly.length };
}

/**
 * Prepare restore info for a backup file.
 * - Validates backupName format
 * - Reads content from cache if available, otherwise fetches from backend
 * - Decides the final restoreName considering overwrite flag and existing list entries
 *
 * @param {string} backupName
 * @param {boolean} overwrite
 * @param {any} state Root redux state (used to read config.list and cached filesByName)
 * @returns {Promise<{ originalName: string, restoreName: string, content: string }>}
 */
export async function restoreBackupFile(backupName, overwrite, state) {
    if (!backupName) throw new Error("Missing backup file name");
    const parsed = parseBackupFileName(backupName);
    if (!parsed) throw new Error("Not a backup file");
    const { originalName } = parsed;

    // Get backup content: prefer cache else fetch
    const cached = state?.config?.filesByName?.[backupName]?.content;
    const content =
        cached != null ? String(cached) : String(await configApi.getBackupFileContent(backupName));

    // Determine target name
    let restoreName = originalName;
    if (!overwrite) {
        const list = Array.isArray(state?.config?.list) ? state.config.list : [];
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
