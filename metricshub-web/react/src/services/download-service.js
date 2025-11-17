import JSZip from "jszip";
import { saveAs } from "file-saver";
import { configApi } from "../api/config";
import { isBackupFileName } from "../utils/backup-names";

/**
 * Download a config file by name.
 * If `content` is provided, it's used directly; otherwise we fetch from backend.
 * For backup files, uses the backup endpoint.
 * @param {{name:string, suggestedName?:string, content?:string}} args
 */
export async function downloadConfigFile({ name, suggestedName, content }) {
	let data = content;
	if (typeof data !== "string") {
		if (isBackupFileName(name)) {
			data = await configApi.getBackupFileContent(name);
		} else {
			data = await configApi.getContent(name);
		}
	}
	const fileName = suggestedName || name || "config.yaml";
	const blob = new Blob([data ?? ""], { type: "text/yaml;charset=utf-8" });
	saveAs(blob, fileName);
}

/**
 * Fetch all config files (excluding backups), zip them, and trigger a browser download.
 * @param {Array<{name:string}>} list Redux config.list
 */
export async function downloadAllConfigs(list = []) {
	const originals = list.filter((f) => !isBackupFileName(f?.name || ""));

	if (originals.length === 0) {
		alert("No configuration files to download.");
		return;
	}

	const zip = new JSZip();
	for (const f of originals) {
		try {
			const content = await configApi.getContent(f.name);
			zip.file(f.name, content ?? "");
		} catch (e) {
			console.warn(`Skipping ${f.name}: ${e.message}`);
		}
	}

	const blob = await zip.generateAsync({ type: "blob" });
	const now = new Date().toISOString().replace(/[:.]/g, "-");
	saveAs(blob, `configs-${now}.zip`);
}
