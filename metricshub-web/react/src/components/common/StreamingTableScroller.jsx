import * as React from "react";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { IconButton, Box } from "@mui/material";

const SCROLL_DISTANCE = 180;
const EPSILON = 3; // px tolerance for rounding/borders

/**
 * A component that enable scrolling a table when streaming is done.
 * And scroll the table when the user is clicking on the left or right arrow buttons.
 *
 * @param {React.ReactNode} children - The children of the component.
 * @param {boolean} isStreaming - Whether streaming is enabled.
 * @returns {React.ReactNode} The component.
 */
const StreamingTableScroller = React.memo(({ children, isStreaming = false }) => {
	const ref = React.useRef(null);

	const [canScrollLeft, setCanScrollLeft] = React.useState(false);
	const [canScrollRight, setCanScrollRight] = React.useState(false);
	const [showToolbar, setShowToolbar] = React.useState(false);

	// Update the scroll state when the user is scrolling the table.
	const updateScrollState = React.useCallback(() => {
		const el = ref.current;
		if (!el) return;

		const { scrollLeft, scrollWidth, clientWidth } = el;
		const maxScrollLeft = Math.max(0, scrollWidth - clientWidth);

		const hasOverflow = maxScrollLeft > EPSILON;
		setShowToolbar(hasOverflow);

		if (!hasOverflow) {
			setCanScrollLeft(false);
			setCanScrollRight(false);
			return;
		}

		setCanScrollLeft(scrollLeft > EPSILON);
		setCanScrollRight(scrollLeft < maxScrollLeft - EPSILON);
	}, []);

	const scrollBy = React.useCallback((dx) => {
		const el = ref.current;
		if (!el) return;

		const maxScrollLeft = Math.max(0, el.scrollWidth - el.clientWidth);
		const target = Math.min(maxScrollLeft, Math.max(0, el.scrollLeft + dx));

		// Use scrollTo so we can clamp precisely at the end
		el.scrollTo({ left: target, behavior: "smooth" });
	}, []);

	// Scroll left by a fixed distance
	const scrollLeft = React.useCallback(() => scrollBy(-SCROLL_DISTANCE), [scrollBy]);

	// Scroll right by a fixed distance
	const scrollRight = React.useCallback(() => scrollBy(SCROLL_DISTANCE), [scrollBy]);

	// Prevent text selection when clicking on the left or right arrow buttons.
	const preventInteraction = React.useCallback((e) => {
		e.preventDefault();
		e.stopPropagation();
	}, []);

	// While streaming: keep toolbar hidden + skip measuring.
	// After streaming ends: wait for layout to settle, then measure.
	React.useEffect(() => {
		const el = ref.current;
		if (!el) return;

		if (isStreaming) {
			setShowToolbar(false);
			setCanScrollLeft(false);
			setCanScrollRight(false);
			return;
		}

		// IDEA: run this after the browser has had a chance to lay out and paint
		// So wait until the browser is about to paint the next frame
		const raf1 = requestAnimationFrame(() => {
			// Wait one more frame so layout/DOM changes (markdown + table)
			// have actually been applied and painted
			const raf2 = requestAnimationFrame(() => {
				updateScrollState();
			});
			// No nested cleanup needed here; outer cleanup covers
			void raf2;
		});

		return () => cancelAnimationFrame(raf1);
	}, [isStreaming, updateScrollState, children]);

	// Attach listeners ONLY when not streaming
	React.useEffect(() => {
		const el = ref.current;
		if (!el || isStreaming) return;

		const onScroll = () => updateScrollState();
		const onResize = () => updateScrollState();

		el.addEventListener("scroll", onScroll, { passive: true });
		window.addEventListener("resize", onResize);

		return () => {
			el.removeEventListener("scroll", onScroll);
			window.removeEventListener("resize", onResize);
		};
	}, [isStreaming, updateScrollState]);

	// Memoized toolbar styles
	const toolbarSx = React.useMemo(
		() => ({
			display: "flex",
			justifyContent: "flex-end",
			gap: 0.5,
			mb: 0.5,
		}),
		[],
	);

	// Memoized button styles
	const buttonSx = React.useMemo(
		() => ({
			bgcolor: "background.paper",
			border: "1px solid",
			borderColor: "divider",
			boxShadow: 1,
			cursor: "pointer",
			"&.Mui-disabled": {
				cursor: "default",
				pointerEvents: "auto", // To intercept mouseDown and prevent text selection
			},
		}),
		[],
	);

	// Memoized scroll container styles
	const scrollContainerSx = React.useMemo(
		() => ({
			width: "100%",
			overflowX: "auto",
			WebkitOverflowScrolling: "touch",

			// Important: ensures last 1px border is visible at max scroll
			paddingRight: "1px",

			// Hide scrollbar but keep scrolling
			scrollbarWidth: "none",
			"&::-webkit-scrollbar": { display: "none" },
		}),
		[],
	);

	return (
		<Box sx={{ my: 1 }}>
			{!isStreaming && showToolbar && (
				<Box sx={toolbarSx}>
					<IconButton
						size="small"
						onClick={scrollLeft}
						disabled={!canScrollLeft}
						onMouseDown={!canScrollLeft ? preventInteraction : undefined}
						onPointerDown={!canScrollLeft ? preventInteraction : undefined}
						disableRipple
						tabIndex={-1}
						sx={buttonSx}
					>
						<ChevronLeftIcon fontSize="small" />
					</IconButton>

					<IconButton
						size="small"
						onClick={scrollRight}
						disabled={!canScrollRight}
						onMouseDown={!canScrollRight ? preventInteraction : undefined}
						onPointerDown={!canScrollRight ? preventInteraction : undefined}
						disableRipple
						tabIndex={-1}
						sx={buttonSx}
					>
						<ChevronRightIcon fontSize="small" />
					</IconButton>
				</Box>
			)}

			<Box ref={ref} className="md-table-wrapper" sx={scrollContainerSx}>
				{children}
			</Box>
		</Box>
	);
});

StreamingTableScroller.displayName = "StreamingTableScroller";

export default StreamingTableScroller;
