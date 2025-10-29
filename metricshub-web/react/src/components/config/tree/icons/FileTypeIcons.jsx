// src/components/config/tree/icons/FileTypeIcons.jsx
import * as React from "react";
import { Box } from "@mui/material";
import FolderOutlinedIcon from "@mui/icons-material/FolderOutlined";
import InsertDriveFileOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import ArchiveOutlinedIcon from "@mui/icons-material/ArchiveOutlined";

/**
 * Icon component for file types in the config tree.
 * @param {{type:string}} props
 * @returns The icon element.
 */
const ICONS = {
	folder: FolderOutlinedIcon,
	file: InsertDriveFileOutlinedIcon,
	backup: ArchiveOutlinedIcon,
};

/**
 * Icon component for file types in the config tree.
 * @param {{type:string}} props
 * @returns The icon element.
 */
const FileTypeIconComponent = ({ type }) => {
	const TreeIcon = ICONS[type] || ICONS.file;

	return (
		<Box component="span" sx={{ display: "inline-flex", alignItems: "center", mr: 1 }}>
			<TreeIcon fontSize="small" />
		</Box>
	);
};

// memoize to avoid useless re-renders when parent tree updates
const FileTypeIcon = React.memo(FileTypeIconComponent);

export default FileTypeIcon;
