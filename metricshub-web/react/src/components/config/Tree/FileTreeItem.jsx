import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, IconButton, Menu, MenuItem, TextField, Stack } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteIcon from "@mui/icons-material/Delete";
import { formatBytes, formatRelativeTime } from "../../../utils/formatters";

export default function FileTreeItem({ file, onSelect, onRename, onDeleteRequest }) {
  const [editing, setEditing] = React.useState(false);
  const [draft, setDraft] = React.useState(file.name);
  const inputRef = React.useRef(null);
  const rowRef = React.useRef(null);
  const containerRef = React.useRef(null);
  const cancelledRef = React.useRef(false);
  const [menuAnchor, setMenuAnchor] = React.useState(null);

  React.useEffect(() => { if (!editing) setDraft(file.name); }, [file.name, editing]);

  const openMenu = (e) => { e.stopPropagation(); setMenuAnchor(e.currentTarget); };
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


  // click-away commits
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
      onClick={(e) => {
        if (!editing) {
          e.stopPropagation();
          onSelect(file.name);
        }
      }}
    >
<Box sx={{ flex: 1, minWidth: 0 }}>
  {editing ? (
    <>
      <TextField
        inputRef={inputRef}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        variant="standard"
        size="small"
        fullWidth
        autoFocus
        onKeyDown={(e) => {
          if (e.key === "Enter") { e.preventDefault(); submitRename(); }
          if (e.key === "Escape") { e.preventDefault(); cancelRename(); }
        }}
        inputProps={{ "aria-label": "Rename file" }}
        sx={{
          "& .MuiInputBase-input": { fontWeight: 400 },
        }}
      />
      <Box
        sx={{
          mt: 0.5,
          opacity: 0.65,
          fontSize: "0.8rem",
          fontWeight: 400,
        }}
      >
        {`${formatBytes(file.size)} • ${formatRelativeTime(file.lastModificationTime)}`}
      </Box>
    </>
  ) : (
    <>
      <Box
        sx={{
          fontWeight: 400,
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
        title={file.name}
      >
        {file.name}
      </Box>
      <Box
        sx={{
          opacity: 0.65,
          fontSize: "0.8rem",
          fontWeight: 400,
        }}
      >
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
          onMouseDown={(e) => e.preventDefault()} // prevent content focus/expand
        >
          <MoreVertIcon fontSize="small" />
        </IconButton>
      )}
    </Stack>
  );

  // IMPORTANT: TreeItem has NO children; it's a leaf.
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
        <MenuItem onClick={() => { closeMenu(); onDeleteRequest(file.name); }}>
          <DeleteIcon fontSize="small" style={{ marginRight: 8 }} />
          Delete
        </MenuItem>
      </Menu>
    </>
  );
}
