package org.metricshub.extension.http.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
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

import static org.sentrysoftware.metricshub.engine.common.helpers.StringHelper.nonNullNonBlank;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.common.helpers.MacrosUpdater;

/**
 * Utility class for formatting URLs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UrlHelper {

	/**
	 * Generate the full URL based on the Path only.
	 *
	 * @param hostname    hostname
	 * @param port        port of the request
	 * @param requestPath request path of the request
	 * @param protocol    protocol used by the request
	 * @return formatted String
	 */
	public static String format(
		@NonNull final String hostname,
		@NonNull final Integer port,
		@NonNull final String requestPath,
		@NonNull final String protocol
	) {
		String fullUrl = String.format(
			"%s://%s:%d%s%s",
			protocol,
			hostname,
			port,
			requestPath.startsWith("/") ? "" : "/",
			requestPath
		);
		return MacrosUpdater.update(fullUrl, null, null, null, hostname, false);
	}

	/**
	 * Generate the full URL based on the concatenation of URL and Path.
	 *
	 * @param url   full URL inserted in the resource
	 * @param path  request path
	 * @return formatted String URL
	 */
	public static String format(@NonNull final String url, @NonNull final String path) {
		return String.format(
			"%s%s%s",
			url,
			url.endsWith("/") || path.startsWith("/") ? "" : "/",
			url.endsWith("/") && path.startsWith("/") ? path.substring(1) : path
		);
	}

	/**
	 * Generate the full URL.
	 *
	 * @param protocol    protocol used by the request.
	 * @param hostname    hostname.
	 * @param port        port of the request.
	 * @param path        request path of the request.
	 * @param url         full URL if specified.
	 * @return formatted String URL.
	 * @throws IllegalArgumentException when neither URL nor path are specified for the source to monitor.
	 */
	public static String format(
		final String protocol,
		final String hostname,
		final Integer port,
		final String path,
		final String url
	) {
		if (nonNullNonBlank(url) && nonNullNonBlank(path)) {
			// Both URL and path have been specified. Consequently, the full URL will be the concatenation of them in that way: 'url/path'
			return UrlHelper.format(url, path);
		} else if (nonNullNonBlank(url)) {
			// URL is already specified by the user
			return url;
		} else if (nonNullNonBlank(path)) {
			// Only Path is specified. Consequently, the full URL is generated from it and other fields.
			return UrlHelper.format(hostname, port, path, protocol);
		}

		// None of the URL and path have been specified, an exception is thrown
		throw new IllegalArgumentException(
			"At least one of the required properties 'url' or 'path' must be specified, but both are missing"
		);
	}
}
