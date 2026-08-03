const STORAGE_PREFIX = "metricshub-host-config";

/**
 * Identifies the current JS app lifetime. Sessions written with this id were saved
 * by in-app navigation; a payload with a different (or missing) id means the page
 * was actually reloaded. Scroll/step restore must only happen after a real reload —
 * returning to a resource within the app should start at the top of the form.
 */
const APP_BOOT_ID = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

/**
 * @param {{ bootId?: string } | null | undefined} payload
 * @returns {boolean} true when the payload was saved during this app lifetime
 */
export const isSessionFromCurrentAppBoot = (payload) => payload?.bootId === APP_BOOT_ID;

/**
 * @param {object} options
 * @param {"create" | "edit"} options.mode
 * @param {string | null} [options.defaultResourceGroup]
 * @param {string} [options.hostId]
 * @param {string} [options.pathname]
 * @returns {string}
 */
export const getHostFormSessionKey = ({ mode, defaultResourceGroup, hostId, pathname = "" }) => {
	const group = defaultResourceGroup || "_standalone";
	const resource = hostId || "new";
	return `${STORAGE_PREFIX}:${mode}:${group}:${resource}:${pathname}`;
};

const getHostFormScrollSessionKey = (pathname) => `${STORAGE_PREFIX}:scroll:${pathname}`;

/**
 * @param {string} pathname
 * @returns {{ scrollTop?: number; activeStep?: number } | null}
 */
export const loadHostFormScrollPosition = (pathname) => {
	if (!pathname) {
		return null;
	}
	try {
		const raw = sessionStorage.getItem(getHostFormScrollSessionKey(pathname));
		if (!raw) {
			return null;
		}
		return JSON.parse(raw);
	} catch {
		return null;
	}
};

/**
 * @param {string} pathname
 * @param {number} scrollTop
 * @param {number} activeStep
 */
export const saveHostFormScrollPosition = (pathname, scrollTop, activeStep) => {
	if (!pathname) {
		return;
	}
	try {
		sessionStorage.setItem(
			getHostFormScrollSessionKey(pathname),
			JSON.stringify({
				scrollTop,
				activeStep,
				savedAt: Date.now(),
				bootId: APP_BOOT_ID,
			}),
		);
	} catch {
		// Ignore quota errors
	}
};

/**
 * @param {string} pathname
 */
export const clearHostFormScrollPosition = (pathname) => {
	if (!pathname) {
		return;
	}
	try {
		sessionStorage.removeItem(getHostFormScrollSessionKey(pathname));
	} catch {
		// ignore
	}
};

/**
 * @param {string} key
 * @returns {object | null}
 */
export const loadHostFormSession = (key) => {
	try {
		const raw = sessionStorage.getItem(key);
		if (!raw) {
			return null;
		}
		return JSON.parse(raw);
	} catch {
		return null;
	}
};

/**
 * @param {string} key
 * @param {object} payload
 */
export const saveHostFormSession = (key, payload) => {
	try {
		sessionStorage.setItem(
			key,
			JSON.stringify({
				...payload,
				savedAt: Date.now(),
				bootId: APP_BOOT_ID,
			}),
		);
	} catch {
		// Ignore quota errors
	}
};

/**
 * @param {string} key
 */
export const clearHostFormSession = (key) => {
	try {
		sessionStorage.removeItem(key);
	} catch {
		// ignore
	}
};

/**
 * Clears the draft for a specific create/edit route (same key as {@link getHostFormSessionKey}).
 *
 * @param {Parameters<typeof getHostFormSessionKey>[0]} options
 */
export const clearHostFormSessionForRoute = (options) => {
	clearHostFormSession(getHostFormSessionKey(options));
	clearHostFormScrollPosition(options.pathname || "");
};
