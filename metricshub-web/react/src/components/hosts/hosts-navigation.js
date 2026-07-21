/** @typedef {'resourceGroups' | 'standalone' | 'group' | 'groupedHost' | 'standaloneHost'} HostsViewType */

/**
 * @param {HostsViewType} type
 * @param {object} [params]
 * @returns {object}
 */
export const hostsView = (type, params = {}) => ({ type, ...params });

export const HOSTS_VIEWS = {
	/** Default landing page for guided-config: empty resource-groups overview. */
	resourceGroups: () => hostsView("resourceGroups"),
	standalone: () => hostsView("standalone"),
	group: (groupName) => hostsView("group", { groupName }),
	groupedHost: (groupName, hostId) => hostsView("groupedHost", { groupName, hostId }),
	standaloneHost: (hostId) => hostsView("standaloneHost", { hostId }),
};

/**
 * Resource group to pre-select when opening "+ New resource" from the current browse view.
 *
 * @param {ReturnType<typeof HOSTS_VIEWS.resourceGroups>} view
 * @returns {string | null | undefined}
 */
export const groupNameForCreateHost = (view) => {
	if (view?.type === "group" || view?.type === "groupedHost") {
		return view.groupName;
	}
	if (view?.type === "standalone" || view?.type === "standaloneHost") {
		return "__none__";
	}
	return undefined;
};
