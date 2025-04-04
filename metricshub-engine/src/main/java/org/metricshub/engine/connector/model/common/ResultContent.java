package org.metricshub.engine.connector.model.common;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enumeration representing different types of result content that can be extracted from a connector.
 */
@Getter
@AllArgsConstructor
public enum ResultContent {
	/**
	 * Represents HTTP status as result content.
	 */
	@JsonAlias(value = { "httpStatus", "http_status" })
	HTTP_STATUS("httpStatus"),
	/**
	 * Represents header as result content.
	 */
	@JsonAlias("header")
	HEADER("header"),
	/**
	 * Represents body as result content.
	 */
	@JsonAlias("body")
	BODY("body"),
	/**
	 * Represents all content as result content.
	 */
	@JsonAlias("all")
	ALL("all"),

	/**
	 * Represents all content as result content with status code.
	 */
	@JsonAlias("all_with_status")
	ALL_WITH_STATUS("all_with_status");

	/**
	 * Map each ResultContent with a regular expression that detects it
	 */
	private static final Map<ResultContent, Pattern> DETECTORS = Map.of(
		HTTP_STATUS,
		Pattern.compile("^httpstatus$|^http_status$"),
		HEADER,
		Pattern.compile("^header$"),
		BODY,
		Pattern.compile("^body$"),
		ALL,
		Pattern.compile("^all$"),
		ALL_WITH_STATUS,
		Pattern.compile("^all_with_status$")
	);

	private String name;

	/**
	 * Detects {@link ResultContent} using the value defined in the connector code.
	 *
	 * @param value The value to detect.
	 * @return {@link ResultContent} instance.
	 * @throws IllegalArgumentException If the value is not a supported ResultContent.
	 */
	public static ResultContent detect(final String value) {
		// Null returns null
		if (value == null) {
			return null;
		}

		// Check all regex in DETECTORS to see which one matches
		final String lCaseValue = value.trim().toLowerCase();
		for (Map.Entry<ResultContent, Pattern> detector : DETECTORS.entrySet()) {
			if (detector.getValue().matcher(lCaseValue).find()) {
				return detector.getKey();
			}
		}

		// No match => Exception
		throw new IllegalArgumentException(
			"'" + value + "' is not a supported ResultContent. Accepted values are: [ httpStatus, header, body, all ]."
		);
	}
}
