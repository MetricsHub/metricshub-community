import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ExplorerSearch from "./ExplorerSearch";
import * as StoreHooks from "../../../hooks/store";
import * as ReactRouterDom from "react-router-dom";
import * as ExplorerThunks from "../../../store/thunks/explorer-thunks";
import * as ExplorerSlice from "../../../store/slices/explorer-slice";
import { paths } from "../../../paths";

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

// Mock paths
vi.mock("../../../paths", () => ({
	paths: {
		explorerResourceGroup: vi.fn((name) => `/group/${name}`),
		explorerResource: vi.fn((group, name) => `/resource/${group}/${name}`),
		explorerMonitorType: vi.fn((group, resource, type) => `/monitor/${group}/${resource}/${type}`),
	},
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

	it("should navigate correctly for resource group", () => {
		const mockOptions = [{ name: "Group1", type: "resource_group", path: "Agent/Group1" }];
		vi.mocked(StoreHooks.useAppSelector).mockImplementation((selector) => {
			if (selector === ExplorerSlice.selectSearchResults) return mockOptions;
			return undefined;
		});

		render(<ExplorerSearch />);

		const input = screen.getByLabelText("Search...");
		fireEvent.change(input, { target: { value: "Group1" } });
		fireEvent.click(input); // Open dropdown

		// Simulate selection (Autocomplete is tricky to test with fireEvent, usually need to find option)
		// We can simulate the onChange handler directly if we could access it, but here we rely on UI interaction
		// MUI Autocomplete renders options in a portal usually.

		// Let's try to find the option by text
		const option = screen.getByText("Group1");
		fireEvent.click(option);

		expect(paths.explorerResourceGroup).toHaveBeenCalledWith("Group1");
		expect(mockNavigate).toHaveBeenCalledWith("/group/Group1");
	});

	it("should navigate correctly for instance with hash", () => {
		// Note: The component parses the path if groupName/resourceName are missing.
		// Let's test the parsing logic by providing a raw path.
		const mockOptionRaw = {
			name: "cpu 0",
			type: "instance",
			path: "Agent/localhost/Windows/cpu/cpu 0",
		};

		vi.mocked(StoreHooks.useAppSelector).mockImplementation((selector) => {
			if (selector === ExplorerSlice.selectSearchResults) return [mockOptionRaw];
			return undefined;
		});

		render(<ExplorerSearch />);

		const input = screen.getByLabelText("Search...");
		fireEvent.change(input, { target: { value: "cpu" } });
		fireEvent.click(input);

		const option = screen.getByText("cpu 0");
		fireEvent.click(option);

		// Path parsing logic:
		// prefix = Agent/localhost/Windows/cpu/
		// parts = [Agent, localhost, Windows, cpu]
		// len = 4
		// resourceName = localhost
		// name (monitorType) = cpu

		expect(paths.explorerMonitorType).toHaveBeenCalledWith(undefined, "localhost", "cpu");
		expect(mockNavigate).toHaveBeenCalledWith("/monitor/undefined/localhost/cpu#cpu 0");
	});

	it("should navigate correctly for connector with hash", () => {
		const mockOption = {
			name: "Windows",
			type: "connector",
			path: "Agent/localhost/Windows",
		};

		vi.mocked(StoreHooks.useAppSelector).mockImplementation((selector) => {
			if (selector === ExplorerSlice.selectSearchResults) return [mockOption];
			return undefined;
		});

		render(<ExplorerSearch />);

		const input = screen.getByLabelText("Search...");
		fireEvent.change(input, { target: { value: "Win" } });
		fireEvent.click(input);

		const option = screen.getByText("Windows");
		fireEvent.click(option);

		// Path parsing:
		// prefix = Agent/localhost/
		// parts = [Agent, localhost]
		// len = 2
		// resourceName = localhost

		expect(paths.explorerResource).toHaveBeenCalledWith(undefined, "localhost");
		expect(mockNavigate).toHaveBeenCalledWith("/resource/undefined/localhost#Windows");
	});
});
