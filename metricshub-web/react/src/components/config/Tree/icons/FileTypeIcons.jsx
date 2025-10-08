// src/components/config/tree/icons/FileTypeIcons.jsx
import { Box } from "@mui/material";
import FolderOutlinedIcon from "@mui/icons-material/FolderOutlined";
import InsertDriveFileOutlinedIcon from "@mui/icons-material/DescriptionOutlined";

export default function FileTypeIcon({ type }) {
	const size = 18;
	return (
		<Box component="span" sx={{ display: "inline-flex", alignItems: "center", mr: 1 }}>
			{type === "folder" ? (
				<FolderOutlinedIcon sx={{ fontSize: size }} />
			) : (
				<InsertDriveFileOutlinedIcon sx={{ fontSize: size }} />
			)}
		</Box>
	);
}
