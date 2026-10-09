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

describe("HostsResourcesTree hostname labels", () => {
	it.each(["standaloneHost", "groupedHost"])(
		"shows resolved hostnames but opens the original %s resource",
		(type) => {
			const resolvedId = "${env::COMPUTERNAME:-localhost}";
			const missingIds = ["${env::MISSING_A:-localhost}", "${env::MISSING_B:-localhost}"];
			const resources = {
				[resolvedId]: hostConfig("PC-ELYES"),
				[missingIds[0]]: hostConfig("localhost"),
				[missingIds[1]]: hostConfig("localhost"),
			};
			const group = type === "groupedHost" ? { groupName: "Paris" } : {};
			const snapshot =
				type === "groupedHost" ? { resourceGroups: { Paris: { resources } } } : { resources };
			const onViewChange = vi.fn();
			render(
				<HostsResourcesTree
					snapshot={snapshot}
					view={{ type, ...group, hostId: resolvedId }}
					onViewChange={onViewChange}
				/>,
			);

			expect(screen.queryByText(/\$\{env::/)).not.toBeInTheDocument();
			const resolvedLabel = screen.getByText("PC-ELYES");
			fireEvent.click(resolvedLabel);
			expect(onViewChange).toHaveBeenLastCalledWith({ type, ...group, hostId: resolvedId });

			// Two variables can both fall back to localhost without sharing a resource ID.
			const fallbackLabels = screen.getAllByText("localhost");
			expect(fallbackLabels).toHaveLength(2);
			fallbackLabels.forEach((label, index) => {
				fireEvent.click(label);
				expect(onViewChange).toHaveBeenLastCalledWith({
					type,
					...group,
					hostId: missingIds[index],
				});
			});
		},
	);

	it("uses resolved hostnames for read-only resources from other YAML files", () => {
		const onViewChange = vi.fn();
		render(
			<HostsResourcesTree
				snapshot={{
					resourceGroups: { Paris: { resources: {} } },
					externalResourceGroups: {
						Paris: { resources: { "${env::HOSTNAME:-localhost}": hostConfig("ec-linux") } },
						Remote: {
							resources: { "${env::COMPUTERNAME:-localhost}": hostConfig("ec-win") },
						},
					},
					externalResources: { "${env::MISSING:-localhost}": hostConfig("localhost") },
				}}
				view={{ type: "resourceGroups" }}
				onViewChange={onViewChange}
			/>,
		);

		expect(screen.queryByText(/\$\{env::/)).not.toBeInTheDocument();
		for (const hostname of ["ec-linux", "ec-win", "localhost"]) {
			fireEvent.click(screen.getByText(hostname));
		}
		expect(onViewChange).not.toHaveBeenCalled();
	});
});
