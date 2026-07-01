import * as React from "react";
import { Box } from "@mui/material";
import RouterOutlinedIcon from "@mui/icons-material/RouterOutlined";
import SecurityOutlinedIcon from "@mui/icons-material/SecurityOutlined";
import StorageOutlinedIcon from "@mui/icons-material/StorageOutlined";
import { HOST_TYPE_LABELS } from "./protocol-definitions";

/** Brand / OS logos served from /public. */
const HOST_TYPE_BRAND_ICONS = {
	windows: "/windows.svg",
	linux: "/linux.svg",
	aix: "/aix.svg",
	hpux: "/hpux.svg",
	solaris: "/solaris.svg",
};

/** Generic icons for non-OS host types. */
const GENERIC_HOST_TYPE_ICONS = {
	network: RouterOutlinedIcon,
	storage: StorageOutlinedIcon,
	oob: SecurityOutlinedIcon,
};

/**
 * Icon for a host.type value: OS brand logos where available, generic icons otherwise.
 *
 * @param {object} props
 * @param {string} props.hostType
 * @param {number} [props.size]
 */
const HostTypeIcon = ({ hostType, size = 20 }) => {
	const brandSrc = HOST_TYPE_BRAND_ICONS[hostType];
	if (brandSrc) {
		return (
			<Box
				component="img"
				src={brandSrc}
				alt={HOST_TYPE_LABELS[hostType] || hostType}
				sx={{
					width: size,
					height: size,
					objectFit: "contain",
					display: "block",
					flexShrink: 0,
				}}
			/>
		);
	}

	const GenericIcon = GENERIC_HOST_TYPE_ICONS[hostType];
	if (GenericIcon) {
		return <GenericIcon sx={{ fontSize: size, color: "text.secondary", flexShrink: 0 }} />;
	}

	return null;
};

export default HostTypeIcon;
