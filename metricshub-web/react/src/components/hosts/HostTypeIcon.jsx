import * as React from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import DevicesOtherIcon from "@mui/icons-material/DevicesOther";
import { HOST_TYPE_LABELS } from "./protocol-definitions";

/** @param {string} name */
const themedHostTypeIcon = (name) => ({
	dark: `/host-types/${name}-dark.svg`,
	light: `/host-types/${name}-light.svg`,
});

/**
 * Palette-mode-specific logos served from /public/host-types. Each SVG's viewBox
 * is cropped tight to its own artwork, so they all scale consistently through
 * plain object-fit: contain, no per-icon fudge factor.
 */
const HOST_TYPE_THEMED_ICONS = {
	aix: themedHostTypeIcon("aix"),
	hpux: themedHostTypeIcon("hpux"),
	ibmi: themedHostTypeIcon("ibmi"),
	linux: themedHostTypeIcon("linux"),
	network: themedHostTypeIcon("network"),
	oob: themedHostTypeIcon("oob"),
	solaris: themedHostTypeIcon("solaris"),
	storage: themedHostTypeIcon("storage"),
	windows: themedHostTypeIcon("windows"),
};

/**
 * Host types with no vendor artwork, drawn from an MUI glyph instead. `other` is
 * a catch-all device kind, so there is no logo to show.
 */
const HOST_TYPE_COMPONENT_ICONS = {
	other: DevicesOtherIcon,
};

/**
 * Icon for a host.type value: a palette-aware OS/brand logo when one exists,
 * otherwise an MUI glyph, always in a uniform box.
 *
 * @param {object} props
 * @param {string} props.hostType
 * @param {number} [props.size]
 */
const HostTypeIcon = ({ hostType, size = 20 }) => {
	const theme = useTheme();
	const mode = theme.palette.mode === "dark" ? "dark" : "light";
	// Every icon occupies an identical square box; logos of any aspect ratio are
	// centered and scaled to fit inside it, so all host types share the exact same
	// footprint and their labels line up.
	const slotSx = {
		width: size,
		height: size,
		display: "flex",
		alignItems: "center",
		justifyContent: "center",
		flexShrink: 0,
	};

	const brandSrc = HOST_TYPE_THEMED_ICONS[hostType]?.[mode];
	if (brandSrc) {
		return (
			<Box sx={slotSx}>
				<Box
					component="img"
					src={brandSrc}
					alt={HOST_TYPE_LABELS[hostType] || hostType}
					sx={{
						maxWidth: "100%",
						maxHeight: "100%",
						objectFit: "contain",
						display: "block",
					}}
				/>
			</Box>
		);
	}

	const IconComponent = HOST_TYPE_COMPONENT_ICONS[hostType];
	if (IconComponent) {
		return (
			<Box sx={slotSx}>
				<IconComponent
					titleAccess={HOST_TYPE_LABELS[hostType] || hostType}
					// Same treatment as the themed SVGs: brand blue in light mode, white in dark.
					sx={{ fontSize: size, color: mode === "dark" ? "common.white" : "primary.main" }}
				/>
			</Box>
		);
	}

	return <Box sx={slotSx} aria-hidden />;
};

export default HostTypeIcon;
