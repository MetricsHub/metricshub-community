package org.metricshub.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiKeyRegistryTest {

	private ApiKeyRegistry registry;

	private static final String PRINCIPAL_1 = "client-openai-1";
	private static final String TOKEN_1 = "token-123";

	private static final String PRINCIPAL_2 = "client-openai-2";
	private static final String TOKEN_2 = "token-456";

	@BeforeEach
	void setUp() {
		registry = new ApiKeyRegistry(Map.of(PRINCIPAL_1, TOKEN_1, PRINCIPAL_2, TOKEN_2));
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
		assertEquals(PRINCIPAL_1, registry.getPrincipal(TOKEN_1), "Expected principal for TOKEN_1 to be user1");
		assertEquals(PRINCIPAL_2, registry.getPrincipal(TOKEN_2), "Expected principal for TOKEN_2 to be user2");
	}

	@Test
	void testGetPrincipalWithInvalidToken() {
		assertNull(registry.getPrincipal("nonexistent-token"), "Expected principal to be null for unknown token");
	}

	@Test
	void testIsValidWithEmptyRegistry() {
		ApiKeyRegistry emptyRegistry = new ApiKeyRegistry(Map.of());
		assertFalse(emptyRegistry.isValid(TOKEN_1), "Expected TOKEN_1 to be invalid in empty registry");
	}

	@Test
	void testGetPrincipalWithEmptyRegistry() {
		ApiKeyRegistry emptyRegistry = new ApiKeyRegistry(Map.of());
		assertNull(emptyRegistry.getPrincipal(TOKEN_1), "Expected principal to be null in empty registry");
	}
}
