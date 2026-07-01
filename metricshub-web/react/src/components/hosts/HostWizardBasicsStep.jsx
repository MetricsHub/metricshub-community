import * as React from "react";
import { Box, MenuItem, Stack } from "@mui/material";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import SettingsInputHdmiIcon from "@mui/icons-material/SettingsInputHdmi";
import HostNameChipInput from "./HostNameChipInput";
import HostTypeIcon from "./HostTypeIcon";
import ProtocolSelectionGrid from "./ProtocolSelectionGrid";
import ResourceGroupPlacementCombobox from "./ResourceGroupPlacementCombobox";
import { getHostNames } from "./host-config-utils";
import { FormSection, LabeledSelect, LabeledTextField } from "./guided-config-form-primitives";
import {
	filterSelectedProtocolsForHostType,
	HOST_TYPE_LABELS,
	HOST_TYPE_UI,
	HOST_TYPE_UNSELECTED,
	HOST_TYPES,
} from "./protocol-definitions";

const HostTypeOption = ({ hostType }) => (
	<Stack direction="row" alignItems="center" spacing={1.25}>
		<HostTypeIcon hostType={hostType} size={20} />
		<span>{HOST_TYPE_LABELS[hostType] || hostType}</span>
	</Stack>
);

/**
 * First wizard step: host identity and placement.
 *
 * @param {object} props
 * @param {object} props.values
 * @param {(patch: object) => void} props.onChange
 * @param {Record<string, string>} [props.errors]
 * @param {string[]} props.resourceGroups
 * @param {string[]} [props.existingHostIds]
 */
const HostWizardBasicsStep = ({
	values,
	onChange,
	errors = {},
	resourceGroups,
	existingHostIds = [],
}) => {
	const manualHostIdRef = React.useRef(false);
	const [autoSuffixMessage, setAutoSuffixMessage] = React.useState("");

	const buildUniqueHostId = React.useCallback(
		(rawHostName) => {
			const hostNames = getHostNames(rawHostName);
			const base =
				hostNames.length > 1 ? "multi_resource" : hostNames[0] || String(rawHostName || "").trim();
			if (!base) {
				return "";
			}
			const existing = new Set(
				(existingHostIds || []).map((id) => String(id).trim()).filter(Boolean),
			);
			if (!existing.has(base)) {
				return base;
			}
			let i = 1;
			while (existing.has(`${base}_${i}`)) {
				i += 1;
			}
			return `${base}_${i}`;
		},
		[existingHostIds],
	);

	return (
		<Stack spacing={2}>
			<FormSection
				id="resource-create-details"
				emphasized
				icon={<DnsOutlinedIcon />}
				title="Resource Information"
				description="Hostname, configuration key, and resource group — how this resource is named and placed in metricshub-ui.yaml."
			>
				<Stack spacing={2}>
					<HostNameChipInput
						staticLabel
						value={values.hostName}
						onChange={(hostName) => {
							if (values._editMode || manualHostIdRef.current) {
								setAutoSuffixMessage("");
								onChange({ hostName });
								return;
							}
							const generatedHostId = buildUniqueHostId(hostName);
							const hostNames = getHostNames(hostName);
							const baseHostId =
								hostNames.length > 1
									? "multi_resource"
									: hostNames[0] || String(hostName || "").trim();
							const suffixed = Boolean(
								baseHostId &&
								generatedHostId &&
								generatedHostId !== baseHostId &&
								generatedHostId.startsWith(`${baseHostId}_`),
							);
							setAutoSuffixMessage(
								suffixed
									? `Resource ID "${baseHostId}" already exists. Using "${generatedHostId}" instead.`
									: "",
							);
							onChange({ hostName, hostId: generatedHostId });
						}}
						error={Boolean(errors.hostName)}
						helperText={errors.hostName}
					/>
					<Box
						sx={{
							display: "grid",
							gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" },
							gap: 2,
							alignItems: "start",
						}}
					>
						<LabeledTextField
							id="resource-id"
							label="Resource ID"
							required
							placeholder="e.g. web-server-01"
							value={values.hostId}
							onChange={(e) => {
								manualHostIdRef.current = true;
								setAutoSuffixMessage("");
								onChange({ hostId: e.target.value });
							}}
							error={Boolean(errors.hostId)}
							helperText={
								errors.hostId ||
								autoSuffixMessage ||
								(values._editMode
									? "Renames the resource key in metricshub-ui.yaml"
									: "Unique key in metricshub-ui.yaml")
							}
						/>
						<ResourceGroupPlacementCombobox
							labelsAbove
							resourceGroups={resourceGroups}
							targetType={values.targetType}
							resourceGroup={values.resourceGroup}
							onChange={onChange}
							error={Boolean(errors.resourceGroup)}
							helperText={errors.resourceGroup}
						/>
					</Box>
				</Stack>
			</FormSection>

			<FormSection
				id="resource-create-device-protocols"
				emphasized
				icon={<SettingsInputHdmiIcon />}
				title={HOST_TYPE_UI.sectionTitle}
				description={HOST_TYPE_UI.sectionDescription}
			>
				<Stack spacing={2}>
					<Box sx={{ maxWidth: { md: "50%" } }}>
						<LabeledSelect
							id="wizard-host-type"
							label={HOST_TYPE_UI.fieldLabel}
							attributeName={HOST_TYPE_UI.attributeName}
							required
							error={Boolean(errors.hostType)}
							helperText={errors.hostType || HOST_TYPE_UI.fieldHelper}
							selectProps={{
								value: values.hostType || HOST_TYPE_UNSELECTED,
								onChange: (e) => {
									const hostType = e.target.value;
									onChange({
										hostType,
										selectedProtocols: filterSelectedProtocolsForHostType(
											values.selectedProtocols,
											hostType,
										),
									});
								},
								renderValue: (selected) => {
									if (!selected || selected === HOST_TYPE_UNSELECTED) {
										return "";
									}
									return <HostTypeOption hostType={String(selected)} />;
								},
							}}
						>
							<MenuItem value={HOST_TYPE_UNSELECTED} disabled sx={{ display: "none" }} />
							{HOST_TYPES.map((t) => (
								<MenuItem key={t} value={t} sx={{ py: 1 }}>
									<HostTypeOption hostType={t} />
								</MenuItem>
							))}
						</LabeledSelect>
					</Box>
					<ProtocolSelectionGrid
						showLabel={false}
						hostType={values.hostType}
						value={values.selectedProtocols || []}
						onChange={(selectedProtocols) => onChange({ selectedProtocols })}
						error={Boolean(errors.selectedProtocols)}
						helperText={errors.selectedProtocols}
					/>
				</Stack>
			</FormSection>
		</Stack>
	);
};

export default HostWizardBasicsStep;
