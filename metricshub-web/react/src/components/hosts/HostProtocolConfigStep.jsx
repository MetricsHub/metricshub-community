import * as React from "react";
import { Box, Paper, Stack, Typography } from "@mui/material";
import ProtocolConfigForm from "./ProtocolConfigForm";
import ProtocolTestButton from "./ProtocolTestButton";
import { guidedConfigBorderedPanelSx } from "./guided-config-form-primitives";
import {
	PROTOCOL_FIELDS,
	PROTOCOL_OPTIONS,
	isAuthFieldOptionalOnLocalhost,
	isLocalhostHost,
} from "./protocol-definitions";

/**
 * Protocol-specific configuration step.
 *
 * @param {object} props
 * @param {string} props.protocol
 * @param {Record<string, unknown>} props.values
 * @param {(name: string, value: unknown) => void} props.onChange
 * @param {Record<string, string>} [props.errors]
 * @param {string} [props.hostId]
 * @param {string} [props.hostName]
 * @param {boolean} [props.allowPasswordReveal] show/hide toggle on password fields (create flow only)
 * @param {boolean} [props.deferEncryptUntilSave] encrypt protocol passwords on submit only
 * @param {boolean} [props.showHeader] title + yaml hint (false when the parent section already has a heading)
 * @param {boolean} [props.borderedContainer] wrap test + form fields in a 1px rounded panel
 */
const HostProtocolConfigStep = ({
	protocol,
	values,
	onChange,
	errors = {},
	hostId,
	hostName,
	allowPasswordReveal = false,
	deferEncryptUntilSave = false,
	showHeader = true,
	borderedContainer = false,
}) => {
	const fields = PROTOCOL_FIELDS[protocol] || [];
	const protocolLabel = PROTOCOL_OPTIONS.find((p) => p.id === protocol)?.label || protocol;
	const isLocal = isLocalhostHost(hostId, hostName);

	const fieldRequired = (field) => {
		if (isLocal && (isAuthFieldOptionalOnLocalhost(field.name) || field.type === "authChoice")) {
			return false;
		}
		return Boolean(field.required);
	};

	const header = showHeader ? (
		<Box>
			<Typography variant="subtitle1" fontWeight={600}>
				{protocolLabel} configuration
			</Typography>
			<Typography variant="body2" color="text.secondary">
				Settings written under protocols.{protocol} in metricshub-ui.yaml
			</Typography>
		</Box>
	) : null;

	const formBody = (
		<Stack spacing={2}>
			<ProtocolTestButton
				protocol={protocol}
				hostName={hostName}
				hostId={hostId}
				protocolValues={values}
			/>
			<ProtocolConfigForm
				protocol={protocol}
				fields={fields}
				values={values}
				onChange={onChange}
				errors={errors}
				isRequired={fieldRequired}
				allowPasswordReveal={allowPasswordReveal}
				deferEncryptUntilSave={deferEncryptUntilSave}
			/>
		</Stack>
	);

	return (
		<Stack spacing={2}>
			{header}
			{borderedContainer ? (
				<Paper elevation={0} sx={guidedConfigBorderedPanelSx}>
					{formBody}
				</Paper>
			) : (
				formBody
			)}
		</Stack>
	);
};

export default HostProtocolConfigStep;
