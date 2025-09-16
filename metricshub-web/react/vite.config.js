import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig({
	plugins: [react()],
	resolve: { dedupe: ["react", "react-dom"] },
	build: {
		chunkSizeWarningLimit: 1000,
		rollupOptions: {
			output: {
				manualChunks(id) {
					if (id.includes("node_modules")) {
						if (id.includes("@mui/") || id.includes("@emotion/")) return "mui";
						if (id.includes("react-router")) return "react-router";
						if (id.includes("@reduxjs/toolkit") || id.includes("react-redux")) return "redux";
						return "vendor";
					}
				},
			},
		},
	},
});
