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
	 * OpenAI API key. Required for chat functionality.
	 */
	private String apiKey;

	/**
	 * OpenAI model name. Defaults to "gpt-5.2" if not specified.
	 */
	private String model = "gpt-5.2";

	/**
	 * Reasoning configuration for OpenAI requests.
	 */
	private ReasoningProperties reasoning = new ReasoningProperties();

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
