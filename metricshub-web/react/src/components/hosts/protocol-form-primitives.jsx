import * as React from "react";
import { Box, InputAdornment, Typography } from "@mui/material";
import DatabaseIcon from "./DatabaseIcon";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import KeyOutlinedIcon from "@mui/icons-material/KeyOutlined";
import LinkIcon from "@mui/icons-material/Link";
import LockOutlinedIcon from "@mui/icons-material/LockOutlined";
import PersonOutlineIcon from "@mui/icons-material/PersonOutline";
import ReplayIcon from "@mui/icons-material/Replay";
import SecurityIcon from "@mui/icons-material/Security";
import SettingsEthernetIcon from "@mui/icons-material/SettingsEthernet";
import StorageIcon from "@mui/icons-material/Storage";
import TerminalIcon from "@mui/icons-material/Terminal";
import TimerOutlinedIcon from "@mui/icons-material/TimerOutlined";
import { filledInputNoLabelSx } from "./guided-config-form-primitives";
import FieldHelpTooltip from "./FieldHelpTooltip";

export const protocolFieldIconSx = { color: "action.active", fontSize: 20, display: "block" };

/** @type {Record<string, React.ElementType>} */
const PROTOCOL_FIELD_ICONS = {
	hostname: DnsOutlinedIcon,
	username: PersonOutlineIcon,
	password: LockOutlinedIcon,
	privacyPassword: LockOutlinedIcon,
	community: LockOutlinedIcon,
	port: SettingsEthernetIcon,
	timeout: TimerOutlinedIcon,
	privateKey: KeyOutlinedIcon,
	namespace: StorageIcon,
	url: LinkIcon,
	database: DatabaseIcon,
	vcenter: DnsOutlinedIcon,
	contextName: SecurityIcon,
	authentications: SecurityIcon,
	retryIntervals: ReplayIcon,
	sudoCommand: TerminalIcon,
	useSudoCommands: TerminalIcon,
	bmcKey: KeyOutlinedIcon,
	type: StorageIcon,
};

/**
 * @param {string} fieldName
 * @returns {React.ReactNode | null}
 */
export const getProtocolFieldStartIcon = (fieldName) => {
	const Icon = PROTOCOL_FIELD_ICONS[fieldName];
	return Icon ? <Icon sx={protocolFieldIconSx} /> : null;
};

/** Start adornment centered with the input text (filled inputs without a floating label). */
export const ProtocolFieldStartAdornment = ({ children }) => (
	<InputAdornment
		position="start"
		sx={{
			marginTop: 0,
			marginBottom: 0,
			marginLeft: 0,
			marginRight: 1.25,
			height: "auto",
			maxHeight: "none",
			display: "inline-flex",
			alignItems: "center",
			alignSelf: "center",
		}}
	>
		{children}
	</InputAdornment>
);

/**
 * @deprecated import FieldHelpTooltip from "./FieldHelpTooltip" instead
 */
export const ProtocolFieldHelpTooltip = FieldHelpTooltip;

/**
 * Field label row: title, optional required marker, optional inline description.
 *
 * @param {object} props
 * @param {string} props.label
 * @param {boolean} [props.required]
 * @param {string} [props.description]
 * @param {string} [props.helpTooltip]
 */
export const ProtocolFieldLabelRow = ({ label, required = false, description, helpTooltip }) => (
	<Box
		sx={{
			display: "flex",
			alignItems: "center",
			flexWrap: "wrap",
			columnGap: 0.75,
			rowGap: 0,
			mb: 0.75,
		}}
	>
		<Typography variant="body2" fontWeight={600}>
			{label}
		</Typography>
		{required ? (
			<Typography component="span" variant="body2" color="error.main" sx={{ lineHeight: 1 }}>
				*
			</Typography>
		) : null}
		{description ? (
			<Typography variant="body2" color="text.secondary">
				{description}
			</Typography>
		) : null}
		{helpTooltip ? <ProtocolFieldHelpTooltip title={helpTooltip} /> : null}
	</Box>
);

/** Stack sx for protocol config forms (external labels, filled inputs). */
export const protocolFieldStackSx = {
	...filledInputNoLabelSx,
	"& .MuiFilledInput-root": {
		display: "flex",
		alignItems: "center",
		paddingTop: 0,
		paddingBottom: 0,
	},
	"& .MuiFilledInput-input": {
		paddingTop: "10px",
		paddingBottom: "10px",
		lineHeight: 1.5,
	},
	"& .MuiInputAdornment-filled.MuiInputAdornment-positionStart, & .MuiInputAdornment-filled.MuiInputAdornment-positionEnd":
		{
			marginTop: 0,
			marginBottom: 0,
		},
	"& .MuiInputAdornment-root": {
		display: "inline-flex",
		alignItems: "center",
		alignSelf: "center",
		height: "auto",
		maxHeight: "none",
	},
};

export const protocolTextFieldProps = {
	hiddenLabel: true,
	size: "small",
};
