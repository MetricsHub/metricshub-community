const DEFAULT_BORDER_COLOR = "#1565c0";

/**
 * @param {string} protocol
 * @returns {string}
 */
export const getProtocolBorderColor = () => DEFAULT_BORDER_COLOR;

/**
 * MUI sx for a protocol badge: colored border, transparent fill.
 *
 * Both the label and the border are theme-aware, picked for legibility next to
 * the bright red/green health dots:
 *  - dark mode  → white
 *  - light mode → black
 *
 * Callers may still pass the protocol id (e.g. `protocolChipSx("ssh")`) — the
 * argument is accepted but ignored now that we no longer theme per-protocol.
 *
 * @returns {import("@mui/material").SxProps}
 */
export const protocolChipSx = () => ({
	border: (theme) =>
		`1.5px solid ${theme.palette.mode === "dark" ? theme.palette.common.white : theme.palette.common.black}`,
	color: (theme) =>
		theme.palette.mode === "dark" ? theme.palette.common.white : theme.palette.common.black,
	bgcolor: "transparent",
	backgroundColor: "transparent",
	fontWeight: 600,
	"& .MuiChip-label": {
		px: 1,
	},
});
