import * as React from "react";
import { Box, Stack } from "@mui/material";
import HostWizardStepContent from "./HostWizardStepContent";
import HostWizardSectionHeader from "./HostWizardSectionHeader";
import { guidedConfigBorderedPanelSx } from "./guided-config-form-primitives";
import { hostConfigSectionId } from "./host-config-form-layout";
import { getWizardStepPageSubtitle } from "./host-wizard-steps";

/**
 * Configuration form sections with static titles (no accordion collapse).
 *
 * @param {object} props
 * @param {object} props.wizard return value of useHostWizard
 * @param {string[]} props.resourceGroups
 * @param {string[]} [props.existingHostIds]
 * @param {Record<string, 0 | 1 | null>} [props.protocolHealth]
 * @param {() => void} [props.onScrollToConnectorsStep]
 */
const HostConfigFormBody = ({
	wizard,
	resourceGroups,
	existingHostIds = [],
	protocolHealth,
	onScrollToConnectorsStep,
}) => {
	const validatedSet = React.useMemo(
		() => new Set(wizard.validatedStepIds || []),
		[wizard.validatedStepIds],
	);
	const invalidSet = React.useMemo(
		() => new Set(wizard.invalidStepIds || []),
		[wizard.invalidStepIds],
	);
	const editedSet = React.useMemo(
		() => new Set(wizard.editedStepIds || []),
		[wizard.editedStepIds],
	);

	const renderStepPanel = (step, index) => {
		if (!step) {
			return null;
		}

		const content = (
			<HostWizardStepContent
				step={step}
				wizard={wizard}
				resourceGroups={resourceGroups}
				existingHostIds={existingHostIds}
				showProtocolStepHeader={false}
				onScrollToConnectorsStep={onScrollToConnectorsStep}
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
					<HostWizardSectionHeader
						step={step}
						stepNumber={index + 1}
						isCompleted={validatedSet.has(step.id) && !editedSet.has(step.id)}
						isInvalid={invalidSet.has(step.id)}
						isEdited={editedSet.has(step.id)}
						description={getWizardStepPageSubtitle(step) || undefined}
						protocolUp={protocolUp}
					/>
					{content}
				</Box>
			</Box>
		);
	};

	return (
		<Stack spacing={4}>{wizard.steps.map((step, index) => renderStepPanel(step, index))}</Stack>
	);
};

export default HostConfigFormBody;
