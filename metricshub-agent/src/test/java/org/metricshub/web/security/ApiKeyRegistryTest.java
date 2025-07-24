package org.metricshub.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.security.ApiKeyRegistry.ApiKey;

class ApiKeyRegistryTest {

	private ApiKeyRegistry registry;

	private static final String PRINCIPAL_1 = "client-openai-1";
	private static final String TOKEN_1 = "token-123";

	private static final String PRINCIPAL_2 = "client-openai-2";
	private static final String TOKEN_2 = "token-456";

	private static final LocalDateTime EXPIRATION_DATE_TIME = LocalDateTime.now().plusDays(1);

	@BeforeEach
	void setUp() {
		registry =
			new ApiKeyRegistry(
				Map.of(
					PRINCIPAL_1,
					new ApiKey(PRINCIPAL_1, TOKEN_1, null),
					PRINCIPAL_2,
					new ApiKey(PRINCIPAL_2, TOKEN_2, EXPIRATION_DATE_TIME)
				)
			);
	}

	@Test
	void testIsValidWithValidToken() {
		assertTrue(registry.isValid(TOKEN_1), "Expected TOKEN_1 to be valid");
		assertTrue(registry.isValid(TOKEN_2), "Expected TOKEN_2 to be valid");
	}

	@Test
	void testIsValidWithInvalidToken() {
		assertFalse(registry.isValid("invalid-token"), "Expected invalid token to be rejected");
	}

	@Test
	void testGetPrincipalWithValidToken() {
		assertEquals(
			new ApiKey(PRINCIPAL_1, TOKEN_1, null),
			registry.getApiKeyByToken(TOKEN_1),
			"Expiected API key for TOKEN_1 to match principal 1"
		);
		assertEquals(
			new ApiKey(PRINCIPAL_2, TOKEN_2, EXPIRATION_DATE_TIME),
			registry.getApiKeyByToken(TOKEN_2),
			"Expected API key for TOKEN_2 to match principal 2"
		);
	}

	@Test
	void testGetPrincipalWithInvalidToken() {
		assertNull(registry.getApiKeyByToken("nonexistent-token"), "Expected API key to be null for unknown token");
	}

	@Test
	void testIsValidWithEmptyRegistry() {
		ApiKeyRegistry emptyRegistry = new ApiKeyRegistry(Map.of());
		assertFalse(emptyRegistry.isValid(TOKEN_1), "Expected TOKEN_1 to be invalid in empty registry");
	}

	@Test
	void testGetPrincipalWithEmptyRegistry() {
		ApiKeyRegistry emptyRegistry = new ApiKeyRegistry(Map.of());
		assertNull(emptyRegistry.getApiKeyByToken(TOKEN_1), "Expected API key to be null in empty registry");
	}
}
