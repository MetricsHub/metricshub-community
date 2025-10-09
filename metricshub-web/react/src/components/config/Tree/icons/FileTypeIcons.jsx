// src/components/config/tree/icons/FileTypeIcons.jsx
import * as React from "react";
import { Box, useTheme } from "@mui/material";
import FolderOutlinedIcon from "@mui/icons-material/FolderOutlined";
import InsertDriveFileOutlinedIcon from "@mui/icons-material/DescriptionOutlined";

/**
 * Icon component for file types in the config tree.
 * @param {{type:string}} props
 * @returns The icon element.
 */
const ICONS = {
	folder: FolderOutlinedIcon,
	file: InsertDriveFileOutlinedIcon,
};

/**
 * Icon component for file types in the config tree.
 * @param {{type:string}} props
 * @returns The icon element.
 */
const FileTypeIconComponent = ({ type }) => {
	const theme = useTheme();
	const Icon = ICONS[type] || ICONS.file;
	const size = theme.custom.treeIconSize;

	return (
		<Box component="span" sx={{ display: "inline-flex", alignItems: "center", mr: 1 }}>
			<Icon sx={{ fontSize: size }} />
		</Box>
	);
};

// memoize to avoid useless re-renders when parent tree updates
const FileTypeIcon = React.memo(FileTypeIconComponent);

export default FileTypeIcon;
