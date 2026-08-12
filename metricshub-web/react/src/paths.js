export const paths = {
	agent: "/agent",
	agentConfig: "/agent/config",
	explorer: "/explorer",
	explorerWelcome: "/explorer/welcome",
	explorerResourceGroup: (name) => `/explorer/resource-groups/${encodeURIComponent(name)}`,
	explorerResource: (group, name) =>
		group
			? `/explorer/resource-groups/${encodeURIComponent(group)}/resources/${encodeURIComponent(name)}`
			: `/explorer/resources/${encodeURIComponent(name)}`,
	explorerMonitorType: (group, resource, connectorId, monitorType) =>
		group
			? `/explorer/resource-groups/${encodeURIComponent(group)}/resources/${encodeURIComponent(resource)}/connectors/${encodeURIComponent(connectorId)}/monitors/${encodeURIComponent(monitorType)}`
			: `/explorer/resources/${encodeURIComponent(resource)}/connectors/${encodeURIComponent(connectorId)}/monitors/${encodeURIComponent(monitorType)}`,
	configuration: "/configuration/yaml-editor",
	/** Monitoring config file: /configuration/yaml-editor/config/:name */
	configurationFile: (name) => `/configuration/yaml-editor/config/${encodeURIComponent(name)}`,
	/** OTEL config file: /configuration/yaml-editor/otel/:name */
	configurationOtelFile: (name) => `/configuration/yaml-editor/otel/${encodeURIComponent(name)}`,
	guidedConfig: "/configuration/guided-config",
	hosts: "/configuration/guided-config/resource-groups",
	hostsResourceGroups: () => "/configuration/guided-config/resource-groups",
	hostsResourceGroupNew: "/configuration/guided-config/resource-groups/new",
	hostsResourceNew: (resourceGroup) => {
		const base = "/configuration/guided-config/resources/new";
		if (resourceGroup === undefined) {
			return base;
		}
		return `${base}?resource-group=${encodeURIComponent(resourceGroup)}`;
	},
	/** Deep link to Hosts UI resource group detail (see HostsPage). */
	hostsResourceGroup: (name) =>
		`/configuration/guided-config/resource-groups/${encodeURIComponent(name)}`,
	hostsGroupedResource: (groupName, hostId) =>
		`/configuration/guided-config/resource-groups/${encodeURIComponent(groupName)}/resources/${encodeURIComponent(hostId)}`,
	hostsStandaloneSection: () => "/configuration/guided-config/no-resource-group",
	hostsStandaloneResource: (hostId) =>
		`/configuration/guided-config/no-resource-group/resources/${encodeURIComponent(hostId)}`,
	hostsGroupHostNew: (groupName) =>
		`/configuration/guided-config/resources/new?resource-group=${encodeURIComponent(groupName)}`,
	hostsGroupHostEdit: (groupName, hostId) =>
		`/configuration/guided-config/resource-groups/${encodeURIComponent(groupName)}/resources/${encodeURIComponent(hostId)}/edit`,
	hostsStandaloneHostNew: () =>
		"/configuration/guided-config/resources/new?resource-group=no-resource-group",
	hostsStandaloneHostEdit: (hostId) =>
		`/configuration/guided-config/no-resource-group/resources/${encodeURIComponent(hostId)}/edit`,
	chat: "/chat",
	login: "/login",
};
