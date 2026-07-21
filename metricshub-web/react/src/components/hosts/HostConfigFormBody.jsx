import * as React from "react";
import { Box, Stack } from "@mui/material";
import HostConfigSectionContent from "./HostConfigSectionContent";
import HostConfigSectionHeader from "./HostConfigSectionHeader";
import { guidedConfigBorderedPanelSx } from "./guided-config-form-primitives";
import { hostConfigSectionId } from "./host-config-form-layout";
import { getFormSectionPageSubtitle } from "./host-config-sections";

/**
 * Configuration form sections with static titles (no accordion collapse).
 *
 * @param {object} props
 * @param {object} props.form return value of useHostConfig
 * @param {string[]} props.resourceGroups
 * @param {{ groups: Record<string, string[]>; standalone: string[] }} [props.existingHostIdScopes]
 * @param {Record<string, 0 | 1 | null>} [props.protocolHealth]
 * @param {() => void} [props.onScrollToConnectorsStep]
 */
const HostConfigFormBody = ({
	form,
	resourceGroups,
	existingHostIdScopes,
	protocolHealth,
	onScrollToConnectorsStep,
	onCreateResourceGroup,
}) => {
	const validatedSet = React.useMemo(
		() => new Set(form.validatedStepIds || []),
		[form.validatedStepIds],
	);
	const invalidSet = React.useMemo(() => new Set(form.invalidStepIds || []), [form.invalidStepIds]);
	const editedSet = React.useMemo(() => new Set(form.editedStepIds || []), [form.editedStepIds]);

	const renderStepPanel = (step, index) => {
		if (!step) {
			return null;
		}

		const content = (
			<HostConfigSectionContent
				step={step}
				form={form}
				resourceGroups={resourceGroups}
				existingHostIdScopes={existingHostIdScopes}
				showProtocolStepHeader={false}
				onScrollToConnectorsStep={onScrollToConnectorsStep}
				onCreateResourceGroup={onCreateResourceGroup}
			/>
		);

		if (!content) {
			return null;
		}

		const sectionId = hostConfigSectionId(step);
		const protocolUp =
			step.type === "protocol" && protocolHealth
				? (protocolHealth[step.protocolId] ?? null)
				: undefined;

		return (
			<Box key={step.id} id={sectionId} sx={{ scrollMarginTop: 88 }}>
				<Box component="section" sx={guidedConfigBorderedPanelSx}>
					<HostConfigSectionHeader
						step={step}
						stepNumber={index + 1}
						isCompleted={validatedSet.has(step.id) && !editedSet.has(step.id)}
						isInvalid={invalidSet.has(step.id)}
						isEdited={editedSet.has(step.id)}
						description={getFormSectionPageSubtitle(step) || undefined}
						protocolUp={protocolUp}
					/>
					{content}
				</Box>
			</Box>
		);
	};

	return <Stack spacing={4}>{form.steps.map((step, index) => renderStepPanel(step, index))}</Stack>;
};

export default HostConfigFormBody;
