import * as React from "react";
import HostConfigBasicsSection from "./HostConfigBasicsSection";
import HostConfigConnectorsSection from "./HostConfigConnectorsSection";
import HostProtocolConfigStep from "./HostProtocolConfigStep";

/**
 * Renders a single form step panel (basics, protocol, or connectors).
 *
 * @param {object} props
 * @param {import("./host-config-sections").FormSectionDescriptor | undefined} props.step
 * @param {object} props.form return value of useHostConfig
 * @param {string[]} props.resourceGroups
 * @param {{ groups: Record<string, string[]>; standalone: string[] }} [props.existingHostIdScopes]
 * @param {boolean} [props.showProtocolStepHeader] show protocol title inside the step (create form)
 * @param {() => void} [props.onScrollToConnectorsStep]
 */
const HostConfigSectionContent = ({
	step,
	form,
	resourceGroups,
	existingHostIdScopes,
	showProtocolStepHeader = false,
	onScrollToConnectorsStep,
}) => {
	if (!step) {
		return null;
	}

	switch (step.type) {
		case "basics":
			return (
				<HostConfigBasicsSection
					values={form.state}
					onChange={form.patchState}
					errors={form.errors}
					resourceGroups={resourceGroups}
					existingHostIdScopes={existingHostIdScopes}
				/>
			);
		case "protocol":
			return (
				<HostProtocolConfigStep
					protocol={step.protocolId}
					values={form.state.protocols?.[step.protocolId] || {}}
					onChange={(name, value) => form.patchProtocolField(step.protocolId, name, value)}
					errors={form.errors}
					hostId={form.state.hostId}
					hostName={form.state.hostName}
					allowPasswordReveal={form.allowPasswordReveal}
					deferEncryptUntilSave={form.deferEncryptUntilSave}
					showHeader={showProtocolStepHeader}
					borderedContainer={false}
				/>
			);
		case "connectors":
			return (
				<HostConfigConnectorsSection
					selectedDirectives={form.state.connectors}
					onChange={(connectors) => form.patchState({ connectors })}
					detectionMode={form.state.connectorDetectionMode}
					onDetectionModeChange={(connectorDetectionMode) =>
						form.patchState({ connectorDetectionMode })
					}
					onScrollToConnectorsStep={onScrollToConnectorsStep}
					connectorError={form.errors._connectors || form.errors._connectorVariables}
					hostType={form.state.hostType}
					protocols={form.orderedProtocols}
					catalog={form.connectorCatalog}
					annotatedCatalog={form.annotatedConnectorCatalog}
					catalogLoading={form.connectorCatalogLoading}
					catalogError={form.connectorCatalogError}
					additionalConnectors={form.state.additionalConnectors}
					onConnectorsStatePatch={(patch) => form.patchState(patch)}
					onAdditionalConnectorsChange={(patch) =>
						form.patchState(form.applyAdditionalConnectorsPatch(patch))
					}
					configurationValidationAttempt={form.connectorVariablesValidationAttempt}
					configurationDialogErrors={form.errors}
					highlightConfigurationId={form.highlightConnectorInstanceId}
					onHighlightConfigurationIdConsumed={form.clearHighlightConnectorInstance}
				/>
			);
		default:
			return null;
	}
};

export default HostConfigSectionContent;
