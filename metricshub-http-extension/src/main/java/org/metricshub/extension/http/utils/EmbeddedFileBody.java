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

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.common.helpers.MacrosUpdater;
import org.sentrysoftware.metricshub.engine.connector.model.common.EmbeddedFile;

/**
 * Represents the body of an HTTP request containing an embedded file.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmbeddedFileBody implements Body {

	private static final long serialVersionUID = -8300191804094179578L;

	/**
	 * The embedded file body content.
	 */
	private EmbeddedFile body;

	@Override
	public String getContent(String username, char[] password, String authenticationToken, @NonNull String hostname) {
		if (body == null) {
			return EMPTY;
		}

		return MacrosUpdater.update(body.getContentAsString(), username, password, authenticationToken, hostname, false);
	}

	@Override
	public Body copy() {
		return EmbeddedFileBody.builder().body(body.copy()).build();
	}

	@Override
	public String description() {
		return body != null ? body.description() : null;
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		if (body != null) {
			body.update(updater);
		}
	}
}
