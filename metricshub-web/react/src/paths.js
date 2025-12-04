export const paths = {
	explorer: "/explorer",
	explorerWelcome: "/explorer/welcome",
	explorerResourceGroup: (name) => `/explorer/resource-groups/${encodeURIComponent(name)}`,
	explorerResource: (group, name) =>
		group
			? `/explorer/resource-groups/${encodeURIComponent(group)}/resources/${encodeURIComponent(name)}`
			: `/explorer/resources/${encodeURIComponent(name)}`,
	explorerMonitorType: (group, resource, monitorType) =>
		group
			? `/explorer/resource-groups/${encodeURIComponent(group)}/resources/${encodeURIComponent(resource)}/monitors/${encodeURIComponent(monitorType)}`
			: `/explorer/resources/${encodeURIComponent(resource)}/monitors/${encodeURIComponent(monitorType)}`,
	configuration: "/configuration",
	configurationFile: (name) => `/configuration/${encodeURIComponent(name)}`,
	login: "/login",
};
