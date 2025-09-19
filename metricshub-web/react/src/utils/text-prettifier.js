// General text helpers used across the app

// Prettify keys like "cpuModel", "otel.status", "gpu-id" → "Cpu Model", "Otel Status", "GPU Id"
export function prettifyKey(key = "") {
    const ACR = new Set(["id", "url", "api", "cpu", "gpu", "os", "otel", "cc"]);
    const k = String(key)
        .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
        .replace(/[._-]+/g, " ")
        .trim()
        .toLowerCase();

    return k
        .split(/\s+/)
        .map((w) => (ACR.has(w) ? w.toUpperCase() : w.charAt(0).toUpperCase() + w.slice(1)))
        .join(" ");
}
