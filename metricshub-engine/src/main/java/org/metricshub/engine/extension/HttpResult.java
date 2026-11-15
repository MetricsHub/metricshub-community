package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.metricshub.engine.connector.model.common.ResultContent;

/**
 * The response to an HTTP request: its status code, its header and its body.
 * <p>
 * An <code>http</code> source reads it as a text, the part its <code>resultContent</code> names
 * ({@link #toText(ResultContent)}). Code that needs the status and the body reads them here.
 *
 * @param statusCode The HTTP status code (200, 401, ...)
 * @param header     The header lines, each one ending with a line feed
 * @param body       The body
 */
public record HttpResult(int statusCode, String header, String body) {
	/**
	 * The text of {@link ResultContent#ALL_WITH_STATUS}: "Status: 200", the header lines, an empty line, the body.
	 */
	private static final Pattern ALL_WITH_STATUS = Pattern.compile(
		"(?s)Status: (\\d+)(?:\\n((?:[^\\n]+\\n)*)\\n(.*))?\\n?"
	);

	/**
	 * The part of the response a <code>resultContent</code> names, as a text.
	 *
	 * @param resultContent What to return
	 * @return The text
	 */
	public String toText(final ResultContent resultContent) {
		return switch (resultContent) {
			case BODY -> body;
			case HEADER -> header;
			case HTTP_STATUS -> String.valueOf(statusCode);
			case ALL -> header + "\n" + Objects.toString(body, "");
			case ALL_WITH_STATUS -> "Status: " + statusCode + "\n" + header + "\n" + Objects.toString(body, "");
		};
	}

	/**
	 * Reads back the text of {@link ResultContent#ALL_WITH_STATUS}, which is how a response is recorded.
	 *
	 * @param text The text {@link #toText(ResultContent)} gives for {@link ResultContent#ALL_WITH_STATUS}
	 * @return The response, <code>null</code> when the text is not one
	 */
	public static HttpResult parse(final String text) {
		final Matcher matcher = text == null ? null : ALL_WITH_STATUS.matcher(text);
		if (matcher == null || !matcher.matches()) {
			return null;
		}
		return new HttpResult(
			Integer.parseInt(matcher.group(1)),
			matcher.group(2) == null ? "" : matcher.group(2),
			matcher.group(3) == null ? "" : matcher.group(3)
		);
	}
}
