import js from "@eslint/js";
import globals from "globals";
import { defineConfig, globalIgnores } from "eslint/config";
import react from "eslint-plugin-react";
import reactRecommended from "eslint-plugin-react/configs/recommended.js";
import reactJsxRuntime from "eslint-plugin-react/configs/jsx-runtime.js";
import reactHooks from "eslint-plugin-react-hooks";

// Eslint configuration for React files
export default defineConfig([
	globalIgnores(["dist"]),
	{
		ignores: ["dist"],
		files: ["**/*.{js,jsx}"],

		plugins: {
			react,
			"react-hooks": reactHooks,
		},

		extends: [js.configs.recommended, reactRecommended, reactJsxRuntime],

		languageOptions: {
			ecmaVersion: "latest",
			globals: globals.browser,
			parserOptions: {
				ecmaFeatures: { jsx: true },
				sourceType: "module",
			},
		},

		settings: {
			react: { version: "detect" },
		},

		rules: {
			"react-hooks/rules-of-hooks": "error",
			"react-hooks/exhaustive-deps": "warn",
			"no-unused-vars": ["error", { varsIgnorePattern: "^[A-Z_]" }],
			"react/prop-types": "off",
		},
	},
]);
