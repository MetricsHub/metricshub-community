/**
 * Encode a string as Base64 using UTF-8 bytes (browser-safe for any Unicode).
 * @param {string} text
 * @returns {string}
 */
export function encodeUtf8ToBase64(text) {
	if (text == null || text === "") {
		return "";
	}
	const bytes = new TextEncoder().encode(text);
	let binary = "";
	const chunkSize = 0x8000;
	for (let i = 0; i < bytes.length; i += chunkSize) {
		const chunk = bytes.subarray(i, i + chunkSize);
		binary += String.fromCharCode.apply(null, chunk);
	}
	return btoa(binary);
}
