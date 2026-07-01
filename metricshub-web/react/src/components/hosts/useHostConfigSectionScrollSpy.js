import * as React from "react";
import { hostConfigSectionId } from "./host-config-form-layout";
import {
	computeActiveStepIndex,
	computeScrollTargetForSection,
	findScrollParent,
} from "./host-config-scroll-spy";

const SCROLL_POSITION_SAVE_MS = 150;

/**
 * Scroll-spy for accordion configuration sections (create/edit).
 * Active step follows scroll position; clicking a step scrolls to its section.
 *
 * @param {object} options
 * @param {boolean} [options.enabled]
 * @param {Array<{ id: string }>} options.steps
 * @param {React.RefObject<HTMLElement | null>} options.formRootRef
 * @param {boolean} [options.scrollAnchorReady] true once the scroll container is mounted
 * @param {boolean} [options.scrollRestoreLocked] when true, defer step detection until scroll restore completes
 * @param {Record<string, boolean>} [options.expandedSections] reserved for accordion layouts
 * @param {(sectionId: string, expanded: boolean) => void} [options.onSectionExpandedChange]
 * @param {(index: number) => void} [options.onActiveStepChange]
 * @param {unknown} [options.contentKey] changes when section heights change (e.g. connector detection mode)
 * @param {number | null} [options.initialActiveStep] stepper index while scroll restore is pending
 * @param {(scrollTop: number) => void} [options.onScrollPositionChange]
 * @returns {{ activeStep: number; scrollToStepIndex: (index: number) => void }}
 */
export const useHostConfigSectionScrollSpy = ({
	enabled = true,
	steps,
	formRootRef,
	scrollAnchorReady = true,
	scrollRestoreLocked = false,
	onSectionExpandedChange,
	onActiveStepChange,
	contentKey,
	initialActiveStep = null,
	onScrollPositionChange,
}) => {
	const scrollContainerRef = React.useRef(/** @type {HTMLElement | null} */ (null));
	const scrollSpyReadyRef = React.useRef(!scrollRestoreLocked);
	const scrollSaveTimerRef = React.useRef(
		/** @type {ReturnType<typeof setTimeout> | null} */ (null),
	);
	const [activeStep, setActiveStep] = React.useState(() =>
		typeof initialActiveStep === "number" ? initialActiveStep : 0,
	);

	const sectionElements = React.useCallback(
		() => steps.map((step) => document.getElementById(hostConfigSectionId(step))),
		[steps],
	);

	const applyActiveStep = React.useCallback(
		(index) => {
			setActiveStep((prev) => {
				if (scrollSpyReadyRef.current && prev !== index) {
					onActiveStepChange?.(index);
				}
				return prev === index ? prev : index;
			});
		},
		[onActiveStepChange],
	);

	const scrollToStepIndex = React.useCallback(
		(index) => {
			const container = scrollContainerRef.current || findScrollParent(formRootRef.current);
			if (!container) {
				return;
			}
			scrollSpyReadyRef.current = true;
			const step = steps[index];
			if (step) {
				onSectionExpandedChange?.(step.id, true);
			}
			requestAnimationFrame(() => {
				const els = sectionElements();
				if (!els[index]) {
					return;
				}
				const target = computeScrollTargetForSection(container, els[index]);
				container.scrollTo({ top: target, behavior: "smooth" });
				applyActiveStep(index);
			});
		},
		[applyActiveStep, formRootRef, onSectionExpandedChange, sectionElements, steps],
	);

	React.useEffect(() => {
		scrollSpyReadyRef.current = !scrollRestoreLocked;
		if (typeof initialActiveStep === "number") {
			setActiveStep(initialActiveStep);
		}
	}, [initialActiveStep, scrollRestoreLocked]);

	React.useLayoutEffect(() => {
		if (!enabled || !scrollAnchorReady) {
			return undefined;
		}
		const container = findScrollParent(formRootRef.current);
		if (!container) {
			return undefined;
		}
		scrollContainerRef.current = container;

		const recompute = () => {
			if (!scrollSpyReadyRef.current) {
				return;
			}
			const els = sectionElements();
			if (els.length === 0 || els.some((el) => !el)) {
				return;
			}
			applyActiveStep(computeActiveStepIndex(container, els));
		};

		const handleScroll = () => {
			recompute();
			if (!onScrollPositionChange) {
				return;
			}
			if (scrollSaveTimerRef.current) {
				clearTimeout(scrollSaveTimerRef.current);
			}
			scrollSaveTimerRef.current = setTimeout(() => {
				onScrollPositionChange(container.scrollTop);
			}, SCROLL_POSITION_SAVE_MS);
		};

		recompute();
		container.addEventListener("scroll", handleScroll, { passive: true });
		window.addEventListener("resize", recompute);

		const observed = formRootRef.current;
		let resizeObserver;
		if (observed && typeof ResizeObserver !== "undefined") {
			resizeObserver = new ResizeObserver(() => {
				recompute();
			});
			resizeObserver.observe(observed);
		}

		return () => {
			container.removeEventListener("scroll", handleScroll);
			window.removeEventListener("resize", recompute);
			resizeObserver?.disconnect();
			if (scrollSaveTimerRef.current) {
				clearTimeout(scrollSaveTimerRef.current);
			}
		};
	}, [
		enabled,
		applyActiveStep,
		contentKey,
		formRootRef,
		onScrollPositionChange,
		scrollAnchorReady,
		scrollRestoreLocked,
		sectionElements,
		steps.length,
	]);

	return { activeStep, scrollToStepIndex };
};
