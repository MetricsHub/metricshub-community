import { useCallback, useEffect, useRef } from "react";

/**
 * Custom hook to track if a component is mounted
 * @returns {function(): boolean} - Function that returns true if the component is mounted
 */
export const useMounted = () => {
	const isMounted = useRef(false);

	useEffect(() => {
		isMounted.current = true;

		return () => {
			isMounted.current = false;
		};
	}, []);

	return useCallback(() => isMounted.current, []);
};
