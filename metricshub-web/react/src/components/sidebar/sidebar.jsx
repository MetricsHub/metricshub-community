import * as React from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Box, Drawer, useMediaQuery } from "@mui/material";

// === Trees owned by the Sidebar ===
import MachineTree from "./MachineTree";

// LocalStorage key for persistent drawer width
const LS_KEY = "metricshub.drawerWidth";

// Drawer width bounds (px)
const DEFAULT_WIDTH = 320;
const MIN_WIDTH = 100;
const MAX_WIDTH = 1000;

// Overlay scrollbar geometry (px)
const THUMB_WIDTH = 8;
const TRACK_INSET = 0;
const MIN_THUMB = 28;

// Sash UX tuning
const SASH_HOVER_DELAY = 500;
const SASH_MOVE_THRESHOLD = 4;
const SASH_KEY_STEP = 10;

/**
 * Sidebar
 * Props:
 *  - open: boolean
 *  - onOpenChange: (open: boolean) => void
 *  - onWidthChange?: (width: number) => void  // reported on desktop
 *  - appBarHeights?: { xs: number, sm: number }
 *  - activeTree?: string                       // which tree to render
 */
export default function Sidebar({
    open,
    onOpenChange,
    onWidthChange,
    appBarHeights = { xs: 56, sm: 64 },
    activeTree = "machines",
}) {
    const isSmall = useMediaQuery("(max-width:900px)");

    // Registry of available trees (extend here as you add more)
    const trees = useMemo(
        () => ({
            machines: <MachineTree />,
            // alerts: <AlertsTree />,
            // inventory: <InventoryTree />,
        }),
        []
    );

    // Persistent drawer width
    const [drawerWidth, setDrawerWidth] = useState(() => {
        if (typeof window === "undefined") return DEFAULT_WIDTH;
        const saved = Number(localStorage.getItem(LS_KEY));
        return Number.isFinite(saved)
            ? Math.min(Math.max(saved, MIN_WIDTH), MAX_WIDTH)
            : DEFAULT_WIDTH;
    });

    useEffect(() => {
        if (!isSmall) onWidthChange?.(drawerWidth);
    }, [drawerWidth, isSmall, onWidthChange]);

    useEffect(() => {
        if (!isSmall) localStorage.setItem(LS_KEY, String(drawerWidth));
    }, [drawerWidth, isSmall]);

    const clampWidth = useCallback(
        (w) => Math.min(Math.max(w, MIN_WIDTH), MAX_WIDTH),
        []
    );

    // ===== Resizer (sash)
    const sashDraggingRef = useRef(false);
    const sashStartXRef = useRef(0);
    const sashStartWidthRef = useRef(0);
    const sashHoverTimerRef = useRef(null);
    const sashEnterXRef = useRef(null);
    const sashHoveringRef = useRef(false);

    const [sashGuideVisible, setSashGuideVisible] = useState(false);
    const [isSashDragging, setIsSashDragging] = useState(false);
    const [isSashHover, setIsSashHover] = useState(false);

    const onSashPointerDown = useCallback(
        (e) => {
            if (isSmall) return;
            e.preventDefault();
            setIsSashDragging(true);

            sashDraggingRef.current = true;
            e.currentTarget?.setPointerCapture?.(e.pointerId);

            sashStartXRef.current = e.clientX;
            sashStartWidthRef.current = drawerWidth;

            setSashGuideVisible(true);
            document.body.style.cursor = "ew-resize";

            const move = (ev) => {
                if (!sashDraggingRef.current) return;
                const dx = ev.clientX - sashStartXRef.current;
                setDrawerWidth(clampWidth(sashStartWidthRef.current + dx));
            };

            const up = () => {
                sashDraggingRef.current = false;
                setIsSashDragging(false);
                document.body.style.cursor = "";
                if (!sashHoveringRef.current) setSashGuideVisible(false);

                window.removeEventListener("pointermove", move);
                window.removeEventListener("pointerup", up);
                window.removeEventListener("pointercancel", up);
            };

            window.addEventListener("pointermove", move, { passive: true });
            window.addEventListener("pointerup", up, { passive: true });
            window.addEventListener("pointercancel", up, { passive: true });
        },
        [drawerWidth, isSmall, clampWidth]
    );

    const onSashPointerEnter = useCallback((e) => {
        sashHoveringRef.current = true;
        setIsSashHover(true);
        sashEnterXRef.current = e.clientX;

        if (sashHoverTimerRef.current) {
            clearTimeout(sashHoverTimerRef.current);
            sashHoverTimerRef.current = null;
        }
        sashHoverTimerRef.current = window.setTimeout(() => {
            if (!sashDraggingRef.current && sashHoveringRef.current) {
                setSashGuideVisible(true);
            }
        }, SASH_HOVER_DELAY);
    }, []);

    const onSashPointerMove = useCallback(
        (e) => {
            if (!sashDraggingRef.current && !sashGuideVisible && sashEnterXRef.current !== null) {
                const moved = Math.abs(e.clientX - sashEnterXRef.current);
                if (moved > SASH_MOVE_THRESHOLD) {
                    if (sashHoverTimerRef.current) {
                        clearTimeout(sashHoverTimerRef.current);
                        sashHoverTimerRef.current = null;
                    }
                    sashEnterXRef.current = e.clientX;
                    sashHoverTimerRef.current = window.setTimeout(() => {
                        if (!sashDraggingRef.current && sashHoveringRef.current) {
                            setSashGuideVisible(true);
                        }
                    }, SASH_HOVER_DELAY);
                }
            }
        },
        [sashGuideVisible]
    );

    const onSashPointerLeave = useCallback(() => {
        setIsSashHover(false);
        sashHoveringRef.current = false;
        sashEnterXRef.current = null;

        if (!sashDraggingRef.current) {
            if (sashHoverTimerRef.current) {
                clearTimeout(sashHoverTimerRef.current);
                sashHoverTimerRef.current = null;
            }
            setSashGuideVisible(false);
        }
    }, []);

    useEffect(() => {
        return () => {
            if (sashHoverTimerRef.current) clearTimeout(sashHoverTimerRef.current);
        };
    }, []);

    // ===== Custom overlay scrollbar
    const scrollRef = useRef(null);

    const [hasOverflow, setHasOverflow] = useState(false);
    const [pointerInScroll, setPointerInScroll] = useState(false);
    const [overTrack, setOverTrack] = useState(false);
    const [isDraggingThumb, setIsDraggingThumb] = useState(false);

    const [thumbTop, setThumbTop] = useState(0);
    const [thumbHeight, setThumbHeight] = useState(MIN_THUMB);

    const dragStateRef = useRef({ startY: 0, startThumbTop: 0 });

    const recalcThumb = useCallback(() => {
        const el = scrollRef.current;
        if (!el) return;

        const { scrollHeight, clientHeight, scrollTop } = el;

        const overflow = scrollHeight > clientHeight + 1;
        setHasOverflow(overflow);

        const trackHeight = Math.max(clientHeight - TRACK_INSET * 2, 0);
        const maxScroll = Math.max(scrollHeight - clientHeight, 1);
        const ratio = clientHeight / Math.max(scrollHeight, 1);

        const h = Math.max(Math.floor(ratio * trackHeight), MIN_THUMB);
        const maxThumbTop = Math.max(trackHeight - h, 0);
        const t = Math.floor((scrollTop / maxScroll) * maxThumbTop);

        setThumbHeight(h);
        setThumbTop(TRACK_INSET + t);
    }, []);

    useEffect(() => {
        const el = scrollRef.current;
        if (!el) return;

        recalcThumb();

        const onScroll = () => recalcThumb();
        el.addEventListener("scroll", onScroll, { passive: true });

        const ro = new ResizeObserver(() => recalcThumb());
        ro.observe(el);

        const observeChild = () => {
            const child = el.firstElementChild;
            if (child) ro.observe(child);
        };
        observeChild();

        const mo = new MutationObserver(() => {
            recalcThumb();
            observeChild();
        });
        mo.observe(el, { childList: true, subtree: true });

        return () => {
            el.removeEventListener("scroll", onScroll);
            ro.disconnect();
            mo.disconnect();
        };
    }, [recalcThumb]);

    const handleScrollAreaPointerEnter = useCallback(() => {
        setPointerInScroll(true);
    }, []);
    const handleScrollAreaPointerLeave = useCallback(() => {
        setPointerInScroll(false);
    }, []);

    const handleDrawerPointerLeave = useCallback(() => {
        setPointerInScroll(false);
        setOverTrack(false);
    }, []);

    const overlayTrackRef = useRef(null);
    const onOverlayTrackEnter = useCallback(() => {
        setOverTrack(true);
        setPointerInScroll(true);
    }, []);
    const onOverlayTrackLeave = useCallback((e) => {
        setOverTrack(false);

        const paperEl = scrollRef.current?.parentElement;
        if (!paperEl) {
            setPointerInScroll(false);
            return;
        }

        const next = e.relatedTarget;
        if (next instanceof Node) {
            if (!paperEl.contains(next)) {
                setPointerInScroll(false);
                return;
            }
        } else {
            const r = paperEl.getBoundingClientRect();
            const { clientX: x, clientY: y } = e;
            const outside = x < r.left || x > r.right || y < r.top || y > r.bottom;
            if (outside) setPointerInScroll(false);
        }
    }, []);

    const onOverlayTrackWheel = useCallback((e) => {
        const el = scrollRef.current;
        if (!el) return;
        el.scrollTop += e.deltaY;
    }, []);

    const startDragThumb = useCallback(
        (e) => {
            e.preventDefault();
            e.stopPropagation();
            setIsDraggingThumb(true);
            e.currentTarget?.setPointerCapture?.(e.pointerId);
            dragStateRef.current.startY = e.clientY;
            dragStateRef.current.startThumbTop = thumbTop;
        },
        [thumbTop]
    );

    const onDragThumbMove = useCallback(
        (e) => {
            if (!isDraggingThumb) return;
            const el = scrollRef.current;
            if (!el) return;

            const { clientHeight, scrollHeight } = el;
            const trackHeight = Math.max(clientHeight - TRACK_INSET * 2, 0);
            const maxThumbTop = Math.max(trackHeight - thumbHeight, 0);
            const maxScroll = Math.max(scrollHeight - clientHeight, 1);

            const dy = e.clientY - dragStateRef.current.startY;
            let newThumbTopInside = dragStateRef.current.startThumbTop - TRACK_INSET + dy;
            newThumbTopInside = Math.max(0, Math.min(maxThumbTop, newThumbTopInside));

            const scrollRatio = maxScroll / Math.max(maxThumbTop, 1);
            const newScrollTop = newThumbTopInside * scrollRatio;

            el.scrollTop = newScrollTop;
        },
        [isDraggingThumb, thumbHeight]
    );

    const endDragThumb = useCallback(() => {
        if (!isDraggingThumb) return;
        setIsDraggingThumb(false);
    }, [isDraggingThumb]);

    useEffect(() => {
        const move = (ev) => onDragThumbMove(ev);
        const up = () => endDragThumb();

        if (isDraggingThumb) {
            window.addEventListener("pointermove", move, { passive: true });
            window.addEventListener("pointerup", up, { passive: true });
            window.addEventListener("pointercancel", up, { passive: true });
        }
        return () => {
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", up);
            window.removeEventListener("pointercancel", up);
        };
    }, [isDraggingThumb, onDragThumbMove, endDragThumb]);

    const overlayVisible =
        hasOverflow &&
        !isSashDragging &&
        !isSashHover &&
        (pointerInScroll || overTrack || isDraggingThumb);

    const overlayOpacity = overlayVisible ? (overTrack || isDraggingThumb ? 0.9 : 0.55) : 0;

    const heights = useMemo(() => appBarHeights, [appBarHeights]);

    return (
        <Drawer
            variant={isSmall ? "temporary" : "persistent"}
            open={open}
            onClose={() => onOpenChange(false)}
            ModalProps={{ keepMounted: true }}
            sx={{ width: isSmall ? DEFAULT_WIDTH : drawerWidth, flexShrink: 0 }}
            slotProps={{
                paper: {
                    onPointerLeave: handleDrawerPointerLeave,
                    sx: (t) => {
                        const hSmUp = t.mixins.toolbar?.minHeight ?? heights.sm;
                        const hXs = heights.xs;
                        return {
                            width: isSmall ? DEFAULT_WIDTH : drawerWidth,
                            boxSizing: "border-box",
                            top: { xs: hXs, sm: hSmUp },
                            height: { xs: `calc(100% - ${hXs}px)`, sm: `calc(100% - ${hSmUp}px)` },
                            p: 0,
                            m: 0,
                            display: "flex",
                            flexDirection: "column",
                            borderRight: 1,
                            borderColor: "divider",
                            position: "fixed",
                            overflow: "visible",
                        };
                    },
                },
            }}
        >
            {/* Scroll container (native scrollbars hidden) */}
            <Box
                id="sidebar-scroll"
                ref={scrollRef}
                onPointerEnter={handleScrollAreaPointerEnter}
                onPointerLeave={handleScrollAreaPointerLeave}
                sx={{
                    position: "absolute",
                    inset: 0,
                    overflowY: "auto",
                    scrollbarWidth: "none",
                    "&": { msOverflowStyle: "none" },
                    "&::-webkit-scrollbar": { width: 0, height: 0 },
                    pr: 1,
                    WebkitOverflowScrolling: "touch",
                }}
            >
                <Box sx={{ p: 1 }}>
                    {trees?.[activeTree] ?? null}
                </Box>
            </Box>

            {/* Overlay scrollbar */}
            {!isSmall && (
                <Box
                    ref={overlayTrackRef}
                    aria-hidden
                    onPointerEnter={onOverlayTrackEnter}
                    onPointerLeave={onOverlayTrackLeave}
                    onWheel={onOverlayTrackWheel}
                    sx={{
                        position: "absolute",
                        top: 0,
                        right: 0,
                        height: "100%",
                        width: THUMB_WIDTH + TRACK_INSET,
                        zIndex: 4,
                        pointerEvents: overlayVisible ? "auto" : "none",
                        opacity: overlayOpacity,
                        transition: "opacity 120ms ease",
                        cursor: "default",
                    }}
                >
                    <Box
                        onPointerDown={startDragThumb}
                        sx={{
                            position: "absolute",
                            top: thumbTop,
                            right: 0,
                            width: THUMB_WIDTH,
                            height: `${thumbHeight}px`,
                            backgroundColor: "rgba(128,128,128,0.85)",
                            borderRadius: 0,
                            boxShadow: "0 0 0 1px rgba(0,0,0,0.05)",
                            pointerEvents: "auto",
                            cursor: "default",
                            userSelect: "none",
                            touchAction: "none",
                        }}
                    />
                </Box>
            )}

            {/* Resizer sash */}
            {!isSmall && (
                <Box
                    role="separator"
                    aria-label="Resize sidebar"
                    aria-orientation="vertical"
                    aria-valuemin={MIN_WIDTH}
                    aria-valuemax={MAX_WIDTH}
                    aria-valuenow={drawerWidth}
                    tabIndex={0}
                    onDoubleClick={() => setDrawerWidth(DEFAULT_WIDTH)}
                    onPointerDown={onSashPointerDown}
                    onPointerEnter={onSashPointerEnter}
                    onPointerMove={onSashPointerMove}
                    onPointerLeave={onSashPointerLeave}
                    onKeyDown={(e) => {
                        if (e.key === "ArrowLeft") {
                            setDrawerWidth((w) => Math.max(w - SASH_KEY_STEP, MIN_WIDTH));
                            e.preventDefault();
                        } else if (e.key === "ArrowRight") {
                            setDrawerWidth((w) => Math.min(w + SASH_KEY_STEP, MAX_WIDTH));
                            e.preventDefault();
                        } else if (e.key === "Home") {
                            setDrawerWidth(MIN_WIDTH);
                            e.preventDefault();
                        } else if (e.key === "End") {
                            setDrawerWidth(MAX_WIDTH);
                            e.preventDefault();
                        }
                    }}
                    sx={{
                        position: "absolute",
                        top: 0,
                        right: -7,
                        width: 8,
                        height: "100%",
                        zIndex: 5,
                        touchAction: "none",
                        backgroundColor: "transparent",
                        cursor: "ew-resize",
                        pointerEvents: "auto",
                        "&::after": {
                            content: '""',
                            position: "absolute",
                            top: 0,
                            right: 5.5,
                            width: "3px",
                            height: "100%",
                            backgroundColor: (t) => t.palette.primary.main,
                            opacity: sashGuideVisible ? 1 : 0,
                            transition: "opacity 160ms ease",
                            pointerEvents: "none",
                        },
                    }}
                />
            )}
        </Drawer>
    );
}
