import * as React from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import StorageOutlinedIcon from "@mui/icons-material/StorageOutlined";
import { HOST_TYPE_LABELS } from "./protocol-definitions";

/** Fixed slot so host.type labels align regardless of logo aspect ratio. */
const HOST_TYPE_ICON_SLOT_WIDTH_PX = 56;

/**
 * The themed logos ship on a padded 30×30 canvas (artwork only fills the center),
 * so they need to render larger than edge-to-edge logos to look the same size.
 */
const THEMED_ICON_SCALE = 1.7;

/** @param {string} name */
const themedHostTypeIcon = (name) => ({
	dark: `/host-types/${name}-dark.svg`,
	light: `/host-types/${name}-light.svg`,
});

/** Palette-mode-specific logos served from /public/host-types. */
const HOST_TYPE_THEMED_ICONS = {
	aix: themedHostTypeIcon("aix"),
	hpux: themedHostTypeIcon("hpux"),
	network: themedHostTypeIcon("network"),
	oob: themedHostTypeIcon("oob"),
	solaris: themedHostTypeIcon("solaris"),
};

/** Brand / OS logos served from /public (Wikimedia Commons) — no themed variant. */
const HOST_TYPE_BRAND_ICONS = {
	windows: "/windows.svg",
	linux: "/linux.png",
};

/** Generic icons for host types without a logo. */
const GENERIC_HOST_TYPE_ICONS = {
	storage: StorageOutlinedIcon,
};

/**
 * Icon for a host.type value: OS brand logos where available, generic icons otherwise.
 *
 * @param {object} props
 * @param {string} props.hostType
 * @param {number} [props.size]
 */
const HostTypeIcon = ({ hostType, size = 20 }) => {
	const theme = useTheme();
	const mode = theme.palette.mode === "dark" ? "dark" : "light";
	const themedSrc = HOST_TYPE_THEMED_ICONS[hostType]?.[mode];
	const renderSize = themedSrc ? Math.round(size * THEMED_ICON_SCALE) : size;
	const slotSx = {
		width: HOST_TYPE_ICON_SLOT_WIDTH_PX,
		height: renderSize,
		display: "flex",
		alignItems: "center",
		justifyContent: "center",
		flexShrink: 0,
	};

	const brandSrc = themedSrc ?? HOST_TYPE_BRAND_ICONS[hostType];
	if (brandSrc) {
		return (
			<Box sx={slotSx}>
				<Box
					component="img"
					src={brandSrc}
					alt={HOST_TYPE_LABELS[hostType] || hostType}
					sx={{
						maxWidth: "100%",
						maxHeight: renderSize,
						width: "auto",
						height: "auto",
						objectFit: "contain",
						display: "block",
					}}
				/>
			</Box>
		);
	}

	const GenericIcon = GENERIC_HOST_TYPE_ICONS[hostType];
	if (GenericIcon) {
		return (
			<Box sx={slotSx}>
				<GenericIcon sx={{ fontSize: size, color: "text.secondary" }} />
			</Box>
		);
	}

	return <Box sx={slotSx} aria-hidden />;
};

export default HostTypeIcon;
