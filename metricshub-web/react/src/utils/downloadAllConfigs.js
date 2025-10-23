import JSZip from "jszip";
import { saveAs } from "file-saver";
import { configApi } from "../api/config";
import { isBackupFileName } from "./backupNames";

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
