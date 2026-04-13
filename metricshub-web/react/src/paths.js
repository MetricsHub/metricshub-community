export const paths = {
	agent: "/agent",
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
	configuration: "/configuration",
	/** Monitoring config file: /configuration/config/:name */
	configurationFile: (name) => `/configuration/config/${encodeURIComponent(name)}`,
	/** OTEL config file: /configuration/otel/:name */
	configurationOtelFile: (name) => `/configuration/otel/${encodeURIComponent(name)}`,
	chat: "/chat",
	login: "/login",
};
