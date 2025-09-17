import * as React from "react";
import { useRef, useState, useEffect, useCallback } from "react";
import { Box, useMediaQuery } from "@mui/material";

const LS_KEY = "metricshub.split-screen.leftPct"; // persist user preference
const MIN_PCT = 15; // min width for each pane (%)
const MAX_PCT = 85;
const SASH_WIDTH = 6;

export const Left = ({ children, ...rest }) => {
	return (
		<Box
			{...rest}
			sx={{
				minWidth: 0, // allow content to shrink properly
				overflow: "auto",
				height: "100%",
				...rest.sx,
			}}
		>
			{children}
		</Box>
	);
};

export const Right = ({ children, ...rest }) => {
	return (
		<Box
			{...rest}
			sx={{
				minWidth: 0,
				overflow: "auto",
				height: "100%",
				...rest.sx,
			}}
		>
			{children}
		</Box>
	);
};

export const SplitScreen = ({ children, initialLeftPct = 40, ...rest }) => {
	const isSmall = useMediaQuery("(max-width:900px)");

	// Extract <Left> and <Right> from children
	const leftChild = React.Children.toArray(children).find(
		(c) => React.isValidElement(c) && c.type === Left,
	);
	const rightChild = React.Children.toArray(children).find(
		(c) => React.isValidElement(c) && c.type === Right,
	);

	// persistent left pane width in %
	const [leftPct, setLeftPct] = useState(() => {
		const saved = Number(localStorage.getItem(LS_KEY));
		if (Number.isFinite(saved) && saved >= MIN_PCT && saved <= MAX_PCT) return saved;
		return Math.max(MIN_PCT, Math.min(MAX_PCT, initialLeftPct));
	});

	useEffect(() => {
		if (!isSmall) localStorage.setItem(LS_KEY, String(leftPct));
	}, [leftPct, isSmall]);

	// drag logic
	const wrapRef = useRef(null);
	const draggingRef = useRef(false);

	const onPointerDown = useCallback(
		(e) => {
			if (isSmall) return; // no drag on small screens
			draggingRef.current = true;
			e.currentTarget.setPointerCapture?.(e.pointerId);
			document.body.style.cursor = "col-resize";
		},
		[isSmall],
	);

	const onPointerMove = useCallback((e) => {
		if (!draggingRef.current || !wrapRef.current) return;
		const rect = wrapRef.current.getBoundingClientRect();
		const x = e.clientX - rect.left; // px from left edge
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
		// global listeners while dragging
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
		// stacked layout on small screens
		return (
			<>
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
			</>
		);
	}

	// side-by-side with draggable sash
	return (
		<>
			<Box
				ref={wrapRef}
				{...rest}
				sx={{
					position: "relative",
					display: "grid",
					gridTemplateColumns: `${leftPct}% ${SASH_WIDTH}px ${100 - leftPct}fr`,
					gridTemplateRows: "minmax(0, 1fr)",
					alignItems: "stretch",
					minHeight: 0,
					height: "calc(100vh - 76px)",
					border: 1,
					borderColor: "divider",
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
					tabIndex={0}
					onKeyDown={(e) => {
						// simple keyboard resize support
						if (e.key === "ArrowLeft") {
							setLeftPct((v) => Math.max(MIN_PCT, v - 2));
							e.preventDefault();
						} else if (e.key === "ArrowRight") {
							setLeftPct((v) => Math.min(MAX_PCT, v + 2));
							e.preventDefault();
						} else if (e.key === "Home") {
							setLeftPct(MIN_PCT);
							e.preventDefault();
						} else if (e.key === "End") {
							setLeftPct(MAX_PCT);
							e.preventDefault();
						}
					}}
					sx={{
						gridColumn: 2,
						gridRow: 1,
						cursor: "col-resize",
						position: "relative",
						"&::before": {
							content: '""',
							position: "absolute",
							top: 0,
							bottom: 0,
							left: 0,
							right: 0,
						},
						"&::after": {
							content: '""',
							position: "absolute",
							top: 0,
							bottom: 0,
							left: "calc(50% - 1px)",
							width: "2px",
							bgcolor: "primary.main",
							opacity: 0.5,
						},
					}}
				/>

				<Right sx={{ gridColumn: 3, gridRow: 1 }}>{rightChild?.props.children}</Right>
			</Box>
		</>
	);
};
