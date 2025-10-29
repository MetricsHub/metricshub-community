export const paths = {
	explorer: "/explorer",
	configuration: "/configuration",
	configurationFile: (name) => `/configuration/${encodeURIComponent(name)}`,
	login: "/login",
};
