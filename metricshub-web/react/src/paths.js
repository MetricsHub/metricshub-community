export const paths = {
	explorer: "/explorer",
	explorerWelcome: "/explorer/welcome",
	explorerResourceGroup: (name) => `/explorer/resource-groups/${encodeURIComponent(name)}`,
	configuration: "/configuration",
	configurationFile: (name) => `/configuration/${encodeURIComponent(name)}`,
	login: "/login",
};
