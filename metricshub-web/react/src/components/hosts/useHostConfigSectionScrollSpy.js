import * as React from "react";
import { hostConfigSectionId } from "./host-config-form-layout";
import {
	computeActiveStepIndex,
	computeScrollTargetForSection,
	findScrollParent,
} from "./host-config-scroll-spy";
import { CONFIG_SECTION_FLASH_CLASS } from "./guided-config-form-primitives";

const SCROLL_POSITION_SAVE_MS = 150;
const SECTION_FLASH_MS = 2000;

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
	// While a step-click smooth-scroll is in flight, the clicked step is pinned so the
	// scroll-spy doesn't reassign it from geometry — small/trailing sections can never
	// bring their own title to the activation line, which otherwise stole the click.
	const pinnedStepRef = React.useRef(/** @type {number | null} */ (null));
	const pinReleaseTimerRef = React.useRef(
		/** @type {ReturnType<typeof setTimeout> | null} */ (null),
	);
	const flashedPanelRef = React.useRef(/** @type {HTMLElement | null} */ (null));
	const flashTimerRef = React.useRef(/** @type {ReturnType<typeof setTimeout> | null} */ (null));
	const [activeStep, setActiveStep] = React.useState(() =>
		typeof initialActiveStep === "number" ? initialActiveStep : 0,
	);

	const sectionElements = React.useCallback(
		() => steps.map((step) => document.getElementById(hostConfigSectionId(step))),
		[steps],
	);

	/**
	 * Keeps the pinned (clicked) step in effect until {@code ms} of scroll silence.
	 * Refreshed on every scroll event fired by the smooth scroll; also given a longer
	 * fallback when the target is already in view and no scroll events fire at all.
	 *
	 * @param {number} ms
	 */
	const schedulePinRelease = React.useCallback((ms) => {
		if (pinReleaseTimerRef.current) {
			clearTimeout(pinReleaseTimerRef.current);
		}
		pinReleaseTimerRef.current = setTimeout(() => {
			pinnedStepRef.current = null;
		}, ms);
	}, []);

	/**
	 * Briefly outlines the section the user just jumped to, so the target is obvious.
	 * The class is removed and re-added (with a forced reflow) to restart the CSS
	 * animation even when the same section is clicked twice in a row.
	 *
	 * @param {HTMLElement | null | undefined} sectionEl the id'd section wrapper
	 */
	const flashSection = React.useCallback((sectionEl) => {
		if (!sectionEl) {
			return;
		}
		const panel = sectionEl.querySelector("section") ?? sectionEl;
		if (flashedPanelRef.current && flashedPanelRef.current !== panel) {
			flashedPanelRef.current.classList.remove(CONFIG_SECTION_FLASH_CLASS);
		}
		if (flashTimerRef.current) {
			clearTimeout(flashTimerRef.current);
		}
		panel.classList.remove(CONFIG_SECTION_FLASH_CLASS);
		// Force reflow so the animation restarts on repeat clicks.
		void panel.offsetWidth;
		panel.classList.add(CONFIG_SECTION_FLASH_CLASS);
		flashedPanelRef.current = panel;
		flashTimerRef.current = setTimeout(() => {
			panel.classList.remove(CONFIG_SECTION_FLASH_CLASS);
			flashedPanelRef.current = null;
		}, SECTION_FLASH_MS);
	}, []);

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
				// Pin before scrolling so recompute() during the smooth-scroll animation
				// keeps this step active. Cleared shortly after scrolling settles.
				pinnedStepRef.current = index;
				schedulePinRelease(700);
				container.scrollTo({ top: target, behavior: "smooth" });
				applyActiveStep(index);
				flashSection(els[index]);
			});
		},
		[
			applyActiveStep,
			flashSection,
			formRootRef,
			onSectionExpandedChange,
			schedulePinRelease,
			sectionElements,
			steps,
		],
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
			// A step click pins its index for the duration of the smooth scroll.
			if (pinnedStepRef.current != null) {
				applyActiveStep(pinnedStepRef.current);
				return;
			}
			const els = sectionElements();
			if (els.length === 0 || els.some((el) => !el)) {
				return;
			}
			applyActiveStep(computeActiveStepIndex(container, els));
		};

		const handleScroll = () => {
			// Keep the click-pin alive while the smooth scroll is still emitting events;
			// release it soon after the scroll settles so manual scrolling resumes spying.
			if (pinnedStepRef.current != null) {
				schedulePinRelease(150);
			}
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
			if (pinReleaseTimerRef.current) {
				clearTimeout(pinReleaseTimerRef.current);
			}
			if (flashTimerRef.current) {
				clearTimeout(flashTimerRef.current);
			}
		};
	}, [
		enabled,
		applyActiveStep,
		contentKey,
		formRootRef,
		onScrollPositionChange,
		schedulePinRelease,
		scrollAnchorReady,
		scrollRestoreLocked,
		sectionElements,
		steps.length,
	]);

	return { activeStep, scrollToStepIndex };
};
