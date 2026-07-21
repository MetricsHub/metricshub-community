import * as React from "react";
import { Box, MenuItem, Stack } from "@mui/material";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";
import SettingsInputHdmiIcon from "@mui/icons-material/SettingsInputHdmi";
import HostNameChipInput from "./HostNameChipInput";
import HostTypeIcon from "./HostTypeIcon";
import ProtocolSelectionPanel from "./ProtocolSelectionPanel";
// Revert to chip grid: import ProtocolSelectionGridLegacy as ProtocolSelectionPanel from "./ProtocolSelectionGrid.legacy";
import ResourceGroupPlacementCombobox from "./ResourceGroupPlacementCombobox";
import ResourceAdvancedOptionsSection from "./ResourceAdvancedOptionsSection";
import { createEmptyResourceAdvancedState } from "./resource-config-fields";
import { getHostNames } from "./host-config-utils";
import { useResourceDefaults } from "./use-resource-defaults";
import { useAgentOsType } from "../../hooks/use-agent-os-type";
import {
	FormSection,
	LabeledSelect,
	LabeledTextField,
	filledInputNoLabelSx,
} from "./guided-config-form-primitives";
import {
	filterSelectedProtocolsForHostType,
	HOST_TYPE_LABELS,
	HOST_TYPE_UI,
	HOST_TYPE_UNSELECTED,
	HOST_TYPES,
} from "./protocol-definitions";

const HOST_TYPE_FIELD_ICON_SIZE = 36;

const hostTypeSelectSx = {
	...filledInputNoLabelSx,
	"& .MuiFilledInput-input": {
		...filledInputNoLabelSx["& .MuiFilledInput-input"],
		paddingTop: "16px",
		paddingBottom: "16px",
		display: "flex",
		alignItems: "center",
	},
};

const HostTypeOption = ({ hostType }) => (
	<Stack direction="row" alignItems="center" spacing={1.25}>
		<HostTypeIcon hostType={hostType} size={HOST_TYPE_FIELD_ICON_SIZE} />
		<span>{HOST_TYPE_LABELS[hostType] || hostType}</span>
	</Stack>
);

/**
 * First form section: host identity and placement.
 *
 * @param {object} props
 * @param {object} props.values
 * @param {(patch: object) => void} props.onChange
 * @param {Record<string, string>} [props.errors]
 * @param {string[]} props.resourceGroups
 * @param {{ groups: Record<string, string[]>; standalone: string[] }} [props.existingHostIdScopes]
 */
const HostConfigBasicsSection = ({
	values,
	onChange,
	errors = {},
	resourceGroups,
	existingHostIdScopes,
	onCreateResourceGroup,
}) => {
	const manualHostIdRef = React.useRef(false);
	const [autoSuffixMessage, setAutoSuffixMessage] = React.useState("");
	const inheritedDefaults = useResourceDefaults(values.targetType, values.resourceGroup);
	const agentOsType = useAgentOsType();

	// Monitoring Windows hosts requires the agent itself to run on Windows: hide the
	// Windows host type otherwise (unknown os.type keeps everything visible, and a
	// resource already configured as Windows stays selectable).
	const hostTypeOptions = React.useMemo(() => {
		if (!agentOsType || agentOsType === "windows") {
			return HOST_TYPES;
		}
		return HOST_TYPES.filter((hostType) => hostType !== "windows" || hostType === values.hostType);
	}, [agentOsType, values.hostType]);

	// A resource ID only has to be unique within its placement scope: the selected
	// resource group, or the standalone section. The same ID in another group is fine.
	const getExistingIdsForPlacement = React.useCallback(
		(targetType, resourceGroup) => {
			const scopes = existingHostIdScopes || { groups: {}, standalone: [] };
			let ids = [];
			if (targetType === "group") {
				ids = scopes.groups?.[String(resourceGroup || "").trim()] || [];
			} else if (targetType === "standalone") {
				ids = scopes.standalone || [];
			}
			return new Set(ids.map((id) => String(id).trim()).filter(Boolean));
		},
		[existingHostIdScopes],
	);

	const buildUniqueHostId = React.useCallback(
		(rawHostName, { targetType, resourceGroup }) => {
			const hostNames = getHostNames(rawHostName);
			const base =
				hostNames.length > 1 ? "multi_resource" : hostNames[0] || String(rawHostName || "").trim();
			if (!base) {
				return "";
			}
			const existing = getExistingIdsForPlacement(targetType, resourceGroup);
			if (!existing.has(base)) {
				return base;
			}
			let i = 1;
			while (existing.has(`${base}_${i}`)) {
				i += 1;
			}
			return `${base}_${i}`;
		},
		[getExistingIdsForPlacement],
	);

	/**
	 * Regenerates the auto resource ID (and the "already exists" hint) against the
	 * given placement. Used on hostname edits and on placement changes.
	 */
	const applyGeneratedHostId = React.useCallback(
		(patch, hostName, placement) => {
			const generatedHostId = buildUniqueHostId(hostName, placement);
			const hostNames = getHostNames(hostName);
			const baseHostId =
				hostNames.length > 1 ? "multi_resource" : hostNames[0] || String(hostName || "").trim();
			const suffixed = Boolean(
				baseHostId &&
				generatedHostId &&
				generatedHostId !== baseHostId &&
				generatedHostId.startsWith(`${baseHostId}_`),
			);
			setAutoSuffixMessage(
				suffixed
					? `Resource ID "${baseHostId}" already exists in this resource group. Using "${generatedHostId}" instead.`
					: "",
			);
			onChange({ ...patch, hostId: generatedHostId });
		},
		[buildUniqueHostId, onChange],
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
							applyGeneratedHostId({ hostName }, hostName, {
								targetType: values.targetType,
								resourceGroup: values.resourceGroup,
							});
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
									: "Unique key within the selected resource group")
							}
						/>
						<ResourceGroupPlacementCombobox
							labelsAbove
							resourceGroups={resourceGroups}
							targetType={values.targetType}
							resourceGroup={values.resourceGroup}
							onChange={(patch) => {
								// Moving the resource to another scope changes which IDs are
								// taken: recompute the auto-generated ID against the new scope.
								if (values._editMode || manualHostIdRef.current || !values.hostName) {
									onChange(patch);
									return;
								}
								applyGeneratedHostId(patch, values.hostName, {
									targetType: patch.targetType ?? values.targetType,
									resourceGroup: patch.resourceGroup ?? values.resourceGroup,
								});
							}}
							onCreateResourceGroup={onCreateResourceGroup}
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
							id="host-config-host-type"
							label={HOST_TYPE_UI.fieldLabel}
							attributeName={HOST_TYPE_UI.attributeName}
							required
							error={Boolean(errors.hostType)}
							helperText={errors.hostType || HOST_TYPE_UI.fieldHelper}
							selectProps={{
								value: values.hostType || HOST_TYPE_UNSELECTED,
								sx: hostTypeSelectSx,
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
							{hostTypeOptions.map((t) => (
								<MenuItem key={t} value={t} sx={{ py: 1.5 }}>
									<HostTypeOption hostType={t} />
								</MenuItem>
							))}
						</LabeledSelect>
					</Box>
					<ProtocolSelectionPanel
						hostType={values.hostType}
						value={values.selectedProtocols || []}
						onChange={(selectedProtocols) => onChange({ selectedProtocols })}
						error={Boolean(errors.selectedProtocols)}
						helperText={errors.selectedProtocols}
					/>
					<ResourceAdvancedOptionsSection
						values={values.resourceAdvanced || createEmptyResourceAdvancedState()}
						onChange={onChange}
						inheritedDefaults={inheritedDefaults}
						inheritLabel={
							values.targetType === "group"
								? "Apply advanced options from resource group"
								: "Apply advanced options from the Agent"
						}
					/>
				</Stack>
			</FormSection>
		</Stack>
	);
};

export default HostConfigBasicsSection;
