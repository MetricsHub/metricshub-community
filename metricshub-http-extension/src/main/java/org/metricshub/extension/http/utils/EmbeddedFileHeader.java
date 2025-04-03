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

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.connector.model.common.EmbeddedFile;

/**
 * Represents the header of an HTTP request containing an embedded file.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmbeddedFileHeader implements Header {

	private static final long serialVersionUID = 7171137961999511622L;

	/**
	 * The embedded file header content.
	 */
	private EmbeddedFile header;

	@Override
	public Map<String, String> getContent(
		String username,
		char[] password,
		String authenticationToken,
		@NonNull String hostname
	) {
		if (header == null) {
			return new HashMap<>();
		}

		return Header.resolveAndParseHeader(header.getContentAsString(), username, password, authenticationToken, hostname);
	}

	@Override
	public Header copy() {
		return EmbeddedFileHeader.builder().header(header.copy()).build();
	}

	@Override
	public String description() {
		return header != null ? header.description() : null;
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		if (header != null) {
			header.update(updater);
		}
	}
}
