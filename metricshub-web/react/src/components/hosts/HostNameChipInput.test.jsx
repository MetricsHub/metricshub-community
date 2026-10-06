import * as React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import HostNameChipInput from "./HostNameChipInput";

describe("Use agent hostname", () => {
	it.each(["ec-win", "localhost"])("fills the field with %s", async (hostname) => {
		const resolve = vi.fn().mockResolvedValue(hostname);
		const Form = () => {
			const [value, setValue] = React.useState("");
			return (
				<HostNameChipInput
					staticLabel
					value={value}
					onChange={setValue}
					onResolveHostname={resolve}
				/>
			);
		};
		render(<Form />);
		fireEvent.click(screen.getByRole("button", { name: "Use agent hostname" }));
		await waitFor(() => expect(screen.getByRole("combobox")).toHaveValue(hostname));
		expect(resolve).toHaveBeenCalledTimes(1);
	});
	it("shows a validation error for an unresolved expression", () => {
		render(
			<HostNameChipInput staticLabel value="${env::COMPUTERNAME:-localhost}" onChange={vi.fn()} />,
		);
		expect(screen.getByRole("combobox")).toHaveAttribute("aria-invalid", "true");
		expect(screen.getByText(/Environment expressions are not supported/)).toBeInTheDocument();
	});
	it("prevents duplicate requests while resolving", () => {
		render(
			<HostNameChipInput
				staticLabel
				value="ec-win"
				onChange={vi.fn()}
				onResolveHostname={vi.fn()}
				resolvingHostname
			/>,
		);
		expect(screen.getByRole("button", { name: /Use agent hostname/ })).toBeDisabled();
		expect(screen.getByRole("combobox")).toBeDisabled();
	});
	it("keeps the current hostname when a request fails", async () => {
		const onChange = vi.fn();
		const resolve = vi.fn().mockResolvedValue(null);
		render(
			<HostNameChipInput
				staticLabel
				value="ec-win"
				onChange={onChange}
				onResolveHostname={resolve}
				resolveHostnameError="Unable to fetch the agent hostname."
			/>,
		);
		fireEvent.click(screen.getByRole("button", { name: "Use agent hostname" }));
		await waitFor(() => expect(resolve).toHaveBeenCalledTimes(1));
		expect(onChange).not.toHaveBeenCalled();
		expect(screen.getByRole("combobox")).toHaveValue("ec-win");
		expect(screen.getByText("Unable to fetch the agent hostname.")).toBeInTheDocument();
	});
});
