import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import HostsResourcesTree from "./HostsResourcesTree";

const hostConfig = (hostname) => ({ attributes: { "host.name": hostname } });

// Keep the real tree labels and click handler, but avoid MUI's nested DOM queries in Happy DOM.
vi.mock("@mui/x-tree-view", () => ({
	treeItemClasses: { iconContainer: "tree-icon", content: "tree-content", label: "tree-label" },
	SimpleTreeView: ({ children, onItemClick }) => (
		<div
			onClick={(event) => {
				const item = event.target.closest("[data-item-id]");
				if (item) onItemClick(event, item.dataset.itemId);
			}}
		>
			{children}
		</div>
	),
	TreeItem: ({ itemId, label, children }) => (
		<div data-item-id={itemId}>
			{label}
			{children}
		</div>
	),
}));

describe("HostsResourcesTree resource ID labels", () => {
	it.each(["standaloneHost", "groupedHost"])(
		"uses resource IDs for %s labels and navigation",
		(type) => {
			const resources = {
				"local-resource": hostConfig("PC-ELYES"),
				"backup-a": hostConfig("localhost"),
				"backup-b": hostConfig("localhost"),
			};
			const group = type === "groupedHost" ? { groupName: "Paris" } : {};
			const snapshot =
				type === "groupedHost" ? { resourceGroups: { Paris: { resources } } } : { resources };
			const onViewChange = vi.fn();
			render(
				<HostsResourcesTree
					snapshot={snapshot}
					view={{ type, ...group, hostId: "local-resource" }}
					onViewChange={onViewChange}
				/>,
			);

			expect(screen.queryByText("PC-ELYES")).not.toBeInTheDocument();
			expect(screen.queryByText("localhost")).not.toBeInTheDocument();
			for (const hostId of Object.keys(resources)) {
				fireEvent.click(screen.getByText(hostId));
				expect(onViewChange).toHaveBeenLastCalledWith({ type, ...group, hostId });
			}
		},
	);

	it("uses resource IDs for read-only resources from other YAML files", () => {
		const onViewChange = vi.fn();
		render(
			<HostsResourcesTree
				snapshot={{
					resourceGroups: { Paris: { resources: {} } },
					externalResourceGroups: {
						Paris: { resources: { "linux-resource": hostConfig("ec-linux") } },
						Remote: {
							resources: { "windows-resource": hostConfig("ec-win") },
						},
					},
					externalResources: { "external-local": hostConfig("localhost") },
				}}
				view={{ type: "resourceGroups" }}
				onViewChange={onViewChange}
			/>,
		);

		for (const hostname of ["ec-linux", "ec-win", "localhost"]) {
			expect(screen.queryByText(hostname)).not.toBeInTheDocument();
		}
		for (const hostId of ["linux-resource", "windows-resource", "external-local"]) {
			fireEvent.click(screen.getByText(hostId));
		}
		expect(onViewChange).not.toHaveBeenCalled();
	});
});
