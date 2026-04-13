/**
 * Test file for backup-service
 *
 * This demonstrates how to test service/utility functions.
 * Key concepts:
 * - Services contain business logic and may call APIs
 * - We mock dependencies (APIs, utilities) to isolate the service logic
 * - We test different scenarios: success, errors, edge cases
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { createBackupSet, restoreBackupFile } from "./backup-service";
import { configApi } from "../api/config";

// Mock dependencies
// We mock all external dependencies so we can control their behavior
// This allows us to test the service logic in isolation

// Mock the config API
vi.mock("../api/config", () => ({
	configApi: {
		getContent: vi.fn(), // Mock function for fetching file content
		saveOrUpdateBackupFile: vi.fn(), // Mock function for saving backups
		getBackupFileContent: vi.fn(), // Mock function for getting backup content
	},
}));

// Mock the backup utility functions
vi.mock("../utils/backup", () => ({
	timestampId: vi.fn(() => "20251016-150220"), // Always return a fixed timestamp for testing
}));

// Mock the backup name utilities
vi.mock("../utils/backup-names", () => ({
	encodeBackupFileName: vi.fn((id, name) => `backup-${id}-${name}`),
	isBackupFileName: vi.fn((name) => name.startsWith("backup-")),
	parseBackupFileName: vi.fn((name) => {
		// Return parsed backup info if it looks like a backup file
		if (name.startsWith("backup-")) {
			return { id: "20251016-150220", originalName: "test.yaml" };
		}
		return null; // Not a backup file
	}),
}));

describe("backup-service", () => {
	beforeEach(() => {
		// Reset all mocks before each test
		// This ensures tests don't affect each other
		vi.clearAllMocks();
	});

	// Group tests for createBackupSet function
	describe("createBackupSet", () => {
		it("should create backup for a single file", async () => {
			// Arrange: Set up test data
			const filesByName = { "test.yaml": { content: "test content" } };
			// Mock the API to resolve successfully
			configApi.saveOrUpdateBackupFile.mockResolvedValue({});

			// Act: Call the function
			const result = await createBackupSet(
				[], // empty list
				filesByName, // files with content
				{}, // no originals
				"file", // backup kind: single file
				"test.yaml", // file name to backup
			);

			// Assert: Verify the result
			expect(result).toEqual({ id: "20251016-150220", count: 1 });
			// Verify the API was called with correct parameters
			expect(configApi.saveOrUpdateBackupFile).toHaveBeenCalledWith(
				"backup-20251016-150220-test.yaml",
				"test content",
			);
		});

		it("should create backup for all files", async () => {
			// Arrange: Set up multiple files
			const list = [{ name: "file1.yaml" }, { name: "file2.yaml" }];
			const filesByName = {
				"file1.yaml": { content: "content1" },
				"file2.yaml": { content: "content2" },
			};
			configApi.saveOrUpdateBackupFile.mockResolvedValue({});

			// Act: Backup all files
			const result = await createBackupSet(list, filesByName, {}, "all");

			// Assert: Verify both files were backed up
			expect(result).toEqual({ id: "20251016-150220", count: 2 });
			expect(configApi.saveOrUpdateBackupFile).toHaveBeenCalledTimes(2);
		});

		it("should fetch content from API if not in cache", async () => {
			// This test verifies the fallback logic: if content isn't in cache, fetch it
			const filesByName = {}; // No cached content
			const originalsByName = {}; // No original content
			configApi.getContent.mockResolvedValue("fetched content");
			configApi.saveOrUpdateBackupFile.mockResolvedValue({});

			// Act
			await createBackupSet([], filesByName, originalsByName, "file", "test.yaml");

			// Assert: Verify API was called to fetch content
			expect(configApi.getContent).toHaveBeenCalledWith("test.yaml");
			// Verify the fetched content was saved
			expect(configApi.saveOrUpdateBackupFile).toHaveBeenCalledWith(
				"backup-20251016-150220-test.yaml",
				"fetched content",
			);
		});

		it("should throw error for invalid kind", async () => {
			// Test error handling: invalid parameter
			// expect().rejects.toThrow() verifies the function throws an error
			await expect(createBackupSet([], {}, {}, "invalid", "test.yaml")).rejects.toThrow(
				"kind must be 'file' or 'all'",
			);
		});

		it("should throw error when name is missing for file backup", async () => {
			// Test error handling: missing required parameter
			await expect(createBackupSet([], {}, {}, "file")).rejects.toThrow(
				"No file selected/name provided for file backup",
			);
		});

		it("should return count 0 when no files to backup", async () => {
			// Test edge case: list contains only backup files (which are skipped)
			const list = [{ name: "backup-123-test.yaml" }]; // Only backup files

			// Act
			const result = await createBackupSet(list, {}, {}, "all");

			// Assert: Should return 0 count and not call the API
			expect(result).toEqual({ id: "20251016-150220", count: 0 });
			expect(configApi.saveOrUpdateBackupFile).not.toHaveBeenCalled();
		});
	});

	// Group tests for restoreBackupFile function
	describe("restoreBackupFile", () => {
		it("should restore backup file with overwrite", async () => {
			// Arrange: Set up test state (Redux state structure)
			const state = {
				config: {
					filesByName: {}, // No cached content
					list: [],
				},
			};
			configApi.getBackupFileContent.mockResolvedValue("backup content");

			// Act: Restore with overwrite enabled
			const result = await restoreBackupFile("backup-123-test.yaml", true, state);

			// Assert: Verify restore info is correct
			expect(result).toEqual({
				originalName: "test.yaml",
				restoreName: "test.yaml", // Same name when overwrite is true
				content: "backup content",
			});
		});

		it("should restore backup file without overwrite when file doesn't exist", async () => {
			// Test case: restore without overwrite, but file doesn't exist
			const state = {
				config: {
					filesByName: {},
					list: [], // File doesn't exist in list
				},
			};
			configApi.getBackupFileContent.mockResolvedValue("backup content");

			// Act
			const result = await restoreBackupFile("backup-123-test.yaml", false, state);

			// Assert: Should use original name since file doesn't exist
			expect(result).toEqual({
				originalName: "test.yaml",
				restoreName: "test.yaml",
				content: "backup content",
			});
		});

		it("should use cached content if available", async () => {
			// Test optimization: use cached content instead of fetching
			const state = {
				config: {
					filesByName: {
						"backup-123-test.yaml": { content: "cached content" },
					},
					list: [],
				},
			};

			// Act
			const result = await restoreBackupFile("backup-123-test.yaml", true, state);

			// Assert: Should use cached content
			expect(result.content).toBe("cached content");
			// Verify API was NOT called (we used cache)
			expect(configApi.getBackupFileContent).not.toHaveBeenCalled();
		});

		it("should throw error for missing backup name", async () => {
			// Test error handling: empty backup name
			await expect(restoreBackupFile("", true, {})).rejects.toThrow("Missing backup file name");
		});

		it("should throw error for invalid backup file", async () => {
			// Test error handling: file that's not a backup
			// The mock parseBackupFileName returns null for non-backup files
			await expect(restoreBackupFile("invalid.yaml", true, {})).rejects.toThrow(
				"Not a backup file",
			);
		});
	});
});
