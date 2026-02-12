package org.metricshub.web.service;

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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for generating and validating ephemeral API keys for M8B proxy requests.
 * <p>
 * Ephemeral tokens are:
 * </p>
 * <ul>
 *   <li>Single-use: consumed immediately upon validation</li>
 *   <li>Short-lived: expire after 2 minutes</li>
 *   <li>Used internally by M8bControlPlaneClient for proxy requests</li>
 * </ul>
 */
@Service
@Slf4j
public class EphemeralApiKeyService {

	/**
	 * Token TTL in milliseconds (2 minutes).
	 */
	private static final long TOKEN_TTL_MS = 2 * 60 * 1000;

	/**
	 * Map of ephemeral tokens to their creation timestamps.
	 */
	private final ConcurrentHashMap<String, Long> ephemeralTokens = new ConcurrentHashMap<>();

	/**
	 * Generates a new ephemeral token for M8B proxy requests.
	 *
	 * @return the generated token
	 */
	public String generateToken() {
		final String token = UUID.randomUUID().toString();
		ephemeralTokens.put(token, System.currentTimeMillis());
		log.debug("Generated ephemeral token: {}", token.substring(0, 8) + "...");
		return token;
	}

	/**
	 * Validates and consumes an ephemeral token.
	 * <p>
	 * The token is removed from the store after validation (single-use).
	 * </p>
	 *
	 * @param token the token to validate
	 * @return true if the token is valid, false otherwise
	 */
	public boolean validateAndConsume(String token) {
		if (token == null || token.isBlank()) {
			return false;
		}

		final Long createdAt = ephemeralTokens.remove(token);
		if (createdAt == null) {
			log.debug("Ephemeral token not found or already consumed");
			return false;
		}

		final boolean valid = (System.currentTimeMillis() - createdAt) < TOKEN_TTL_MS;
		if (!valid) {
			log.debug("Ephemeral token expired");
		}
		return valid;
	}

	/**
	 * Periodically cleans up expired tokens.
	 * <p>
	 * Runs every 60 seconds to remove tokens older than TTL.
	 * </p>
	 */
	@Scheduled(fixedRate = 60000)
	public void cleanupExpiredTokens() {
		final long now = System.currentTimeMillis();
		final int sizeBefore = ephemeralTokens.size();
		ephemeralTokens.entrySet().removeIf(entry -> (now - entry.getValue()) > TOKEN_TTL_MS);
		final int removed = sizeBefore - ephemeralTokens.size();
		if (removed > 0) {
			log.debug("Cleaned up {} expired ephemeral tokens", removed);
		}
	}

	/**
	 * Returns the number of active tokens.
	 *
	 * @return the token count
	 */
	public int getTokenCount() {
		return ephemeralTokens.size();
	}
}
