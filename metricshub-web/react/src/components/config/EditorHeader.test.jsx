import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import EditorHeader from "./EditorHeader";
import * as StoreHooks from "../../hooks/store";

vi.mock("../../hooks/store", () => ({
	useAppSelector: vi.fn(),
}));

// Not under test here: keep the header free of their own dependencies.
vi.mock("../common/EncryptPasswordDialog", () => ({ default: () => null }));
vi.mock("./tree/icons/FileTypeIcons", () => ({ default: () => null }));

describe("EditorHeader Reevaluate button", () => {
	beforeEach(() => {
		vi.mocked(StoreHooks.useAppSelector).mockImplementation((selector) =>
			selector({ config: { dirtyByName: {}, filesByName: {} } }),
		);
	});

	it("is enabled on a saved template for a user who can write", () => {
		render(
			<EditorHeader selected="hosts.vm" saving={false} onSave={() => {}} onReevaluate={() => {}} />,
		);

		expect(screen.getByRole("button", { name: /reevaluate/i })).toBeEnabled();
	});

	it("is disabled for a read-only user, whose request the server would refuse", () => {
		render(
			<EditorHeader
				selected="hosts.vm"
				saving={false}
				onSave={() => {}}
				onReevaluate={() => {}}
				isReadOnly
			/>,
		);

		expect(screen.getByRole("button", { name: /reevaluate/i })).toBeDisabled();
	});

	it("is not shown on a draft, which the agent does not load", () => {
		render(
			<EditorHeader
				selected="hosts.vm.draft"
				saving={false}
				onSave={() => {}}
				onReevaluate={() => {}}
			/>,
		);

		expect(screen.queryByRole("button", { name: /reevaluate/i })).not.toBeInTheDocument();
	});
});
