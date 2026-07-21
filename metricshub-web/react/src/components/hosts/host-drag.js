/** MIME type for host drag-and-drop between resource groups and standalone. */
export const HOST_DRAG_MIME = "application/vnd.metricshub.host-move+json";

/** Drag payload {@code sourceGroup} value for standalone (top-level) hosts. */
export const HOST_DRAG_STANDALONE = "__standalone__";

/**
 * @param {string} sourceGroup resource group name or {@link HOST_DRAG_STANDALONE}
 * @param {string} targetGroup resource group name or {@link HOST_DRAG_STANDALONE}
 * @returns {boolean}
 */
export const isHostDragNoOp = (sourceGroup, targetGroup) => sourceGroup === targetGroup;

/**
 * @param {DragEvent} event
 * @param {string} sourceGroup resource group name or {@link HOST_DRAG_STANDALONE}
 * @param {string} hostId
 */
export function setHostDragData(event, sourceGroup, hostId) {
	event.dataTransfer.setData(HOST_DRAG_MIME, JSON.stringify({ sourceGroup, hostId }));
	event.dataTransfer.effectAllowed = "move";
}

/**
 * @param {DragEvent} event
 * @returns {{ sourceGroup: string; hostId: string } | null}
 */
export function readHostDragData(event) {
	try {
		const raw = event.dataTransfer.getData(HOST_DRAG_MIME);
		if (!raw) {
			return null;
		}
		const parsed = JSON.parse(raw);
		if (parsed?.sourceGroup && parsed?.hostId) {
			return parsed;
		}
	} catch {
		// ignore invalid payload
	}
	return null;
}

/**
 * Drop-zone handlers for a resource group or the standalone section.
 *
 * @param {object} options
 * @param {string} options.targetGroup group name or {@link HOST_DRAG_STANDALONE}
 * @param {(sourceGroup: string, hostId: string, targetGroup: string) => Promise<void>} [options.onMoveHost]
 * @param {React.Dispatch<React.SetStateAction<string | null>>} options.setDropTarget
 */
export function createHostDropZoneHandlers({ targetGroup, onMoveHost, setDropTarget }) {
	return {
		onDragOver: (event) => {
			if (!onMoveHost) {
				return;
			}
			event.preventDefault();
			event.dataTransfer.dropEffect = "move";
		},
		onDragEnter: () => setDropTarget(targetGroup),
		onDragLeave: (event) => {
			if (!event.currentTarget.contains(event.relatedTarget)) {
				setDropTarget((prev) => (prev === targetGroup ? null : prev));
			}
		},
		onDrop: async (event) => {
			event.preventDefault();
			setDropTarget(null);
			if (!onMoveHost) {
				return;
			}
			const payload = readHostDragData(event);
			if (!payload || isHostDragNoOp(payload.sourceGroup, targetGroup)) {
				return;
			}
			await onMoveHost(payload.sourceGroup, payload.hostId, targetGroup);
		},
	};
}
