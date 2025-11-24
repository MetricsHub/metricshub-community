export const paths = {
	explorer: "/explorer",
	explorerWelcome: "/explorer/welcome",
	explorerResourceGroup: (name) => `/explorer/resource-groups/${encodeURIComponent(name)}`,
	explorerResource: (name) => `/explorer/resources/${encodeURIComponent(name)}`,
	explorerGroupResource: (groupName, resourceName) =>
		`/explorer/resource-groups/${encodeURIComponent(groupName)}/resources/${encodeURIComponent(resourceName)}`,
	configuration: "/configuration",
	configurationFile: (name) => `/configuration/${encodeURIComponent(name)}`,
	login: "/login",
};
