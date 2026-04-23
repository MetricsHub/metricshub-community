package org.metricshub.web.service;

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

import static org.metricshub.agent.security.PasswordEncrypt.getKeyStoreFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.metricshub.engine.security.MetricsHubSecurityException;
import org.metricshub.engine.security.SecurityManager;
import org.springframework.stereotype.Service;

/**
 * Encrypts passwords using the MetricsHub agent keystore (same output as the encrypt CLI).
 */
@Service
public class PasswordEncryptionService {

	/**
	 * Decodes a Base64 transport string (UTF-8 bytes of the plaintext password), encrypts with the keystore,
	 * and returns the ciphertext string.
	 *
	 * @param passwordBase64 Base64-encoded UTF-8 bytes of the password; must not be null or blank after trim
	 * @return encrypted password (plain text representation, CLI-compatible)
	 * @throws IllegalArgumentException if the value is blank or Base64 is invalid
	 * @throws MetricsHubSecurityException if keystore encryption fails
	 */
	public String encryptPassword(final String passwordBase64) throws MetricsHubSecurityException {
		if (passwordBase64 == null || passwordBase64.isBlank()) {
			throw new IllegalArgumentException("Request body must contain Base64-encoded UTF-8 password bytes.");
		}

		final byte[] decoded;
		try {
			decoded = Base64.getDecoder().decode(passwordBase64.trim());
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid Base64 in request body.", e);
		}

		final String plainPassword = new String(decoded, StandardCharsets.UTF_8);
		final char[] passwordChars = plainPassword.toCharArray();
		try {
			final char[] encrypted = SecurityManager.encrypt(passwordChars, getKeyStoreFile(true));
			return new String(encrypted);
		} finally {
			Arrays.fill(passwordChars, '\0');
		}
	}
}
