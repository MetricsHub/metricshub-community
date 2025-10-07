import { useState, useCallback, useRef, useEffect } from "react";
import {
	IconButton,
	ListItem,
	ListItemButton,
	ListItemText,
	Menu,
	MenuItem,
	TextField,
	Box,
} from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteIcon from "@mui/icons-material/Delete";
import { formatBytes, formatRelativeTime } from "../../utils/formatters";

export default function ConfigListItem({ file, selected, onSelect, onDeleteRequest, onRename }) {
	const [anchorEl, setAnchorEl] = useState(null);
	const [editing, setEditing] = useState(false);
	const [draft, setDraft] = useState(file.name);
	const inputRef = useRef(null);
	const editContainerRef = useRef(null);
	const rowBtnRef = useRef(null);
	const cancelledRef = useRef(false);

	useEffect(() => {
		if (!editing) setDraft(file.name);
	}, [file.name, editing]);

	const openMenu = useCallback((e) => {
		e.stopPropagation();
		setAnchorEl(e.currentTarget);
	}, []);
	const closeMenu = useCallback(() => setAnchorEl(null), []);

	const startRename = useCallback(() => {
		closeMenu();
		rowBtnRef.current?.blur();
		setEditing(true);
		requestAnimationFrame(() => inputRef.current?.select());
	}, [closeMenu]);

	const cancelRename = useCallback(() => {
		cancelledRef.current = true;
		setDraft(file.name);
		setEditing(false);
		rowBtnRef.current?.blur();
	}, [file.name]);

	const submitRename = useCallback(() => {
		const blurRow = () => rowBtnRef.current?.blur();
		if (cancelledRef.current) {
			cancelledRef.current = false;
			setEditing(false);
			blurRow();
			return;
		}
		const next = draft.trim();
		if (!next || next === file.name) {
			setEditing(false);
			blurRow();
			return;
		}
		onRename?.(file.name, next);
		setEditing(false);
		blurRow();
	}, [draft, file.name, onRename]);

	useEffect(() => {
		if (!editing) return;
		const onClickAway = (e) => {
			const container = editContainerRef.current;
			if (container && !container.contains(e.target)) {
				submitRename();
			}
		};
		document.addEventListener("mousedown", onClickAway, true);
		return () => document.removeEventListener("mousedown", onClickAway, true);
	}, [editing, submitRename]);

	return (
		<ListItem
			disableGutters
			disablePadding
			secondaryAction={
				!editing && (
					<IconButton edge="end" aria-label="more actions" onClick={openMenu}>
						<MoreVertIcon />
					</IconButton>
				)
			}
		>
			<ListItemButton
				ref={rowBtnRef}
				selected={selected}
				onClick={() => !editing && onSelect(file.name)}
				disableRipple={editing}
				disableTouchRipple={editing}
				sx={{
					"&.Mui-focusVisible": {
						backgroundColor: "transparent",
					},
				}}
			>
				{editing ? (
					<Box ref={editContainerRef} sx={{ width: "100%" }}>
						<TextField
							inputRef={inputRef}
							value={draft}
							onChange={(e) => setDraft(e.target.value)}
							variant="standard"
							size="small"
							fullWidth
							autoFocus
							onKeyDown={(e) => {
								if (e.key === "Enter") {
									e.preventDefault();
									submitRename();
								}
								if (e.key === "Escape") {
									e.preventDefault();
									cancelRename();
								}
							}}
							inputProps={{ "aria-label": "Rename file" }}
						/>
						<Box sx={{ mt: 0.5, opacity: 0.65, fontSize: "0.8rem" }}>
							{`${formatBytes(file.size)} • ${formatRelativeTime(file.lastModificationTime)}`}
						</Box>
					</Box>
				) : (
					<ListItemText
						primary={file.name}
						secondary={`${formatBytes(file.size)} • ${formatRelativeTime(file.lastModificationTime)}`}
						slotProps={{
							primary: { sx: { fontWeight: 500 } },
							secondary: { sx: { opacity: 0.65, fontSize: "0.8rem" } },
						}}
					/>
				)}
			</ListItemButton>

			<Menu
				anchorEl={anchorEl}
				open={Boolean(anchorEl)}
				onClose={closeMenu}
				disableRestoreFocus
				disableAutoFocusItem
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem onMouseDown={(e) => e.preventDefault()} onClick={startRename}>
					<DriveFileRenameOutlineIcon fontSize="small" style={{ marginRight: 8 }} />
					Rename
				</MenuItem>
				<MenuItem
					onClick={() => {
						closeMenu();
						onDeleteRequest(file.name);
					}}
				>
					<DeleteIcon fontSize="small" style={{ marginRight: 8 }} />
					Delete
				</MenuItem>
			</Menu>
		</ListItem>
	);
}
