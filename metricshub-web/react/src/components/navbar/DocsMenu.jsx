import React from "react";
import { IconButton, Menu, MenuItem, ListItemIcon, ListItemText, Tooltip } from "@mui/material";
import HelpIcon from "@mui/icons-material/Help";
import MenuBookOutlinedIcon from "@mui/icons-material/MenuBookOutlined";
import DataObjectIcon from "@mui/icons-material/DataObject";

/**
 * Help / documentation menu with links to external docs and OpenAPI spec.
 *
 * @returns {JSX.Element}
 */
const DocsMenu = () => {
	const [anchorEl, setAnchorEl] = React.useState(null);
	const open = Boolean(anchorEl);

	const handleOpen = React.useCallback((event) => {
		setAnchorEl(event.currentTarget);
	}, []);
	const handleClose = React.useCallback(() => setAnchorEl(null), []);

	return (
		<>
			<Tooltip title="Docs" arrow enterDelay={200}>
				<IconButton
					aria-label="Open help menu"
					aria-controls={open ? "docs-menu" : undefined}
					aria-haspopup="true"
					aria-expanded={open ? "true" : undefined}
					onClick={handleOpen}
				>
					<HelpIcon />
				</IconButton>
			</Tooltip>

			<Menu
				id="docs-menu"
				anchorEl={anchorEl}
				open={open}
				onClose={handleClose}
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem
					component="a"
					href="https://metricshub.com/docs/latest/"
					target="_blank"
					rel="noopener noreferrer"
					onClick={handleClose}
				>
					<ListItemIcon>
						<MenuBookOutlinedIcon fontSize="small" />
					</ListItemIcon>
					<ListItemText>Documentation</ListItemText>
				</MenuItem>
				<MenuItem
					component="a"
					href="/swagger-ui/index.html"
					target="_blank"
					rel="noopener noreferrer"
					onClick={handleClose}
				>
					<ListItemIcon>
						<DataObjectIcon fontSize="small" />
					</ListItemIcon>
					<ListItemText>OpenAPI</ListItemText>
				</MenuItem>
			</Menu>
		</>
	);
};

export default React.memo(DocsMenu);
