import * as React from "react";
import { useRef, useState, useEffect, useCallback } from "react";
import { Box } from "@mui/material";
import { alpha } from "@mui/material/styles";

const LS_KEY = "metricshub.otel-editor-log-split.topPct";
const MIN_PCT = 20;
const MAX_PCT = 90;
const SASH_HEIGHT = 6;

const Scrollbar = (t) => {
	const base = alpha(t.palette.text.primary, 0.35);
	const hover = alpha(t.palette.text.primary, 0.55);
	return {
		overflow: "auto",
		width: "100%",
		minHeight: 0,
		scrollbarWidth: "thin",
		scrollbarColor: `${base} transparent`,
		"&::-webkit-scrollbar": { width: 10, height: 10 },
		"&::-webkit-scrollbar-thumb": {
			backgroundColor: base,
			borderRadius: 8,
			border: "2px solid transparent",
			backgroundClip: "content-box",
		},
		"&::-webkit-scrollbar-thumb:hover": { backgroundColor: hover },
		"&::-webkit-scrollbar-track": { background: "transparent" },
		scrollbarGutter: "stable",
		overscrollBehavior: "contain",
	};
};

export const Top = ({ children, ...rest }) => (
	<Box {...rest} sx={(t) => ({ ...Scrollbar(t), ...rest.sx })}>
		{children}
	</Box>
);

export const Bottom = ({ children, disableScroll = false, ...rest }) => (
	<Box
		{...rest}
		sx={(t) => ({
			...(disableScroll ? { overflow: "hidden", width: "100%", minHeight: 0 } : Scrollbar(t)),
			...rest.sx,
		})}
	>
		{children}
	</Box>
);

/**
 * Vertical resizable split: Top and Bottom panes with a horizontal sash.
 * @param {Object} props
 * @param {number} [props.initialTopPct=60] - Initial percentage of height for top pane (e.g. 60 = 60% top, 40% bottom)
 * @param {boolean} [props.bottomVisible=true] - When false, the bottom panel is collapsed and the top takes full height.
 * @param {React.ReactNode} props.children - Must contain Top and Bottom components
 */
export function ResizableVerticalSplit({
	children,
	initialTopPct = 60,
	bottomVisible = true,
	...rest
}) {
	const topChild = React.Children.toArray(children).find(
		(c) => React.isValidElement(c) && c.type === Top,
	);
	const bottomChild = React.Children.toArray(children).find(
		(c) => React.isValidElement(c) && c.type === Bottom,
	);

	const [topPct, setTopPct] = useState(() => {
		const saved = Number(localStorage.getItem(LS_KEY));
		if (Number.isFinite(saved) && saved >= MIN_PCT && saved <= MAX_PCT) return saved;
		return Math.max(MIN_PCT, Math.min(MAX_PCT, initialTopPct));
	});

	useEffect(() => {
		localStorage.setItem(LS_KEY, String(topPct));
	}, [topPct]);

	const wrapRef = useRef(null);
	const draggingRef = useRef(false);

	const onPointerDown = useCallback((e) => {
		draggingRef.current = true;
		e.currentTarget.setPointerCapture?.(e.pointerId);
		document.body.style.cursor = "row-resize";
	}, []);

	const onPointerMove = useCallback((e) => {
		if (!draggingRef.current || !wrapRef.current) return;
		const rect = wrapRef.current.getBoundingClientRect();
		const y = e.clientY - rect.top;
		const pct = (y / rect.height) * 100;
		const clamped = Math.max(MIN_PCT, Math.min(MAX_PCT, pct));
		setTopPct(clamped);
	}, []);

	const endDrag = useCallback(() => {
		if (!draggingRef.current) return;
		draggingRef.current = false;
		document.body.style.cursor = "";
	}, []);

	useEffect(() => {
		const move = (ev) => onPointerMove(ev);
		const up = () => endDrag();
		window.addEventListener("pointermove", move, { passive: true });
		window.addEventListener("pointerup", up, { passive: true });
		window.addEventListener("pointercancel", up, { passive: true });
		return () => {
			window.removeEventListener("pointermove", move);
			window.removeEventListener("pointerup", up);
			window.removeEventListener("pointercancel", up);
		};
	}, [onPointerMove, endDrag]);

	const gridRows = bottomVisible ? `${topPct}fr ${SASH_HEIGHT}px ${100 - topPct}fr` : "1fr 0px 0fr";

	return (
		<Box
			ref={wrapRef}
			{...rest}
			sx={{
				position: "relative",
				display: "grid",
				gridTemplateRows: gridRows,
				gridTemplateColumns: "minmax(0, 1fr)",
				alignItems: "stretch",
				minHeight: 0,
				flex: 1,
				overflow: "hidden",
				...rest.sx,
			}}
		>
			<Top sx={{ gridRow: 1, gridColumn: 1 }}>{topChild?.props.children}</Top>
			<Box
				onPointerDown={onPointerDown}
				role="separator"
				aria-orientation="horizontal"
				aria-label="Resize editor and logs"
				sx={{
					gridRow: 2,
					gridColumn: 1,
					cursor: "row-resize",
					position: "relative",
					"&::after": {
						content: '""',
						position: "absolute",
						left: 0,
						right: 0,
						top: "calc(50% - 1px)",
						height: "2px",
						bgcolor: "grey.500",
						opacity: 0.5,
					},
				}}
			/>
			<Bottom sx={{ gridRow: 3, gridColumn: 1 }}>{bottomChild?.props.children}</Bottom>
		</Box>
	);
}
