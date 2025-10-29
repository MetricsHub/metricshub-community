import * as React from "react";
import { useRef, useState, useEffect, useCallback } from "react";
import { Box, useMediaQuery } from "@mui/material";
import { alpha } from "@mui/material/styles";

const LS_KEY = "metricshub.split-screen.leftPct"; // persist user preference
const MIN_PCT = 15; // min width for each pane (%)
const MAX_PCT = 85;
const SASH_WIDTH = 6;

const Scrollbar = (t) => {
	const base = alpha(t.palette.text.primary, 0.35);
	const hover = alpha(t.palette.text.primary, 0.55);
	const active = alpha(t.palette.text.primary, 0.75);

	return {
		overflow: "auto",
		height: "100%",
		minWidth: 0,

		// Firefox
		scrollbarWidth: "thin",
		scrollbarColor: `${base} transparent`,

		// WebKit
		"&::-webkit-scrollbar": {
			width: 10,
			height: 10,
		},
		"&::-webkit-scrollbar-thumb": {
			backgroundColor: base,
			borderRadius: 8,
			border: "2px solid transparent", // creates the slim “pill” look
			backgroundClip: "content-box",
		},
		"&::-webkit-scrollbar-thumb:hover": {
			backgroundColor: hover,
		},
		"&::-webkit-scrollbar-thumb:active": {
			backgroundColor: active,
		},
		"&::-webkit-scrollbar-track": {
			background: "transparent",
		},
		"&::-webkit-scrollbar-corner": {
			background: "transparent",
		},

		// Slightly stronger on container hover (Firefox)
		"&:hover": {
			scrollbarColor: `${hover} transparent`,
		},

		// Optional niceties
		scrollbarGutter: "stable", // avoid layout shift where supported
		overscrollBehavior: "contain",
	};
};

/**
 * Left pane of the split screen
 *
 * @param {*} param0  children - content to render
 * @returns JSX.Element
 */
export const Left = ({ children, ...rest }) => (
	<Box
		{...rest}
		sx={(t) => ({
			...Scrollbar(t),
			...rest.sx,
		})}
	>
		{children}
	</Box>
);

/**
 * Right pane of the split screen
 *
 * @param {*} param0 children - content to render
 * @returns JSX.Element
 */
export const Right = ({ children, disableScroll = false, ...rest }) => (
	<Box
		{...rest}
		sx={(t) => ({
			...(disableScroll ? { overflow: "hidden", height: "100%", minWidth: 0 } : Scrollbar(t)),
			...rest.sx,
		})}
	>
		{children}
	</Box>
);

export const SplitScreen = ({ children, initialLeftPct = 40, ...rest }) => {
	const isSmall = useMediaQuery("(max-width:900px)");

	const leftChild = React.Children.toArray(children).find(
		(c) => React.isValidElement(c) && c.type === Left,
	);
	const rightChild = React.Children.toArray(children).find(
		(c) => React.isValidElement(c) && c.type === Right,
	);

	const [leftPct, setLeftPct] = useState(() => {
		const saved = Number(localStorage.getItem(LS_KEY));
		if (Number.isFinite(saved) && saved >= MIN_PCT && saved <= MAX_PCT) return saved;
		return Math.max(MIN_PCT, Math.min(MAX_PCT, initialLeftPct));
	});

	useEffect(() => {
		if (!isSmall) localStorage.setItem(LS_KEY, String(leftPct));
	}, [leftPct, isSmall]);

	const wrapRef = useRef(null);
	const draggingRef = useRef(false);

	const onPointerDown = useCallback(
		(e) => {
			if (isSmall) return;
			draggingRef.current = true;
			e.currentTarget.setPointerCapture?.(e.pointerId);
			document.body.style.cursor = "col-resize";
		},
		[isSmall],
	);

	const onPointerMove = useCallback((e) => {
		if (!draggingRef.current || !wrapRef.current) return;
		const rect = wrapRef.current.getBoundingClientRect();
		const x = e.clientX - rect.left;
		const pct = (x / rect.width) * 100;
		const clamped = Math.max(MIN_PCT, Math.min(MAX_PCT, pct));
		setLeftPct(clamped);
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

	if (isSmall) {
		return (
			<Box
				{...rest}
				sx={{
					display: "flex",
					flexDirection: "column",
					gap: 2,
					minHeight: 0,
					...rest.sx,
				}}
			>
				<Box sx={{ minHeight: 0 }}>{rightChild}</Box>
			</Box>
		);
	}

	return (
		<Box
			ref={wrapRef}
			{...rest}
			sx={{
				position: "relative",
				display: "grid",
				// Use fr across to avoid overflow
				gridTemplateColumns: `${leftPct}fr ${SASH_WIDTH}px ${100 - leftPct}fr`,
				gridTemplateRows: "minmax(0, 1fr)",
				alignItems: "stretch",
				minHeight: 0,
				height: "calc(100vh - 76px)",
				border: 1,
				borderColor: "divider",
				overflow: "hidden",
				...rest.sx,
			}}
		>
			<Left sx={{ gridColumn: 1, gridRow: 1 }}>{leftChild?.props.children}</Left>

			{/* Sash */}
			<Box
				onPointerDown={onPointerDown}
				role="separator"
				aria-orientation="vertical"
				aria-label="Resize panels"
				sx={{
					gridColumn: 2,
					gridRow: 1,
					cursor: "ew-resize",
					position: "relative",
					"&::after": {
						content: '""',
						position: "absolute",
						top: 0,
						bottom: 0,
						left: "calc(50% - 1px)",
						width: "2px",
						bgcolor: "grey.500",
						opacity: 0.5,
					},
				}}
			/>

			<Right sx={{ gridColumn: 3, gridRow: 1 }}>{rightChild?.props.children}</Right>
		</Box>
	);
};
