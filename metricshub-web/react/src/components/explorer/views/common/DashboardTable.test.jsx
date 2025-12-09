import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TableBody, TableRow, TableCell } from "@mui/material";
import DashboardTable from "./DashboardTable";

describe("DashboardTable", () => {
	it("renders children", () => {
		render(
			<DashboardTable>
				<TableBody>
					<TableRow>
						<TableCell>Test Cell</TableCell>
					</TableRow>
				</TableBody>
			</DashboardTable>,
		);
		expect(screen.getByText("Test Cell")).toBeInTheDocument();
	});
});
