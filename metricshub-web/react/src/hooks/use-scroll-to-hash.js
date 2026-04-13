import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";

/**
 * Hook to handle scrolling to an element based on the URL hash.
 * Returns the decoded ID from the hash to be used for highlighting.
 *
 * @param {number} [delay=100] - Delay in ms before scrolling (to allow rendering).
 * @returns {string|null} The decoded ID from the hash, or null if none.
 */
export const useScrollToHash = (delay = 100) => {
	const location = useLocation();
	const [highlightedId, setHighlightedId] = useState(null);

	useEffect(() => {
		if (location.hash) {
			const id = location.hash.substring(1);
			const decodedId = decodeURIComponent(id);
			setHighlightedId(decodedId);

			const timer = setTimeout(() => {
				const element = document.getElementById(decodedId);
				if (element) {
					element.scrollIntoView({ behavior: "smooth", block: "center" });
				}
			}, delay);

			return () => clearTimeout(timer);
		} else {
			setHighlightedId(null);
		}
	}, [location.hash, delay]);

	return highlightedId;
};
