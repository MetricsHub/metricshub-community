import { configApi } from "../api/config";
import { PROTOCOL_FIELDS } from "../components/hosts/protocol-definitions";
import { getOrderedSelectedProtocols } from "../components/hosts/host-config-sections";
import { encodeUtf8ToBase64 } from "./base64-utf8";

/** Minimum time to show the encrypt spinner so the UI does not flash on fast responses. */
export const ENCRYPT_SPINNER_MIN_MS = 400;

/**
 * Heuristic: MetricsHub keystore ciphertext is Base64 (AES-GCM), typically long.
 *
 * @param {string} value
 * @returns {boolean}
 */
export const looksEncrypted = (value) => {
	const s = String(value || "").trim();
	if (s.length < 32) {
		return false;
	}
	return /^[A-Za-z0-9+/=]+$/.test(s);
};

/**
 * @param {number} ms
 * @returns {Promise<void>}
 */
export const delay = (ms) =>
	new Promise((resolve) => {
		setTimeout(resolve, ms);
	});

/**
 * Encrypts a plain-text password for metricshub-ui.yaml storage.
 *
 * @param {string} plain
 * @returns {Promise<string>}
 */
export const encryptPlainPassword = async (plain) => {
	const ciphertext = await configApi.encryptPassword(encodeUtf8ToBase64(plain));
	return ciphertext;
};

export const PASSWORD_ENCRYPT_HELPER_TEXT =
	"Passwords are encrypted automatically when you change them before saving.";

/** Shown on create form protocol password fields (encryption runs on Add resource). */
export const CREATE_MODE_PASSWORD_HELPER_TEXT =
	"Password will be encrypted when you add the resource.";

/** Shown on edit-form protocol password fields (encryption runs on Save changes). */
export const EDIT_MODE_PASSWORD_HELPER_TEXT = "Password will be encrypted if you update it.";

/**
 * Encrypts plain-text password fields in a protocol form (skips already-encrypted values).
 *
 * @param {string} protocolId
 * @param {Record<string, unknown>} [formValues]
 * @returns {Promise<Record<string, unknown>>}
 */
export const encryptProtocolFormPasswordFields = async (protocolId, formValues = {}) => {
	const fields = PROTOCOL_FIELDS[protocolId] || [];
	const next = { ...formValues };
	for (const field of fields) {
		if (field.type !== "password") {
			continue;
		}
		const raw = String(next[field.name] ?? "").trim();
		if (!raw || looksEncrypted(raw)) {
			continue;
		}
		next[field.name] = await encryptPlainPassword(raw);
	}
	return next;
};

/**
 * Encrypts password fields for all selected protocols in form state (create flow submit).
 *
 * @param {object} formState
 * @returns {Promise<object>}
 */
export const encryptFormProtocolPasswords = async (formState) => {
	const selected = getOrderedSelectedProtocols(formState.selectedProtocols);
	const protocols = { ...(formState.protocols || {}) };
	for (const protocolId of selected) {
		protocols[protocolId] = await encryptProtocolFormPasswordFields(
			protocolId,
			protocols[protocolId] || {},
		);
	}
	return { ...formState, protocols };
};
