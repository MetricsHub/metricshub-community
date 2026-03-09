package org.metricshub.web.config;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.StringHelper;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Chat AI feature.
 * Reads configuration from metricshub.yaml under the web.ai.openai prefix.
 */
@ConfigurationProperties(prefix = "ai.openai")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatOpenAiConfigurationProperties {

	/**
	 * Known context window sizes for OpenAI models (in tokens).
	 * Must be maintained manually as OpenAI does not expose them via API.
	 * Source: https://developers.openai.com/api/docs/models
	 */
	private static final Map<String, Integer> MODEL_CONTEXT_WINDOWS = Map.ofEntries(
		Map.entry("gpt-4o", 128000),
		Map.entry("gpt-4o-mini", 128000),
		Map.entry("gpt-4-turbo", 128000),
		Map.entry("gpt-4", 8192),
		Map.entry("gpt-3.5-turbo", 16385),
		Map.entry("gpt-5.1", 400000),
		Map.entry("gpt-5.2", 400000),
		Map.entry("o1", 200000),
		Map.entry("o1-mini", 128000),
		Map.entry("o3-mini", 200000)
	);

	/**
	 * Known max output tokens for OpenAI models.
	 * Source: https://developers.openai.com/api/docs/models
	 */
	private static final Map<String, Integer> MODEL_MAX_OUTPUT_TOKENS = Map.ofEntries(
		Map.entry("gpt-4o", 16384),
		Map.entry("gpt-4o-mini", 16384),
		Map.entry("gpt-4-turbo", 4096),
		Map.entry("gpt-4", 8192),
		Map.entry("gpt-3.5-turbo", 4096),
		Map.entry("gpt-5.1", 128000),
		Map.entry("gpt-5.2", 128000),
		Map.entry("o1", 100000),
		Map.entry("o1-mini", 65536),
		Map.entry("o3-mini", 100000)
	);

	/**
	 * Default context window size if model is not in the known list.
	 */
	private static final int DEFAULT_CONTEXT_WINDOW = 128000;

	/**
	 * Default max output tokens if model is not in the known list.
	 */
	private static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;

	/**
	 * OpenAI API key. Required for chat functionality.
	 */
	private String apiKey;

	/**
	 * OpenAI model name. Defaults to "gpt-5.2" if not specified.
	 */
	private String model = "gpt-5.2";

	/**
	 * Optional override for context window tokens.
	 * If set, this takes precedence over the built-in model lookup.
	 */
	private Integer contextWindowTokens;

	/**
	 * Optional override for reserved response tokens.
	 * If set, this takes precedence over the built-in model lookup.
	 */
	private Integer reservedResponseTokens;

	/**
	 * Reasoning configuration for OpenAI requests.
	 */
	private ReasoningProperties reasoning = new ReasoningProperties();

	/**
	 * Returns the context window size for the configured model.
	 *
	 * @return context window in tokens
	 */
	public int getEffectiveContextWindowTokens() {
		if (contextWindowTokens != null) {
			return contextWindowTokens;
		}
		return MODEL_CONTEXT_WINDOWS.getOrDefault(normalizeModelName(model), DEFAULT_CONTEXT_WINDOW);
	}

	/**
	 * Returns the max output tokens (reserved for response) for the configured model.
	 *
	 * @return max output tokens
	 */
	public int getEffectiveReservedResponseTokens() {
		if (reservedResponseTokens != null) {
			return reservedResponseTokens;
		}
		return MODEL_MAX_OUTPUT_TOKENS.getOrDefault(normalizeModelName(model), DEFAULT_MAX_OUTPUT_TOKENS);
	}

	/**
	 * Normalizes a model name by removing date suffixes (e.g., "gpt-4o-2024-08-06" -&gt; "gpt-4o").
	 *
	 * @param modelName the full model name
	 * @return normalized model name
	 */
	private String normalizeModelName(final String modelName) {
		if (modelName == null) {
			return null;
		}
		// Remove date suffixes like "-2024-08-06"
		return modelName.replaceAll("-\\d{4}-\\d{2}-\\d{2}$", "");
	}

	@Data
	public static class ReasoningProperties {

		/**
		 * Flag to enable OpenAI reasoning.
		 */
		private boolean enabled = true;

		/**
		 * Parsed reasoning effort enum.
		 */
		private ReasoningEffort effort = ReasoningEffort.MEDIUM;

		/**
		 * Parsed reasoning summary enum.
		 */
		private Reasoning.Summary summary = Reasoning.Summary.AUTO;

		/**
		 * Parses the reasoning effort from a string value.
		 *
		 * @param value the input string
		 * @return the corresponding ReasoningEffort enum
		 */
		public static ReasoningEffort parseEffort(final String value) {
			return switch (normalize(value)) {
				case "HIGH" -> ReasoningEffort.HIGH;
				case "LOW" -> ReasoningEffort.LOW;
				case "MINIMAL" -> ReasoningEffort.MINIMAL;
				case "NONE" -> ReasoningEffort.NONE;
				case "XHIGH" -> ReasoningEffort.XHIGH;
				default -> ReasoningEffort.MEDIUM;
			};
		}

		/**
		 * Parses the reasoning summary from a string value.
		 *
		 * @param value the input string
		 * @return the corresponding Reasoning.Summary enum
		 */
		public static Reasoning.Summary parseSummary(final String value) {
			return switch (normalize(value)) {
				case "CONCISE" -> Reasoning.Summary.CONCISE;
				case "DETAILED" -> Reasoning.Summary.DETAILED;
				default -> Reasoning.Summary.AUTO;
			};
		}

		/**
		 * Normalizes a string by trimming whitespace and converting to uppercase.
		 * @param value the input string
		 * @return the normalized string
		 */
		private static String normalize(final String value) {
			if (StringHelper.nonNullNonBlank(value)) {
				return value.trim().toUpperCase(Locale.ROOT);
			}

			return "";
		}
	}
}
