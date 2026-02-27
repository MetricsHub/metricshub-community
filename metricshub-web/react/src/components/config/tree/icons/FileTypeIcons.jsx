// src/components/config/tree/icons/FileTypeIcons.jsx
import * as React from "react";
import { Box } from "@mui/material";
import FolderOutlinedIcon from "@mui/icons-material/FolderOutlined";
import InsertDriveFileOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import ArchiveOutlinedIcon from "@mui/icons-material/ArchiveOutlined";
import CodeIcon from "@mui/icons-material/Code";

/**
 * Icon component for file types in the config tree.
 * @param {{type:string}} props
 * @returns The icon element.
 */
const ICONS = {
	folder: FolderOutlinedIcon,
	file: InsertDriveFileOutlinedIcon,
	vm: CodeIcon,
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
		<Box
			component="span"
			sx={{ display: "inline-flex", alignItems: "center", mr: 1, transition: "color 0.4s ease" }}
		>
			<TreeIcon fontSize="small" />
		</Box>
	);
};

// memoize to avoid useless re-renders when parent tree updates
const FileTypeIcon = React.memo(FileTypeIconComponent);

export default FileTypeIcon;
