package org.metricshub.agent.upgrade.api;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.util.HexFormat;
import java.util.Map;

/**
 * A software package offered to the agent, decoupled from the OpAMP protobuf types.
 *
 * @param packageName the offered package name (e.g. {@code metricshub})
 * @param version     the offered version
 * @param downloadUrl the URL the package must be downloaded from
 * @param sha256      the expected SHA-256 of the package content, as raw bytes
 * @param headers     headers to send with the download request; never {@code null}
 */
public record PackageOffer(
	String packageName,
	String version,
	String downloadUrl,
	byte[] sha256,
	Map<String, String> headers
) {
	/**
	 * Returns the expected SHA-256 as a lowercase hexadecimal string.
	 *
	 * @return the hexadecimal representation of the expected content hash
	 */
	public String sha256Hex() {
		return sha256 == null ? "" : HexFormat.of().formatHex(sha256);
	}
}
