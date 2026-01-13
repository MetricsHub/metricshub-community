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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TLS configuration for the embedded web server.
 * <p>
 * By default TLS is enabled and uses the bundled {@code m8b-keystore.p12} on the classpath
 * with password {@code NOPWD}. Users can override by providing {@code tls.keystore.path}
 * and {@code tls.keystore.password}.
 * </p>
 */
@ConfigurationProperties(prefix = "tls")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TlsConfigurationProperties {

	/**
	 * Enable or disable TLS for the embedded web server. When disabled, HTTP is used.
	 */
	private boolean enabled = true;

	/**
	 * Keystore location and password. When empty, the default classpath keystore is used.
	 */
	private TlsKeystore keystore = new TlsKeystore();

	/**
	 * Holder for keystore-specific settings.
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class TlsKeystore {

		/**
		 * Path to the keystore. Supports {@code classpath:} URLs. Defaults to the bundled
		 * {@code m8b-keystore.p12} when not set.
		 */
		private String path = "classpath:m8b-keystore.p12";

		/**
		 * Keystore password; reused as key password. Defaults to {@code NOPWD} when not set.
		 */
		private String password = "NOPWD";

		/**
		 * Optional key password. Defaults to the keystore password when not set.
		 */
		private String keyPassword;

		/**
		 * Optional Key alias to load from the keystore.
		 * If not provided we load the keystore and the first suitable private-key entry is used.
		 */
		private String keyAlias;
	}
}
