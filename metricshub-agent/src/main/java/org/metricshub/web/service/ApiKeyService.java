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

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.security.SecurityManager;
import org.metricshub.web.security.ApiKey;
import org.springframework.stereotype.Service;

/**
 * Service for managing and validating API keys.
 */
@Service
@Slf4j
public class ApiKeyService {

	/**
	 * Find API key by token from the KeyStore.
	 *
	 * @return the ApiKey if found, null otherwise
	 */
	public ApiKey findApiKeyByToken(final String token) {
		try {
			final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
			final var ks = SecurityManager.loadKeyStore(keyStoreFile);

			final var aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
				final var alias = aliases.nextElement();
				if (!alias.startsWith(AgentConstants.API_KEY_PREFIX)) {
					continue;
				}

				final var entry = ks.getEntry(alias, new PasswordProtection(new char[] { 's', 'e', 'c', 'r', 'e', 't' }));
				if (entry instanceof KeyStore.SecretKeyEntry secreKeyEntry) {
					final var secretKey = secreKeyEntry.getSecretKey();
					final var apiKeyId = new String(secretKey.getEncoded(), StandardCharsets.UTF_8);

					final var parts = apiKeyId.split("__");
					final var key = parts[0];
					LocalDateTime expirationDateTime = null;
					if (parts.length > 1) {
						expirationDateTime = LocalDateTime.parse(parts[1]);
					}
					final var apiKeyAlias = alias.substring(AgentConstants.API_KEY_PREFIX.length());
					if (token.equals(key)) {
						return new ApiKey(apiKeyAlias, key, expirationDateTime);
					}
				}
			}
		} catch (Exception e) {
			log.error("Failed to resolve API keys from KeyStore");
			log.debug("Exception details: ", e);
		}

		return null;
	}

	/**
	 * Validates the provided API key.
	 *
	 * @param apiKey the API key to validate
	 * @return true if the API key is valid, false otherwise
	 */
	public boolean isValid(final ApiKey apiKey) {
		return apiKey != null && (apiKey.expiresOn() == null || apiKey.expiresOn().isAfter(LocalDateTime.now()));
	}
}
