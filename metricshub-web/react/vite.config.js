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
		globals: true,
		environment: "happy-dom",
		setupFiles: "./src/test/setup.js",
		css: true,
	},
});
