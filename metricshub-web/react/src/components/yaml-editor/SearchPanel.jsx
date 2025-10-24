import React from "react";
import { Box, IconButton, InputBase, Typography } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useTheme } from "@mui/material/styles";
import { EditorView } from "@codemirror/view";

/**
 * VS Code-style search panel for CodeMirror editor.
 * Fixed position overlay at top-right of the editor.
 */
export default function SearchPanel({ onClose, viewRef }) {
	const theme = useTheme();
	const [searchQuery, setSearchQuery] = React.useState("");
	const [replaceQuery, setReplaceQuery] = React.useState("");
	const [currentMatch, setCurrentMatch] = React.useState(0);
	const [totalMatches, setTotalMatches] = React.useState(0);
	const [showReplace, setShowReplace] = React.useState(false);
	const inputRef = React.useRef(null);
	const matchesRef = React.useRef([]);

	// Focus search input when panel opens
	React.useEffect(() => {
		setTimeout(() => inputRef.current?.focus(), 50);
	}, []);

	// Search logic - find all matches and update count
	const updateMatches = React.useCallback(
		(query) => {
			const editorView = viewRef.current;
			if (!editorView || !query) {
				setTotalMatches(0);
				setCurrentMatch(0);
				matchesRef.current = [];
				return;
			}

			const text = editorView.state.doc.toString();
			const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
			const regex = new RegExp(escaped, "gi");
			const matches = [...text.matchAll(regex)];
			
			console.log("Search query:", query);
			console.log("Document length:", text.length);
			console.log("Matches found:", matches.length);
			
			matchesRef.current = matches;
			setTotalMatches(matches.length);

			if (matches.length === 0) {
				setCurrentMatch(0);
			} else {
				// Automatically go to first match with proper viewport centering
				const match = matches[0];
				setCurrentMatch(1);
				const from = match.index;
				const to = match.index + match[0].length;
				
				editorView.dispatch({
					selection: { anchor: from, head: to },
					effects: [
						// Center the selection in the viewport
						EditorView.scrollIntoView(
							{ from, to },
							{ y: "center", yMargin: 50 }
						)
					],
				});
			}
		},
		[viewRef],
	);

	// Navigate to a specific match
	const goToMatch = React.useCallback(
		(direction = "next") => {
			const editorView = viewRef.current;
			if (!editorView || matchesRef.current.length === 0) return;

			const matches = matchesRef.current;
			const currentPos = editorView.state.selection.main.head;
			let targetIndex = 0;

			if (direction === "next") {
				// Find next match after current position
				targetIndex = matches.findIndex((m) => m.index > currentPos);
				if (targetIndex === -1) targetIndex = 0; // Wrap to start
			} else if (direction === "prev") {
				// Find previous match before current position
				let found = -1;
				for (let i = matches.length - 1; i >= 0; i--) {
					if (matches[i].index < currentPos) {
						found = i;
						break;
					}
				}
				targetIndex = found === -1 ? matches.length - 1 : found; // Wrap to end
			}

			const match = matches[targetIndex];
			setCurrentMatch(targetIndex + 1);

			// Scroll to and select the match with proper viewport centering
			const from = match.index;
			const to = match.index + match[0].length;
			
			editorView.dispatch({
				selection: { anchor: from, head: to },
				effects: [
					// Center the selection in the viewport
					EditorView.scrollIntoView(
						{ from, to },
						{ y: "center", yMargin: 50 }
					)
				],
			});
			editorView.focus();
		},
		[viewRef],
	);

	// Update matches when query changes and navigate to first match
	React.useEffect(() => {
		updateMatches(searchQuery);
	}, [searchQuery, updateMatches]);

	const handleNext = () => goToMatch("next");
	const handlePrev = () => goToMatch("prev");

	const handleKeyDown = (e) => {
		if (e.key === "Enter") {
			e.preventDefault();
			if (e.shiftKey) {
				handlePrev();
			} else {
				handleNext();
			}
		} else if (e.key === "Escape") {
			onClose();
		}
	};

	return (
		<Box
			sx={{
				position: "absolute",
				top: 4,
				right: 4,
				zIndex: 1000,
				bgcolor: theme.palette.mode === "dark" ? "#252526" : "#f3f3f3",
				border: 1,
				borderColor: theme.palette.mode === "dark" ? "#3c3c3c" : "#d4d4d4",
				boxShadow: "0 2px 8px rgba(0,0,0,0.15)",
				minWidth: showReplace ? 320 : 280,
			}}
		>
			{/* Search input row */}
			<Box sx={{ display: "flex", alignItems: "center", gap: 0.25, px: 0.5, py: 0.25 }}>
				<IconButton
					size="small"
					onClick={() => setShowReplace(!showReplace)}
					sx={{ width: 20, height: 20, p: 0.25 }}
				>
					<ExpandMoreIcon
						fontSize="small"
						sx={{
							fontSize: 16,
							transform: showReplace ? "rotate(0deg)" : "rotate(-90deg)",
							transition: "transform 0.2s",
						}}
					/>
				</IconButton>

				<InputBase
					ref={inputRef}
					placeholder="Find"
					value={searchQuery}
					onChange={(e) => setSearchQuery(e.target.value)}
					onKeyDown={handleKeyDown}
					sx={{
						flex: 1,
						fontSize: "13px",
						px: 0.75,
						py: 0.25,
						bgcolor: theme.palette.mode === "dark" ? "#3c3c3c" : "#ffffff",
						border: 1,
						borderColor: theme.palette.mode === "dark" ? "#3c3c3c" : "#cecece",
						"&:focus-within": {
							borderColor: theme.palette.mode === "dark" ? "#007acc" : "#007acc",
						},
					}}
				/>

				{searchQuery && (
					<Typography
						variant="caption"
						sx={{
							fontSize: "11px",
							color: totalMatches === 0 ? "error.main" : "text.secondary",
							minWidth: totalMatches > 0 ? "auto" : 0,
							px: 0.5,
						}}
					>
						{totalMatches === 0 ? "No results" : `${currentMatch}/${totalMatches}`}
					</Typography>
				)}

				<IconButton
					size="small"
					onClick={handlePrev}
					disabled={!searchQuery || totalMatches === 0}
					sx={{ width: 20, height: 20, p: 0.25 }}
				>
					<KeyboardArrowUpIcon sx={{ fontSize: 16 }} />
				</IconButton>

				<IconButton
					size="small"
					onClick={handleNext}
					disabled={!searchQuery || totalMatches === 0}
					sx={{ width: 20, height: 20, p: 0.25 }}
				>
					<KeyboardArrowDownIcon sx={{ fontSize: 16 }} />
				</IconButton>

				<IconButton size="small" onClick={onClose} sx={{ width: 20, height: 20, p: 0.25 }}>
					<CloseIcon sx={{ fontSize: 16 }} />
				</IconButton>
			</Box>

			{/* Replace input row (optional) */}
			{showReplace && (
				<Box sx={{ display: "flex", alignItems: "center", gap: 0.25, px: 0.5, pb: 0.25, pl: 3.25 }}>
					<InputBase
						placeholder="Replace"
						value={replaceQuery}
						onChange={(e) => setReplaceQuery(e.target.value)}
						sx={{
							flex: 1,
							fontSize: "13px",
							px: 0.75,
							py: 0.25,
							bgcolor: theme.palette.mode === "dark" ? "#3c3c3c" : "#ffffff",
							border: 1,
							borderColor: theme.palette.mode === "dark" ? "#3c3c3c" : "#cecece",
							"&:focus-within": {
								borderColor: theme.palette.mode === "dark" ? "#007acc" : "#007acc",
							},
						}}
					/>
				</Box>
			)}
		</Box>
	);
}
