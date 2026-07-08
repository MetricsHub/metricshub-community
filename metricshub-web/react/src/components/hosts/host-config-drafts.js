import * as React from "react";

/**
 * Browser-local drafts for the "Add Resource" guided form. There is no backend
 * storage for unfinished resources, so drafts live in localStorage and are
 * surfaced in the guided-config tree with a "Draft" badge.
 */
const DRAFTS_STORAGE_KEY = "metricshub-host-config:drafts";
const DRAFTS_CHANGED_EVENT = "metricshub-host-config:drafts-changed";

/** @typedef {{ id: string; name: string; state: object; savedAt: number }} HostConfigDraft */

/** @returns {Record<string, HostConfigDraft>} */
const readDraftsMap = () => {
	try {
		const raw = localStorage.getItem(DRAFTS_STORAGE_KEY);
		if (!raw) {
			return {};
		}
		const parsed = JSON.parse(raw);
		return parsed && typeof parsed === "object" ? parsed : {};
	} catch {
		return {};
	}
};

/** @param {Record<string, HostConfigDraft>} map */
const writeDraftsMap = (map) => {
	try {
		localStorage.setItem(DRAFTS_STORAGE_KEY, JSON.stringify(map));
	} catch {
		// Ignore quota errors — a draft that cannot be stored is simply lost.
	}
	window.dispatchEvent(new Event(DRAFTS_CHANGED_EVENT));
};

/** @returns {HostConfigDraft[]} sorted by name */
export const listHostConfigDrafts = () =>
	Object.values(readDraftsMap()).sort((a, b) => String(a.name).localeCompare(String(b.name)));

/**
 * @param {string} draftId
 * @returns {HostConfigDraft | null}
 */
export const getHostConfigDraft = (draftId) => readDraftsMap()[draftId] ?? null;

/**
 * Creates or updates a draft.
 *
 * @param {{ id?: string | null; name?: string; state: object }} draft
 * @returns {string} the draft id
 */
export const saveHostConfigDraft = ({ id, name, state }) => {
	const draftId = id || `draft-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
	const map = readDraftsMap();
	map[draftId] = {
		id: draftId,
		name: String(name || "").trim() || "Untitled resource",
		state,
		savedAt: Date.now(),
	};
	writeDraftsMap(map);
	return draftId;
};

/** @param {string} draftId */
export const deleteHostConfigDraft = (draftId) => {
	const map = readDraftsMap();
	if (!(draftId in map)) {
		return;
	}
	delete map[draftId];
	writeDraftsMap(map);
};

/**
 * Live list of drafts; re-renders when drafts change in this tab (custom event)
 * or another tab (storage event).
 *
 * @returns {HostConfigDraft[]}
 */
export const useHostConfigDrafts = () => {
	const [drafts, setDrafts] = React.useState(listHostConfigDrafts);

	React.useEffect(() => {
		const refresh = () => setDrafts(listHostConfigDrafts());
		const onStorage = (event) => {
			if (!event.key || event.key === DRAFTS_STORAGE_KEY) {
				refresh();
			}
		};
		window.addEventListener(DRAFTS_CHANGED_EVENT, refresh);
		window.addEventListener("storage", onStorage);
		return () => {
			window.removeEventListener(DRAFTS_CHANGED_EVENT, refresh);
			window.removeEventListener("storage", onStorage);
		};
	}, []);

	return drafts;
};
