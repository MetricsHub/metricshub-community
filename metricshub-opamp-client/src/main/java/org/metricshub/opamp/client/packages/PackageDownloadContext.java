package org.metricshub.opamp.client.packages;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
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

import java.util.Map;
import java.util.Optional;

/**
 * Connection material of the OpAMP endpoint that a package download may reuse when the package
 * repository shares the OpAMP server's authentication or trust chain. The Upgrade Manager is free
 * to ignore this context and build its own HTTP client from its own configuration.
 */
public interface PackageDownloadContext {
	/**
	 * Returns the headers configured for the OpAMP endpoint (e.g. an {@code Authorization}
	 * header).
	 *
	 * @return an immutable map of header names to values; never {@code null}
	 */
	Map<String, String> headers();

	/**
	 * Returns the path to the PEM file containing the trusted certificate configured for the
	 * OpAMP endpoint, if any.
	 *
	 * @return the optional trusted certificate file path
	 */
	Optional<String> certificateFile();
}
