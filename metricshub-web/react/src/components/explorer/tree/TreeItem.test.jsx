import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SimpleTreeView } from "@mui/x-tree-view";
import ExplorerTreeItem from "./TreeItem";

describe("ExplorerTreeItem", () => {
	const renderTree = (node) => {
		return render(
			<SimpleTreeView>
				<ExplorerTreeItem node={node} />
			</SimpleTreeView>,
		);
	};

	it("renders leaf node", () => {
		const node = { id: "1", name: "Leaf Node", isExpandable: false };
		renderTree(node);
		expect(screen.getByText("Leaf Node")).toBeInTheDocument();
	});

	it("renders folder node", () => {
		const node = { id: "1", name: "Folder Node", isExpandable: true };
		renderTree(node);
		expect(screen.getByText("Folder Node")).toBeInTheDocument();
	});
});
