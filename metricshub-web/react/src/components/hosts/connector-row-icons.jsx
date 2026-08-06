import * as React from "react";
import { Box } from "@mui/material";
import SettingsInputHdmiIcon from "@mui/icons-material/SettingsInputHdmi";
import SettingsOutlinedIcon from "@mui/icons-material/SettingsOutlined";

/** Compact tree row icon spacing — matches configuration file tree density. */
export const CONNECTOR_TREE_ICON_WRAP_SX = {
	display: "inline-flex",
	alignItems: "center",
	justifyContent: "center",
	flexShrink: 0,
	mr: 1,
};

/** Blue toothed-wheel icon for configurable (variable) connectors. */
export const CONFIGURABLE_CONNECTOR_ICON_SX = {
	color: "primary.main",
};

/**
 * Gear / parameter-settings icon for configurable connectors (tree rows, catalog table).
 *
 * @param {object} props
 * @param {import("@mui/material").SvgIconProps["fontSize"]} [props.fontSize]
 * @param {object} [props.sx]
 */
export const ConfigurableConnectorIcon = ({ fontSize = "small", sx }) => (
	<SettingsOutlinedIcon fontSize={fontSize} sx={{ ...CONFIGURABLE_CONNECTOR_ICON_SX, ...sx }} />
);

/**
 * Blue settings-wheel icon for configurable (variable) connector template rows.
 */
export const ConfigurableConnectorTreeIcon = () => (
	<Box component="span" sx={{ ...CONNECTOR_TREE_ICON_WRAP_SX, ...CONFIGURABLE_CONNECTOR_ICON_SX }}>
		<ConfigurableConnectorIcon />
	</Box>
);

/**
 * Standard connector icon used for instantiated variable connector rows.
 */
export const StandardConnectorTreeIcon = () => (
	<Box component="span" sx={{ ...CONNECTOR_TREE_ICON_WRAP_SX, color: "success.main" }}>
		<SettingsInputHdmiIcon fontSize="small" />
	</Box>
);
