import React from "react";
import { IconButton, Menu, MenuItem, ListItemIcon, ListItemText, Tooltip } from "@mui/material";
import HelpIcon from "@mui/icons-material/Help";
import MenuBookOutlinedIcon from "@mui/icons-material/MenuBookOutlined";
import DataObjectIcon from "@mui/icons-material/DataObject";
import SupportAgentIcon from "@mui/icons-material/SupportAgent";
import { SUPPORT_URL } from "../../utils/constants";

/**
 * Help / documentation menu with links to external docs and OpenAPI spec.
 *
 * @returns {JSX.Element}
 */
const HelpMenu = () => {
	const [anchorEl, setAnchorEl] = React.useState(null);
	const open = Boolean(anchorEl);

	const handleOpen = React.useCallback((event) => {
		setAnchorEl(event.currentTarget);
	}, []);
	const handleClose = React.useCallback(() => setAnchorEl(null), []);

	return (
		<>
			<Tooltip title="Help" arrow enterDelay={200}>
				<IconButton
					aria-label="Open help menu"
					aria-controls={open ? "help-menu" : undefined}
					aria-haspopup="true"
					aria-expanded={open ? "true" : undefined}
					onClick={handleOpen}
				>
					<HelpIcon />
				</IconButton>
			</Tooltip>

			<Menu
				id="help-menu"
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
				<MenuItem
					component="a"
					href={SUPPORT_URL}
					target="_blank"
					rel="noopener noreferrer"
					onClick={handleClose}
				>
					<ListItemIcon>
						<SupportAgentIcon fontSize="small" />
					</ListItemIcon>
					<ListItemText>Support</ListItemText>
				</MenuItem>
			</Menu>
		</>
	);
};

export default React.memo(HelpMenu);
