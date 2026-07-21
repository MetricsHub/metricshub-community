import * as React from "react";

const KEY_PREFIX = "metricshub-configuration:editor-scroll";

/**
 * Remembers the CodeMirror scroll position per configuration file (sessionStorage)
 * so returning to the file within the same session restores where the user was.
 *
 * @param {object} options
 * @param {string} options.repo "config" | "otel"
 * @param {string | null | undefined} options.selected selected file name
 * @param {React.RefObject<{ scrollDOM?: HTMLElement } | null>} options.editorViewRef CodeMirror view
 * @param {boolean} options.ready editor mounted and the file content is loaded
 * @param {string | null | undefined} options.content current content (restore waits for it)
 */
export const useEditorScrollMemory = ({ repo, selected, editorViewRef, ready, content }) => {
	const restoredForRef = React.useRef(null);

	React.useEffect(() => {
		if (!selected || !ready || typeof content !== "string") {
			return undefined;
		}
		const scroller = editorViewRef.current?.scrollDOM;
		if (!scroller) {
			return undefined;
		}
		const key = `${KEY_PREFIX}:${repo}:${selected}`;

		if (restoredForRef.current !== selected) {
			restoredForRef.current = selected;
			const saved = Number(sessionStorage.getItem(key));
			if (Number.isFinite(saved) && saved > 0) {
				// One frame later so CodeMirror has applied the freshly loaded document.
				requestAnimationFrame(() => {
					scroller.scrollTop = saved;
				});
			}
		}

		let timer = null;
		const onScroll = () => {
			clearTimeout(timer);
			timer = setTimeout(() => {
				try {
					sessionStorage.setItem(key, String(scroller.scrollTop));
				} catch {
					// Ignore quota errors — losing a scroll position is harmless.
				}
			}, 150);
		};
		scroller.addEventListener("scroll", onScroll, { passive: true });
		return () => {
			clearTimeout(timer);
			scroller.removeEventListener("scroll", onScroll);
		};
	}, [repo, selected, ready, content, editorViewRef]);
};
