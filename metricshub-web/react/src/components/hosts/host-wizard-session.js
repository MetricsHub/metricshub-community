const STORAGE_PREFIX = "metricshub-host-wizard";

/**
 * @param {object} options
 * @param {"create" | "edit"} options.mode
 * @param {string | null} [options.defaultResourceGroup]
 * @param {string} [options.hostId]
 * @param {string} [options.pathname]
 * @returns {string}
 */
export const getHostWizardSessionKey = ({ mode, defaultResourceGroup, hostId, pathname = "" }) => {
	const group = defaultResourceGroup || "_standalone";
	const resource = hostId || "new";
	return `${STORAGE_PREFIX}:${mode}:${group}:${resource}:${pathname}`;
};

const getHostWizardScrollSessionKey = (pathname) => `${STORAGE_PREFIX}:scroll:${pathname}`;

/**
 * @param {string} pathname
 * @returns {{ scrollTop?: number; activeStep?: number } | null}
 */
export const loadHostWizardScrollPosition = (pathname) => {
	if (!pathname) {
		return null;
	}
	try {
		const raw = sessionStorage.getItem(getHostWizardScrollSessionKey(pathname));
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
export const saveHostWizardScrollPosition = (pathname, scrollTop, activeStep) => {
	if (!pathname) {
		return;
	}
	try {
		sessionStorage.setItem(
			getHostWizardScrollSessionKey(pathname),
			JSON.stringify({
				scrollTop,
				activeStep,
				savedAt: Date.now(),
			}),
		);
	} catch {
		// Ignore quota errors
	}
};

/**
 * @param {string} pathname
 */
export const clearHostWizardScrollPosition = (pathname) => {
	if (!pathname) {
		return;
	}
	try {
		sessionStorage.removeItem(getHostWizardScrollSessionKey(pathname));
	} catch {
		// ignore
	}
};

/**
 * @param {string} key
 * @returns {object | null}
 */
export const loadHostWizardSession = (key) => {
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
export const saveHostWizardSession = (key, payload) => {
	try {
		sessionStorage.setItem(
			key,
			JSON.stringify({
				...payload,
				savedAt: Date.now(),
			}),
		);
	} catch {
		// Ignore quota errors
	}
};

/**
 * @param {string} key
 */
export const clearHostWizardSession = (key) => {
	try {
		sessionStorage.removeItem(key);
	} catch {
		// ignore
	}
};

/**
 * @param {string} key
 * @param {string} [pathname]
 */
export const clearHostWizardSessionArtifacts = (key, pathname = "") => {
	clearHostWizardSession(key);
	clearHostWizardScrollPosition(pathname);
};

/**
 * Clears the draft for a specific create/edit route (same key as {@link getHostWizardSessionKey}).
 *
 * @param {Parameters<typeof getHostWizardSessionKey>[0]} options
 */
export const clearHostWizardSessionForRoute = (options) => {
	clearHostWizardSession(getHostWizardSessionKey(options));
	clearHostWizardScrollPosition(options.pathname || "");
};
