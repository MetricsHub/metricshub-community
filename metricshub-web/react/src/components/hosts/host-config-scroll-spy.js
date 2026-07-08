/** Distance below the scroll-viewport top at which a section title "activates". */
export const STEP_ACTIVATION_OFFSET = 24;

/**
 * Finds the nearest scrollable ancestor of an element.
 *
 * @param {HTMLElement | null} el
 * @returns {HTMLElement | null}
 */
export const findScrollParent = (el) => {
	let node = el;
	while (node) {
		const style = window.getComputedStyle(node);
		const overflowY = style.overflowY;
		if (overflowY === "auto" || overflowY === "scroll") {
			return node;
		}
		node = node.parentElement;
	}
	return null;
};

/**
 * Computes, for each section element, the scrollTop at which it should become the
 * active step. Sections whose title can never reach the activation line (because
 * they sit inside the final, non-scrollable viewport) are spread evenly across the
 * remaining scroll distance so every trailing step is still reachable.
 *
 * @param {HTMLElement} container scroll container
 * @param {(HTMLElement | null)[]} els section elements, in step order
 * @returns {{ activateAt: number[]; scrollTop: number; maxScroll: number }}
 */
export const computeSectionActivateAt = (container, els) => {
	const containerRect = container.getBoundingClientRect();
	const scrollTop = container.scrollTop;
	const maxScroll = Math.max(0, container.scrollHeight - container.clientHeight);

	const activateAt = els.map((el) => {
		if (!el) {
			return Number.POSITIVE_INFINITY;
		}
		const top = el.getBoundingClientRect().top - containerRect.top + scrollTop;
		return Math.max(0, top - STEP_ACTIVATION_OFFSET);
	});

	const firstUnreachable = activateAt.findIndex((a) => a > maxScroll);
	if (maxScroll > 0 && firstUnreachable > 0) {
		const startIdx = firstUnreachable - 1;
		const bandStart = Math.min(activateAt[startIdx], maxScroll);
		const tailCount = activateAt.length - startIdx;
		const slice = tailCount > 0 ? (maxScroll - bandStart) / tailCount : 0;
		for (let j = 0; j < tailCount; j++) {
			activateAt[startIdx + j] = bandStart + slice * j;
		}
	}

	return { activateAt, scrollTop, maxScroll };
};

/**
 * Active step from live section geometry. Handles short sections (e.g. Ping) whose
 * title never reaches the activation line before the next section appears.
 *
 * @param {HTMLElement} container scroll container
 * @param {(HTMLElement | null)[]} els section elements, in step order
 * @param {number} [offset]
 * @returns {number}
 */
export const computeGeometryActiveStepIndex = (container, els, offset = STEP_ACTIVATION_OFFSET) => {
	const containerRect = container.getBoundingClientRect();
	const activationY = containerRect.top + offset;

	let passedTitleActive = 0;
	let containsActivation = -1;
	let shortSectionActive = -1;

	for (let i = 0; i < els.length; i++) {
		const el = els[i];
		if (!el) {
			continue;
		}
		const rect = el.getBoundingClientRect();

		if (rect.top <= activationY + 1) {
			passedTitleActive = i;
		}
		if (rect.top <= activationY && rect.bottom > activationY) {
			containsActivation = i;
		}

		const isVisible = rect.bottom > containerRect.top && rect.top < containerRect.bottom;
		if (isVisible && rect.top > activationY) {
			const prev = i > 0 ? els[i - 1] : null;
			const prevRect = prev?.getBoundingClientRect();
			const prevEnded = !prev || prevRect.bottom <= activationY + 1;
			if (prevEnded) {
				shortSectionActive = i;
			}
		}
	}

	if (containsActivation >= 0) {
		return containsActivation;
	}
	if (shortSectionActive >= 0) {
		return shortSectionActive;
	}
	return passedTitleActive;
};

/**
 * Active step from scroll position using distributed activation thresholds
 * ({@link computeSectionActivateAt}). Ensures trailing steps still activate when
 * their titles can never reach the top of the viewport.
 *
 * @param {HTMLElement} container scroll container
 * @param {(HTMLElement | null)[]} els section elements, in step order
 * @returns {number}
 */
export const computeThresholdActiveStepIndex = (container, els) => {
	const scrollTop = container.scrollTop ?? 0;
	const maxScroll = Math.max(0, (container.scrollHeight ?? 0) - (container.clientHeight ?? 0));
	if (maxScroll <= 0) {
		return 0;
	}

	const { activateAt } = computeSectionActivateAt(container, els);
	let thresholdActive = 0;
	for (let i = 0; i < activateAt.length; i++) {
		if (Number.isFinite(activateAt[i]) && scrollTop + 1 >= activateAt[i]) {
			thresholdActive = i;
		}
	}
	return thresholdActive;
};

/**
 * Balances title-at-top detection with scroll-threshold detection so every step
 * can become active, including short trailing sections near the page bottom.
 *
 * @param {HTMLElement} container scroll container
 * @param {(HTMLElement | null)[]} els section elements, in step order
 * @param {number} [offset]
 * @returns {number}
 */
export const computeActiveStepIndex = (container, els, offset = STEP_ACTIVATION_OFFSET) => {
	const geometryActive = computeGeometryActiveStepIndex(container, els, offset);
	const thresholdActive = computeThresholdActiveStepIndex(container, els);
	return Math.max(geometryActive, thresholdActive);
};

/**
 * Scroll position that places a section in view: title near the activation line, and
 * the full section visible when it is shorter than the viewport.
 *
 * @param {HTMLElement} container scroll container
 * @param {HTMLElement | null} el section element
 * @param {number} [offset] distance from viewport top for section start
 * @returns {number}
 */
export const computeScrollTargetForSection = (container, el, offset = STEP_ACTIVATION_OFFSET) => {
	if (!el) {
		return 0;
	}
	const containerRect = container.getBoundingClientRect();
	const scrollTop = container.scrollTop;
	const sectionTop = el.getBoundingClientRect().top - containerRect.top + scrollTop;
	const sectionHeight = el.offsetHeight;
	const containerHeight = container.clientHeight;
	const maxScroll = Math.max(0, container.scrollHeight - containerHeight);

	let target = sectionTop - offset;

	if (sectionHeight <= containerHeight - offset) {
		const targetShowingBottom = sectionTop + sectionHeight - containerHeight;
		target = Math.min(target, Math.max(0, targetShowingBottom));
	}

	return Math.min(Math.max(0, target), maxScroll);
};

/**
 * Scrolls a table row to just below a sticky header inside a scroll container.
 *
 * @param {HTMLElement} container table scroll container
 * @param {HTMLElement} row target row
 * @param {"auto" | "smooth"} [behavior]
 */
export const scrollTableRowBelowStickyHeader = (container, row, behavior = "smooth") => {
	const header =
		container.querySelector("thead") ?? container.querySelector(".MuiDataGrid-columnHeaders");
	const headerHeight = header instanceof HTMLElement ? header.offsetHeight : 0;
	const scrollElRaw = container.querySelector(".MuiDataGrid-virtualScroller");
	const scrollEl = scrollElRaw instanceof HTMLElement ? scrollElRaw : container;
	const containerRect = scrollEl.getBoundingClientRect();
	const rowRect = row.getBoundingClientRect();
	const rowTop = rowRect.top - containerRect.top + scrollEl.scrollTop;
	const target = Math.max(0, rowTop - headerHeight);
	scrollEl.scrollTo({ top: target, behavior });
};

/**
 * Scrolls a MUI DataGrid so a row index enters the virtual window (row may not be in the DOM yet).
 *
 * @param {HTMLElement} container grid wrapper element
 * @param {number} rowIndex zero-based row index
 * @param {number} rowHeight fixed row height in pixels
 * @param {{ current?: { scrollToIndexes?: (params: { rowIndex: number; colIndex: number }) => void } } | null | undefined} [gridApiRef]
 */
export const scrollDataGridToRowIndex = (container, rowIndex, rowHeight, gridApiRef) => {
	if (rowIndex < 0) {
		return;
	}
	if (gridApiRef?.current?.scrollToIndexes) {
		gridApiRef.current.scrollToIndexes({ rowIndex, colIndex: 0 });
		return;
	}
	const scrollElRaw = container.querySelector(".MuiDataGrid-virtualScroller");
	if (!(scrollElRaw instanceof HTMLElement)) {
		return;
	}
	const header =
		container.querySelector("thead") ?? container.querySelector(".MuiDataGrid-columnHeaders");
	const headerHeight = header instanceof HTMLElement ? header.offsetHeight : 0;
	scrollElRaw.scrollTop = Math.max(0, rowIndex * rowHeight - headerHeight);
};

/**
 * Runs {@code attempt} once per animation frame until it returns true or
 * {@code maxFrames} frames have elapsed, then calls {@code onGiveUp}.
 * Useful for DOM lookups that must wait for async rendering (e.g. DataGrid
 * virtualization) without leaving the request pending forever.
 *
 * @param {() => boolean} attempt returns true when the work succeeded
 * @param {number} maxFrames maximum number of frames to try
 * @param {() => void} [onGiveUp] called when every attempt failed
 * @returns {() => void} cancel function (stops pending attempts)
 */
export const retryOnAnimationFrames = (attempt, maxFrames, onGiveUp) => {
	let attemptsLeft = maxFrames;
	let rafId = 0;
	const tick = () => {
		if (attempt()) {
			return;
		}
		attemptsLeft -= 1;
		if (attemptsLeft > 0) {
			rafId = requestAnimationFrame(tick);
			return;
		}
		onGiveUp?.();
	};
	rafId = requestAnimationFrame(tick);
	return () => cancelAnimationFrame(rafId);
};
