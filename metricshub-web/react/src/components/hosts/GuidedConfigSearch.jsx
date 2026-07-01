import * as React from "react";
import { Autocomplete, Box, TextField, Typography } from "@mui/material";
import { useNavigate } from "react-router-dom";
import NodeTypeIcons from "../explorer/tree/icons/NodeTypeIcons";
import {
	flattenToRows,
	getGroupResources,
	getHostNames,
	getHostProtocolNames,
	isMultiHostConfig,
} from "./host-config-utils";
import { HOSTS_VIEWS } from "./hosts-navigation";
import { pathForView } from "./hosts-route-sync";
import { NO_RESOURCE_GROUP } from "./hosts-labels";
import { paths } from "../../paths";

const GLOBAL_SETTINGS_ENTRIES = [
	{ id: "job-duration", label: "Job duration" },
	{ id: "logging-level", label: "Logging level" },
	{ id: "license", label: "License" },
];

/**
 * Search option model used by Guided Config search.
 *
 * @typedef {object} GuidedConfigSearchOption
 * @property {string} id
 * @property {string} name
 * @property {string} kind
 * @property {"agent" | "resource-group" | "resource" | "multi-host-resource"} iconType
 * @property {string} breadcrumb
 * @property {string[]} tokens
 * @property {() => void} onSelect
 */

/**
 * @param {string} text
 * @returns {string}
 */
const normalize = (text) =>
	String(text || "")
		.trim()
		.toLowerCase();

/**
 * @param {Record<string, unknown>} snapshot
 * @param {(view: object) => void} onViewChange
 * @param {(to: string) => void} navigate
 * @returns {GuidedConfigSearchOption[]}
 */
const buildOptions = (snapshot, onViewChange, navigate) => {
	/** @type {GuidedConfigSearchOption[]} */
	const options = [];

	for (const setting of GLOBAL_SETTINGS_ENTRIES) {
		options.push({
			id: `global:${setting.id}`,
			name: setting.label,
			kind: "global setting",
			iconType: "agent",
			breadcrumb: "Resource Groups",
			tokens: [setting.label, setting.id, "global", "settings"],
			onSelect: () => {
				navigate(`${paths.agentConfig}#${encodeURIComponent(setting.id)}`);
			},
		});
	}

	const resourceGroups = snapshot?.resourceGroups || {};
	for (const [groupName, groupNode] of Object.entries(resourceGroups)) {
		const groupBreadcrumb = `Resource Groups > ${groupName}`;
		options.push({
			id: `group:${groupName}`,
			name: groupName,
			kind: "resource group",
			iconType: "resource-group",
			breadcrumb: groupBreadcrumb,
			tokens: [groupName, "group", "resource group"],
			onSelect: () => onViewChange(HOSTS_VIEWS.group(groupName)),
		});

		const resources = getGroupResources(groupNode);
		for (const [hostId, hostConfig] of Object.entries(resources)) {
			const hostNames = getHostNames(hostConfig?.attributes?.["host.name"]);
			const hostName = hostNames.join(", ");
			const hostType = String(hostConfig?.attributes?.["host.type"] || "").trim();
			const hostBreadcrumb = `${groupBreadcrumb} > ${hostId}`;
			const openHost = () => onViewChange(HOSTS_VIEWS.groupedHost(groupName, hostId));
			options.push({
				id: `host:${groupName}:${hostId}`,
				name: hostId,
				kind: "resource",
				iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
				breadcrumb: hostBreadcrumb,
				tokens: [
					hostId,
					hostName,
					...hostNames,
					hostType,
					"resource",
					"host",
					"multi-host",
					groupName,
				],
				onSelect: openHost,
			});

			if (hostType) {
				options.push({
					id: `host-type:${groupName}:${hostId}`,
					name: `${hostType} (${hostId})`,
					kind: "host type",
					iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
					breadcrumb: hostBreadcrumb,
					tokens: [hostType, "host.type", hostId, groupName],
					onSelect: openHost,
				});
			}

			for (const protocol of getHostProtocolNames(hostConfig)) {
				const protocolId = `${groupName}:${hostId}:${protocol}`;
				options.push({
					id: `protocol:${protocolId}`,
					name: `${protocol} (${hostId})`,
					kind: "protocol",
					iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
					breadcrumb: hostBreadcrumb,
					tokens: [protocol, hostId, groupName, "protocol"],
					onSelect: () => {
						openHost();
						navigate(
							`${pathForView(HOSTS_VIEWS.groupedHost(groupName, hostId))}#protocol-${protocol}`,
						);
					},
				});
			}

			for (const c of hostConfig?.connectors || []) {
				const connectorId = String(c).replace(/^\+/, "");
				if (!connectorId) {
					continue;
				}
				options.push({
					id: `connector:${groupName}:${hostId}:${connectorId}`,
					name: `${connectorId} (${hostId})`,
					kind: "connector",
					iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
					breadcrumb: hostBreadcrumb,
					tokens: [connectorId, "connector", hostId, groupName],
					onSelect: () => {
						openHost();
						navigate(
							`${pathForView(HOSTS_VIEWS.groupedHost(groupName, hostId))}#connector-${connectorId}`,
						);
					},
				});
			}

			const additionalConnectors = Object.keys(hostConfig?.additionalConnectors || {});
			for (const connectorId of additionalConnectors) {
				options.push({
					id: `additional-connector:${groupName}:${hostId}:${connectorId}`,
					name: `${connectorId} (${hostId})`,
					kind: "connector variables",
					iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
					breadcrumb: hostBreadcrumb,
					tokens: [connectorId, "connector", "variables", hostId, groupName],
					onSelect: () => {
						openHost();
						navigate(
							`${pathForView(HOSTS_VIEWS.groupedHost(groupName, hostId))}#additional-connector-${connectorId}`,
						);
					},
				});
			}

			for (const row of flattenToRows(hostConfig?.attributes)) {
				options.push({
					id: `cfg-attr:${groupName}:${hostId}:${row.property}`,
					name: `${row.property} (${hostId})`,
					kind: "configuration",
					iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
					breadcrumb: hostBreadcrumb,
					tokens: [
						row.property,
						row.value,
						hostId,
						hostName,
						...hostNames,
						hostType,
						groupName,
						"configuration",
					],
					onSelect: () => {
						openHost();
						navigate(
							`${pathForView(HOSTS_VIEWS.groupedHost(groupName, hostId))}#cfg-${encodeURIComponent(row.property)}`,
						);
					},
				});
			}
		}
	}

	const standaloneResources = snapshot?.resources || {};
	for (const [hostId, hostConfig] of Object.entries(standaloneResources)) {
		const hostNames = getHostNames(hostConfig?.attributes?.["host.name"]);
		const hostName = hostNames.join(", ");
		const hostType = String(hostConfig?.attributes?.["host.type"] || "").trim();
		const breadcrumb = `Resource Groups > ${NO_RESOURCE_GROUP} > ${hostId}`;
		const openHost = () => onViewChange(HOSTS_VIEWS.standaloneHost(hostId));
		options.push({
			id: `host:standalone:${hostId}`,
			name: hostId,
			kind: "resource",
			iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
			breadcrumb,
			tokens: [
				hostId,
				hostName,
				...hostNames,
				hostType,
				"resource",
				"host",
				"multi-host",
				NO_RESOURCE_GROUP,
			],
			onSelect: openHost,
		});

		if (hostType) {
			options.push({
				id: `host-type:standalone:${hostId}`,
				name: `${hostType} (${hostId})`,
				kind: "host type",
				iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
				breadcrumb,
				tokens: [hostType, "host.type", hostId, NO_RESOURCE_GROUP],
				onSelect: openHost,
			});
		}

		for (const protocol of getHostProtocolNames(hostConfig)) {
			options.push({
				id: `protocol:standalone:${hostId}:${protocol}`,
				name: `${protocol} (${hostId})`,
				kind: "protocol",
				iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
				breadcrumb,
				tokens: [protocol, hostId, "protocol", NO_RESOURCE_GROUP],
				onSelect: () => {
					openHost();
					navigate(`${pathForView(HOSTS_VIEWS.standaloneHost(hostId))}#protocol-${protocol}`);
				},
			});
		}

		for (const c of hostConfig?.connectors || []) {
			const connectorId = String(c).replace(/^\+/, "");
			if (!connectorId) {
				continue;
			}
			options.push({
				id: `connector:standalone:${hostId}:${connectorId}`,
				name: `${connectorId} (${hostId})`,
				kind: "connector",
				iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
				breadcrumb,
				tokens: [connectorId, "connector", hostId, NO_RESOURCE_GROUP],
				onSelect: () => {
					openHost();
					navigate(`${pathForView(HOSTS_VIEWS.standaloneHost(hostId))}#connector-${connectorId}`);
				},
			});
		}

		for (const row of flattenToRows(hostConfig?.attributes)) {
			options.push({
				id: `cfg-attr:standalone:${hostId}:${row.property}`,
				name: `${row.property} (${hostId})`,
				kind: "configuration",
				iconType: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
				breadcrumb,
				tokens: [
					row.property,
					row.value,
					hostId,
					hostName,
					...hostNames,
					hostType,
					NO_RESOURCE_GROUP,
					"configuration",
				],
				onSelect: () => {
					openHost();
					navigate(
						`${pathForView(HOSTS_VIEWS.standaloneHost(hostId))}#cfg-${encodeURIComponent(row.property)}`,
					);
				},
			});
		}
	}

	return options;
};

/**
 * Guided Config scoped search (resource groups/resources/protocols/connectors/config/global settings).
 *
 * @param {object} props
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot
 * @param {(view: object) => void} props.onViewChange
 */
const GuidedConfigSearch = ({ snapshot, onViewChange }) => {
	const navigate = useNavigate();
	const [open, setOpen] = React.useState(false);
	const [inputValue, setInputValue] = React.useState("");
	const [value, setValue] = React.useState(null);

	const allOptions = React.useMemo(
		() => buildOptions(snapshot, onViewChange, navigate),
		[snapshot, onViewChange, navigate],
	);

	const options = React.useMemo(() => {
		const q = normalize(inputValue);
		if (q.length < 2) {
			return [];
		}
		return allOptions
			.filter((option) => option.tokens.some((token) => normalize(token).includes(q)))
			.slice(0, 150);
	}, [allOptions, inputValue]);

	return (
		<Autocomplete
			noOptionsText={inputValue.length < 2 ? "Start typing to search..." : "No result"}
			open={open}
			onOpen={() => setOpen(true)}
			onClose={() => setOpen(false)}
			value={value}
			inputValue={inputValue}
			clearOnBlur={false}
			filterOptions={(x) => x}
			isOptionEqualToValue={(option, v) => option.id === v.id}
			getOptionLabel={(option) => option.name}
			options={options}
			onInputChange={(_event, newInputValue, reason) => {
				if (reason !== "reset") {
					setInputValue(newInputValue);
				}
			}}
			onChange={(_event, newValue) => {
				setValue(null);
				if (!newValue) {
					return;
				}
				newValue.onSelect();
			}}
			renderInput={(params) => <TextField {...params} label="Search..." size="small" />}
			renderOption={(props, option) => {
				const { key, ...otherProps } = props;
				return (
					<li key={key} {...otherProps}>
						<Box sx={{ display: "flex", alignItems: "center", gap: 1, width: "100%" }}>
							<NodeTypeIcons type={option.iconType} name={option.name} />
							<Box sx={{ minWidth: 0, flex: 1 }}>
								<Typography variant="body1" noWrap>
									{option.name}
								</Typography>
								<Typography variant="caption" color="text.secondary" noWrap display="block">
									{option.kind} {option.breadcrumb ? `• ${option.breadcrumb}` : ""}
								</Typography>
							</Box>
						</Box>
					</li>
				);
			}}
		/>
	);
};

export default React.memo(GuidedConfigSearch);
