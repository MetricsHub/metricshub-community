import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, IconButton, Menu, MenuItem, TextField, Stack } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteIcon from "@mui/icons-material/Delete";
import FileTypeIcon from "./icons/FileTypeIcons";
import FileMeta from "./FileMeta";
import ClickAwayListener from "@mui/material/ClickAwayListener";

/**
 * File tree item component.
 * @param {{file:{name:string,size:number,lastModificationTime:string,localOnly?:boolean},onRename:(oldName:string,newName:string)=>void,onDelete:(name:string)=>void}} props The component props.
 * @returns {JSX.Element} The file tree item component.
 */
export default function FileTreeItem({
	file,
	onRename,
	onDelete,
	isDirty = false,
	validation = null,
}) {
	const [editing, setEditing] = React.useState(false);
	const [draft, setDraft] = React.useState(file.name);
	const inputRef = React.useRef(null);
	const rowRef = React.useRef(null);
	const containerRef = React.useRef(null);
	const cancelledRef = React.useRef(false);
	const [menuAnchor, setMenuAnchor] = React.useState(null);

	/**
	 * Reset draft name when file changes (but not when editing).
	 */
	React.useEffect(() => {
		if (!editing) setDraft(file.name);
	}, [file.name, editing]);

	/**
	 * Open the action menu.
	 * @param {*} e The click event.
	 */
	const openMenu = (e) => {
		e.stopPropagation();
		setMenuAnchor(e.currentTarget);
	};

	/**
	 * Close the action menu.
	 * @returns {void}
	 */
	const closeMenu = () => setMenuAnchor(null);

	/**
	 * Start renaming the file.
	 * @returns {void}
	 */
	const startRename = () => {
		closeMenu();
		rowRef.current?.blur?.();
		setEditing(true);
		requestAnimationFrame(() => inputRef.current?.select());
	};

	/**
	 * Cancel renaming the file.
	 * @returns {void}
	 */
	const cancelRename = () => {
		cancelledRef.current = true;
		setDraft(file.name);
		setEditing(false);
		rowRef.current?.blur?.();
	};

	/**
	 * Submit the rename action.
	 */
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

	const label = (
		<Stack
			ref={containerRef}
			direction="row"
			alignItems="center"
			justifyContent="space-between"
			sx={{ width: "100%", pr: 1, userSelect: editing ? "text" : "none" }}
		>
			<Box sx={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
				{editing ? (
          <ClickAwayListener
            onClickAway={submitRename}
            mouseEvent="onClick"
            touchEvent={false}
            disableReactTree
          >
            <Box
              // keep all mouse activity inside the editor from reaching the TreeItem
              onMouseDown={(e) => e.stopPropagation()}
              onClick={(e) => e.stopPropagation()}
            >
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
									slotProps={{
										htmlInput: { "aria-label": "Rename file" },
									}}
									sx={{ "& .MuiInputBase-input": { fontWeight: 500 } }}
								/>
							</Box>
							<FileMeta file={file} sx={{ mt: 0.5 }} />
						</Box>
					</ClickAwayListener>
				) : (
					<>
						<Box sx={{ display: "flex", alignItems: "center", minWidth: 0 }}>
							<FileTypeIcon type="yaml" />
							<Box
								sx={{
									fontWeight: isDirty ? 510 : 500,
									whiteSpace: "nowrap",
									overflow: "hidden",
									textOverflow: "ellipsis",
								}}
								title={file.name}
							>
								{file.name}
							</Box>

							{/* Unsaved/dirty indicator or error indicator */}
							{isDirty && (
								<Box
									component="span"
									title={
										validation && validation.valid === false
											? "Validation errors"
											: "Unsaved changes"
									}
									aria-label="Unsaved changes"
									sx={{
										ml: 0.75,
										width: 8,
										height: 8,
										borderRadius: "50%",
										bgcolor: (t) =>
											validation && validation.valid === false
												? t.palette.error.main
												: t.palette.warning.main,
										flexShrink: 0,
									}}
								/>
							)}
						</Box>
						<FileMeta file={file} />
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
						onDelete(file.name);
					}}
				>
					<DeleteIcon fontSize="small" style={{ marginRight: 8 }} />
					Delete
				</MenuItem>
			</Menu>
		</>
	);
}
