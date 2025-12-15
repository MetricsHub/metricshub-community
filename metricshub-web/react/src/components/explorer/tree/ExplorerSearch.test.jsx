import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ExplorerSearch from "./ExplorerSearch";
import * as StoreHooks from "../../../hooks/store";
import * as ReactRouterDom from "react-router-dom";
import * as ExplorerSlice from "../../../store/slices/explorer-slice";

// Mocks
vi.mock("../../../hooks/store", () => ({
	useAppDispatch: vi.fn(),
	useAppSelector: vi.fn(),
}));

vi.mock("react-router-dom", () => ({
	useNavigate: vi.fn(),
}));

vi.mock("../../../store/thunks/explorer-thunks", () => ({
	searchExplorer: vi.fn(),
}));

vi.mock("../../../store/slices/explorer-slice", () => ({
	selectSearchResults: { name: "selectSearchResults" }, // Mock selector identity
	selectSearchLoading: { name: "selectSearchLoading" },
	clearSearchResults: vi.fn(),
}));

describe("ExplorerSearch", () => {
	const mockDispatch = vi.fn();
	const mockNavigate = vi.fn();

	beforeEach(() => {
		vi.clearAllMocks();
		vi.useFakeTimers();
		vi.mocked(StoreHooks.useAppDispatch).mockReturnValue(mockDispatch);
		vi.mocked(ReactRouterDom.useNavigate).mockReturnValue(mockNavigate);

		// Default selector behavior
		vi.mocked(StoreHooks.useAppSelector).mockImplementation((selector) => {
			if (selector === ExplorerSlice.selectSearchResults) return [];
			if (selector === ExplorerSlice.selectSearchLoading) return false;
			return undefined;
		});
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it("should render search input", () => {
		render(<ExplorerSearch />);
		expect(screen.getByLabelText("Search...")).toBeInTheDocument();
	});

	it("should clear results if input is less than 2 chars", () => {
		render(<ExplorerSearch />);

		const input = screen.getByLabelText("Search...");
		fireEvent.change(input, { target: { value: "a" } });

		expect(ExplorerSlice.clearSearchResults).toHaveBeenCalled();
		expect(mockDispatch).toHaveBeenCalled();
	});

	it("should navigate to the path provided in the search result", () => {
		const mockOption = {
			name: "Group1",
			type: "resource_group",
			path: "/explorer/resource-groups/Group1",
		};
		vi.mocked(StoreHooks.useAppSelector).mockImplementation((selector) => {
			if (selector === ExplorerSlice.selectSearchResults) return [mockOption];
			return undefined;
		});

		render(<ExplorerSearch />);

		const input = screen.getByLabelText("Search...");
		fireEvent.change(input, { target: { value: "Group1" } });
		fireEvent.click(input);

		const option = screen.getByText("Group1");
		fireEvent.click(option);

		expect(mockNavigate).toHaveBeenCalledWith("/explorer/resource-groups/Group1");
	});
});
