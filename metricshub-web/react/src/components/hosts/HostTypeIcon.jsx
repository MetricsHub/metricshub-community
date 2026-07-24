import * as React from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";
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
 * Icon for a host.type value: palette-aware OS/brand logos, all in a uniform box.
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

	return <Box sx={slotSx} aria-hidden />;
};

export default HostTypeIcon;
