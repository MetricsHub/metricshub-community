import { availableParallelism } from "node:os";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig({
	plugins: [react()],
	resolve: {
		dedupe: [
			"react",
			"react-dom",
			"@mui/material",
			"@mui/x-data-grid",
			"@mui/x-tree-view",
			"@mui/x-internals",
		],
	},
	test: {
		// Bound memory usage when frontend and Maven Java tests run together.
		maxWorkers: Math.min(4, availableParallelism()),
		globals: true,
		environment: "happy-dom",
		setupFiles: "./src/test/setup.js",
		css: true,
	},
});
