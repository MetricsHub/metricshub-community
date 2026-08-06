import * as React from "react";
import {
	Button,
	CircularProgress,
	ListItemIcon,
	ListItemText,
	Menu,
	MenuItem,
	Tooltip,
} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { openExplorerResourcePath } from "./host-explorer-links";
import { useExplorerResourceReadiness } from "../../hooks/use-explorer-resource-readiness";
import { guidedConfigOutlinedPrimaryHoverSx } from "./guided-config-ui-tokens";

/**
 * Opens the guided-config resource in MetricsHub Explorer (new tab).
 * Multi-host resources show a menu to pick which monitored host to open.
 *
 * @param {object} props
 * @param {string | null | undefined} props.resourceGroup
 * @param {string} props.hostId
 * @param {Record<string, unknown>} [props.hostConfig]
 * @param {boolean} [props.disabled]
 */
const ShowInExplorerButton = ({ resourceGroup, hostId, hostConfig, disabled = false }) => {
	const [menuAnchor, setMenuAnchor] = React.useState(null);

	const { ready, resourceLinks } = useExplorerResourceReadiness({
		resourceGroup,
		hostId,
		hostConfig,
		enabled: Boolean(hostId),
	});

	if (resourceLinks.length === 0) {
		return null;
	}

	if (!ready) {
		return (
			<Tooltip title="Waiting for the first collection cycle to populate Explorer">
				<span>
					<Button
						variant="outlined"
						size="small"
						disabled
						startIcon={<CircularProgress size={16} thickness={5} color="inherit" />}
						sx={{ flexShrink: 0 }}
					>
						Collecting…
					</Button>
				</span>
			</Tooltip>
		);
	}

	const multiHost = resourceLinks.length > 1;
	const tooltip = multiHost
		? "Choose a monitored host to open in Explorer"
		: "Open this resource in Explorer";

	const handleSingleOpen = () => {
		openExplorerResourcePath(resourceLinks[0].path);
	};

	const handlePickHost = (path) => {
		setMenuAnchor(null);
		openExplorerResourcePath(path);
	};

	return (
		<>
			<Tooltip title={tooltip}>
				<span>
					<Button
						variant="outlined"
						size="small"
						startIcon={<OpenInNewIcon fontSize="small" />}
						endIcon={multiHost ? <ArrowDropDownIcon /> : undefined}
						disabled={disabled}
						onClick={multiHost ? (event) => setMenuAnchor(event.currentTarget) : handleSingleOpen}
						sx={{ flexShrink: 0, ...guidedConfigOutlinedPrimaryHoverSx }}
					>
						Show in explorer
					</Button>
				</span>
			</Tooltip>
			{multiHost ? (
				<Menu
					anchorEl={menuAnchor}
					open={Boolean(menuAnchor)}
					onClose={() => setMenuAnchor(null)}
					anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
					transformOrigin={{ vertical: "top", horizontal: "right" }}
					slotProps={{
						paper: {
							sx: { minWidth: 240, maxHeight: 360 },
						},
					}}
				>
					{resourceLinks.map((link) => (
						<MenuItem key={link.path} onClick={() => handlePickHost(link.path)}>
							<ListItemIcon>
								<OpenInNewIcon fontSize="small" />
							</ListItemIcon>
							<ListItemText
								primary={link.label}
								secondary={link.resourceId !== link.label ? link.resourceId : undefined}
								primaryTypographyProps={{ noWrap: true }}
								secondaryTypographyProps={{ noWrap: true }}
							/>
						</MenuItem>
					))}
				</Menu>
			) : null}
		</>
	);
};

export default ShowInExplorerButton;
