import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, IconButton, Menu, MenuItem, TextField, Stack } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteIcon from "@mui/icons-material/Delete";
import { formatBytes, formatRelativeTime } from "../../../utils/formatters";
import FileTypeIcon from "./icons/FileTypeIcons";

export default function FileTreeItem({ file, onRename, onDeleteRequest }) {
	const [editing, setEditing] = React.useState(false);
	const [draft, setDraft] = React.useState(file.name);
	const inputRef = React.useRef(null);
	const rowRef = React.useRef(null);
	const containerRef = React.useRef(null);
	const cancelledRef = React.useRef(false);
	const [menuAnchor, setMenuAnchor] = React.useState(null);

	React.useEffect(() => {
		if (!editing) setDraft(file.name);
	}, [file.name, editing]);

	const openMenu = (e) => {
		e.stopPropagation();
		setMenuAnchor(e.currentTarget);
	};
	const closeMenu = () => setMenuAnchor(null);

	const startRename = () => {
		closeMenu();
		rowRef.current?.blur?.();
		setEditing(true);
		requestAnimationFrame(() => inputRef.current?.select());
	};

	const cancelRename = () => {
		cancelledRef.current = true;
		setDraft(file.name);
		setEditing(false);
		rowRef.current?.blur?.();
	};

	const submitRename = React.useCallback(() => {
		if (cancelledRef.current) {
			cancelledRef.current = false;
			setEditing(false);
			return;
		}
		const next = draft.trim();
		if (!next || next === file.name) {
			setEditing(false);
			return;
		}
		onRename?.(file.name, next);
		setEditing(false);
	}, [draft, file.name, onRename]);

	React.useEffect(() => {
		if (!editing) return;
		const onDown = (e) => {
			const c = containerRef.current;
			if (c && !c.contains(e.target)) submitRename();
		};
		document.addEventListener("mousedown", onDown, true);
		return () => document.removeEventListener("mousedown", onDown, true);
	}, [editing, submitRename]);

	const label = (
		<Stack
			ref={containerRef}
			direction="row"
			alignItems="center"
			justifyContent="space-between"
			sx={{ width: "100%", pr: 1 }}
			onMouseDown={() => {
				if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
			}}
		>
			<Box sx={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
				{editing ? (
					<>
						<Box sx={{ display: "flex", alignItems: "center" }}>
							<FileTypeIcon type="yaml" />
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
										e.stopPropagation();
										submitRename();
										return;
									}
									if (e.key === "Escape") {
										e.preventDefault();
										e.stopPropagation();
										cancelRename();
										return;
									}
									e.stopPropagation();
								}}
								onKeyUp={(e) => {
									e.stopPropagation();
								}}
								inputProps={{ "aria-label": "Rename file" }}
								sx={{ "& .MuiInputBase-input": { fontWeight: 500 } }}
							/>
						</Box>
						<Box sx={{ mt: 0.5, opacity: 0.65, fontSize: "0.8rem", fontWeight: 400 }}>
							{`${formatBytes(file.size)} • ${formatRelativeTime(file.lastModificationTime)}`}
						</Box>
					</>
				) : (
					<>
						<Box sx={{ display: "flex", alignItems: "center", minWidth: 0 }}>
							<FileTypeIcon type="yaml" />
							<Box
								sx={{
									fontWeight: 500,
									whiteSpace: "nowrap",
									overflow: "hidden",
									textOverflow: "ellipsis",
								}}
								title={file.name}
							>
								{file.name}
							</Box>
						</Box>
						<Box sx={{ opacity: 0.65, fontSize: "0.8rem", fontWeight: 400 }}>
							{`${formatBytes(file.size)} • ${formatRelativeTime(file.lastModificationTime)}`}
						</Box>
					</>
				)}
			</Box>

			{!editing && (
				<IconButton
					size="small"
					aria-label="More actions"
					onClick={openMenu}
					onMouseDown={(e) => e.preventDefault()} // prevent focus/expand
				>
					<MoreVertIcon fontSize="small" />
				</IconButton>
			)}
		</Stack>
	);

	return (
		<>
			<TreeItem
				itemId={file.name}
				label={label}
				slotProps={{
					content: {
						ref: rowRef,
						sx: { "&.Mui-focusVisible": { backgroundColor: "transparent" } },
					},
				}}
			/>
			<Menu
				anchorEl={menuAnchor}
				open={Boolean(menuAnchor)}
				onClose={closeMenu}
				disableRestoreFocus
				disableAutoFocusItem
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem onClick={startRename}>
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
		</>
	);
}
