import { describe, expect, it } from "vitest";
import {
	explorerResourceUrls,
	getExplorerResourceLinks,
	getExplorerResourcePaths,
} from "./host-explorer-links";

describe("getExplorerResourceLinks", () => {
	it("returns a grouped single-host explorer link", () => {
		expect(
			getExplorerResourceLinks({
				resourceGroup: "Elyes",
				hostId: "Carnapp",
				hostConfig: { attributes: { "host.name": "carnapp.example" } },
			}),
		).toEqual([
			{
				label: "Carnapp",
				resourceId: "Carnapp",
				path: "/explorer/resource-groups/Elyes/resources/Carnapp",
			},
		]);
	});

	it("returns derived links for multi-host resources", () => {
		expect(
			getExplorerResourceLinks({
				resourceGroup: "MetricsHub_Hosts",
				hostId: "linux_metricshub_hosts",
				hostConfig: {
					attributes: {
						"host.name": ["deb-arm", "ec-deb-01", "ec-rocky-01", "rocky-arm"],
					},
				},
			}),
		).toEqual([
			{
				label: "deb-arm",
				resourceId: "linux_metricshub_hosts-1-deb-arm",
				path: "/explorer/resource-groups/MetricsHub_Hosts/resources/linux_metricshub_hosts-1-deb-arm",
			},
			{
				label: "ec-deb-01",
				resourceId: "linux_metricshub_hosts-2-ec-deb-01",
				path: "/explorer/resource-groups/MetricsHub_Hosts/resources/linux_metricshub_hosts-2-ec-deb-01",
			},
			{
				label: "ec-rocky-01",
				resourceId: "linux_metricshub_hosts-3-ec-rocky-01",
				path: "/explorer/resource-groups/MetricsHub_Hosts/resources/linux_metricshub_hosts-3-ec-rocky-01",
			},
			{
				label: "rocky-arm",
				resourceId: "linux_metricshub_hosts-4-rocky-arm",
				path: "/explorer/resource-groups/MetricsHub_Hosts/resources/linux_metricshub_hosts-4-rocky-arm",
			},
		]);
	});
});

describe("getExplorerResourcePaths", () => {
	it("returns paths from explorer links", () => {
		expect(
			getExplorerResourcePaths({
				resourceGroup: null,
				hostId: "my-host",
				hostConfig: { attributes: { "host.name": "my-host" } },
			}),
		).toEqual(["/explorer/resources/my-host"]);
	});
});

describe("explorerResourceUrls", () => {
	it("prefixes paths with the origin", () => {
		expect(explorerResourceUrls(["/explorer/resources/a"], "https://localhost:31888")).toEqual([
			"https://localhost:31888/explorer/resources/a",
		]);
	});
});
