import * as React from "react";
import HostWizardBasicsStep from "./HostWizardBasicsStep";
import HostWizardConnectorsStep from "./HostWizardConnectorsStep";
import HostProtocolConfigStep from "./HostProtocolConfigStep";

/**
 * Renders a single wizard step panel (basics, protocol, or connectors).
 *
 * @param {object} props
 * @param {import("./host-wizard-steps").WizardStepDescriptor | undefined} props.step
 * @param {object} props.wizard return value of useHostWizard
 * @param {string[]} props.resourceGroups
 * @param {string[]} [props.existingHostIds]
 * @param {boolean} [props.showProtocolStepHeader] show protocol title inside the step (create wizard)
 * @param {() => void} [props.onScrollToConnectorsStep]
 */
const HostWizardStepContent = ({
	step,
	wizard,
	resourceGroups,
	existingHostIds = [],
	showProtocolStepHeader = false,
	onScrollToConnectorsStep,
}) => {
	if (!step) {
		return null;
	}

	switch (step.type) {
		case "basics":
			return (
				<HostWizardBasicsStep
					values={wizard.state}
					onChange={wizard.patchState}
					errors={wizard.errors}
					resourceGroups={resourceGroups}
					existingHostIds={existingHostIds}
				/>
			);
		case "protocol":
			return (
				<HostProtocolConfigStep
					protocol={step.protocolId}
					values={wizard.state.protocols?.[step.protocolId] || {}}
					onChange={(name, value) => wizard.patchProtocolField(step.protocolId, name, value)}
					errors={wizard.errors}
					hostId={wizard.state.hostId}
					hostName={wizard.state.hostName}
					allowPasswordReveal={wizard.allowPasswordReveal}
					deferEncryptUntilSave={wizard.deferEncryptUntilSave}
					showHeader={showProtocolStepHeader}
					borderedContainer={false}
				/>
			);
		case "connectors":
			return (
				<HostWizardConnectorsStep
					selectedDirectives={wizard.state.connectors}
					onChange={(connectors) => wizard.patchState({ connectors })}
					detectionMode={wizard.state.connectorDetectionMode}
					onDetectionModeChange={(connectorDetectionMode) =>
						wizard.patchState({ connectorDetectionMode })
					}
					onScrollToConnectorsStep={onScrollToConnectorsStep}
					connectorError={wizard.errors._connectors || wizard.errors._connectorVariables}
					hostType={wizard.state.hostType}
					protocols={wizard.orderedProtocols}
					catalog={wizard.connectorCatalog}
					annotatedCatalog={wizard.annotatedConnectorCatalog}
					catalogLoading={wizard.connectorCatalogLoading}
					catalogError={wizard.connectorCatalogError}
					additionalConnectors={wizard.state.additionalConnectors}
					onConnectorsStatePatch={(patch) => wizard.patchState(patch)}
					onAdditionalConnectorsChange={(patch) =>
						wizard.patchState(wizard.applyAdditionalConnectorsPatch(patch))
					}
					configurationValidationAttempt={wizard.connectorVariablesValidationAttempt}
					configurationDialogErrors={wizard.errors}
					highlightConfigurationId={wizard.highlightConnectorInstanceId}
					onHighlightConfigurationIdConsumed={wizard.clearHighlightConnectorInstance}
				/>
			);
		default:
			return null;
	}
};

export default HostWizardStepContent;
