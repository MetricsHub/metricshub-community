import { saveAs } from "file-saver";
import { configApi } from "../api/config";

/**
 * Download a config file by name.
 * If `content` is provided, it's used directly; otherwise we fetch from backend.
 * @param {{name:string, suggestedName?:string, content?:string}} args
 */
export async function downloadConfigFile({ name, suggestedName, content }) {
  const data = typeof content === "string" ? content : await configApi.getContent(name);
  const fileName = suggestedName || name || "config.yaml";
  // Use text/yaml; if your files can be .yml/.yaml/json, this is still fine for browsers
  const blob = new Blob([data ?? ""], { type: "text/yaml;charset=utf-8" });
  saveAs(blob, fileName);
}
