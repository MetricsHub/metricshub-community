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

import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.MacrosUpdater;

/**
 * Represents an HTTP request body with a string content.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StringBody implements Body {

	private static final long serialVersionUID = 7408610469247885489L;

	/**
	 * The actual content of the HTTP request body.
	 */
	private String body;

	@Override
	public String getContent(String username, char[] password, String authenticationToken, @NonNull String hostname) {
		return MacrosUpdater.update(body, username, password, authenticationToken, hostname, false);
	}

	@Override
	public Body copy() {
		return StringBody.builder().body(body).build();
	}

	@Override
	public String description() {
		return body;
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		body = updater.apply(body);
	}
}
