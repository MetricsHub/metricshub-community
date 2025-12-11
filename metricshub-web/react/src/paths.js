export const paths = {
	explorer: "/explorer",
	explorerWelcome: "/explorer/welcome",
	explorerResourceGroup: (name) => `/explorer/resource-groups/${encodeURIComponent(name)}`,
	explorerResource: (group, name) =>
		group
			? `/explorer/resource-groups/${encodeURIComponent(group)}/resources/${encodeURIComponent(name)}`
			: `/explorer/resources/${encodeURIComponent(name)}`,
	configuration: "/configuration",
	configurationFile: (name) => `/configuration/${encodeURIComponent(name)}`,
	login: "/login",
};
