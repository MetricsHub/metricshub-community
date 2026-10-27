import * as React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material";
import { logFilesApi } from "../../api/logs";
import { logFilesReducer } from "../../store/slices/log-files-slice";
import { createTheme } from "../../theme";
import LogFilesViewer from "./LogFilesViewer";

const file = {
	name: "metricshub-agent-global.log",
	size: 1024,
	lastModificationTime: "2026-10-26T12:00:00Z",
};

const renderViewer = () => {
	const store = configureStore({ reducer: { logFiles: logFilesReducer } });
	render(
		<Provider store={store}>
			<ThemeProvider theme={createTheme({ paletteMode: "dark" })}>
				<LogFilesViewer />
			</ThemeProvider>
		</Provider>,
	);
};

beforeEach(() => {
	vi.spyOn(logFilesApi, "list").mockResolvedValue([file]);
	vi.spyOn(logFilesApi, "getTail").mockResolvedValue("Latest log content");
	vi.spyOn(logFilesApi, "deleteFile").mockResolvedValue();
	vi.spyOn(logFilesApi, "deleteAllFiles").mockResolvedValue(1);
});

afterEach(() => vi.restoreAllMocks());

// The real DataGrid can take over 12 seconds to mount and load under the parallel suite.
describe("log file errors", { timeout: 20_000 }, () => {
	it("shows a content error and clears it after a successful refresh", async () => {
		logFilesApi.getTail.mockRejectedValueOnce(new Error("Log content is unavailable"));
		renderViewer();
		const error = await screen.findByText("Log content is unavailable");
		expect(error.closest('[role="alert"]')).not.toBeNull();

		fireEvent.click(screen.getByLabelText("Refresh logs", { selector: "button" }));
		expect(await screen.findByText("Latest log content")).toBeInTheDocument();
		expect(screen.queryByText("Log content is unavailable")).not.toBeInTheDocument();
	});

	it.each([false, true])("shows a failed deletion and keeps the file (all: %s)", async (all) => {
		const deleteApi = all ? logFilesApi.deleteAllFiles : logFilesApi.deleteFile;
		deleteApi.mockRejectedValueOnce(new Error("Log deletion is unavailable"));
		renderViewer();
		await screen.findByText("Latest log content");
		fireEvent.click(
			all
				? screen.getByText("Delete All", { selector: "button" })
				: screen.getByLabelText(`Delete ${file.name}`),
		);
		const title = await screen.findByText(all ? "Delete All Log Files" : "Delete Log File");
		const dialog = title.closest('[role="dialog"]');
		fireEvent.click(
			within(dialog).getByText(all ? "Delete All" : "Delete", { selector: "button" }),
		);
		const error = await screen.findByText("Log deletion is unavailable");
		expect(error.closest('[role="alert"]')).not.toBeNull();
		await waitFor(() => expect(dialog).not.toBeInTheDocument());
		expect(screen.getByLabelText(`Delete ${file.name}`)).toBeEnabled();
		expect(screen.getByText("Latest log content")).toBeInTheDocument();
		if (all) {
			expect(deleteApi).toHaveBeenCalledWith();
		} else {
			expect(deleteApi).toHaveBeenCalledWith(file.name);
		}
	});

	it.each([false, true])(
		"clears the content error after deleting the displayed log (all: %s)",
		async (all) => {
			renderViewer();
			await screen.findByText("Latest log content");
			logFilesApi.getTail.mockRejectedValueOnce(new Error("Log content is unavailable"));
			fireEvent.click(screen.getByLabelText("Refresh logs", { selector: "button" }));
			expect(await screen.findByText("Log content is unavailable")).toBeInTheDocument();

			fireEvent.click(
				all
					? screen.getByText("Delete All", { selector: "button" })
					: screen.getByLabelText(`Delete ${file.name}`),
			);
			const title = await screen.findByText(all ? "Delete All Log Files" : "Delete Log File");
			const dialog = title.closest('[role="dialog"]');
			fireEvent.click(
				within(dialog).getByText(all ? "Delete All" : "Delete", { selector: "button" }),
			);
			await screen.findByText("No log file selected");
			expect(screen.queryByText("Log content is unavailable")).not.toBeInTheDocument();
			expect(screen.queryByText("Latest log content")).not.toBeInTheDocument();
		},
	);
});
