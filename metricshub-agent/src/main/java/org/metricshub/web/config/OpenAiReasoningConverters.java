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
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/**
 * Converters for OpenAI reasoning properties.
 */
@Configuration
public class OpenAiReasoningConverters {

	/**
	 * Converter for ReasoningEffort from String.
	 *
	 * @return the converter instance
	 */
	@Bean
	@ConfigurationPropertiesBinding
	public Converter<String, ReasoningEffort> reasoningEffortConverter() {
		return ChatOpenAiConfigurationProperties.ReasoningProperties::parseEffort;
	}

	/**
	 * Converter for Reasoning.Summary from String.
	 *
	 * @return the converter instance
	 */
	@Bean
	@ConfigurationPropertiesBinding
	public Converter<String, Reasoning.Summary> reasoningSummaryConverter() {
		return ChatOpenAiConfigurationProperties.ReasoningProperties::parseSummary;
	}
}
