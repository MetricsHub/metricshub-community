import * as React from "react";
import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ProtocolHostnameOverridesTable from "./ProtocolHostnameOverridesTable";

/** Controlled wrapper mirroring how ProtocolConfigForm drives the table. */
const Harness = ({ hostNames, initialValue = [] }) => {
	const [value, setValue] = React.useState(initialValue);
	return (
		<ProtocolHostnameOverridesTable
			hostNames={hostNames}
			value={value}
			onChange={setValue}
			label="Hostname"
		/>
	);
};

describe("ProtocolHostnameOverridesTable", () => {
	it("keeps the typed text when it passes through the row's own host.name entry", () => {
		render(<Harness hostNames={["host1", "host2"]} />);
		const input = screen.getByLabelText("Hostname override for host1");

		// Typing "host1" makes the override equal to the host entry: the derived
		// slot collapses to blank, but the focused cell must keep the raw text so
		// the next keystroke can extend it to "host12".
		fireEvent.focus(input);
		fireEvent.change(input, { target: { value: "host1" } });
		expect(input).toHaveValue("host1");

		fireEvent.change(input, { target: { value: "host12" } });
		expect(input).toHaveValue("host12");
	});

	it("shows the host.name entry as a blank cell once the row loses focus", () => {
		render(<Harness hostNames={["host1", "host2"]} />);
		const input = screen.getByLabelText("Hostname override for host1");

		fireEvent.focus(input);
		fireEvent.change(input, { target: { value: "host1" } });
		fireEvent.blur(input);

		expect(input).toHaveValue("");
		expect(input).toHaveAttribute("placeholder", "host1");
	});

	it("renders a saved override and lets it be edited from its stored value", () => {
		render(<Harness hostNames={["host1", "host2"]} initialValue={["proxy1", "host2"]} />);
		const input = screen.getByLabelText("Hostname override for host1");
		expect(input).toHaveValue("proxy1");

		fireEvent.focus(input);
		fireEvent.change(input, { target: { value: "proxy12" } });
		expect(input).toHaveValue("proxy12");
	});
});
