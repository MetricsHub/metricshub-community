package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.cli.service.ApiKeyCliService;
import org.metricshub.web.security.ApiKey;
import org.mockito.MockedStatic;

class ApiKeyServiceTest {

	private ApiKeyService apiKeyService;
	private File tempKeystore;

	@TempDir
	private File tempDir;

	@BeforeEach
	void setup() {
		apiKeyService = new ApiKeyService();
		tempKeystore = new File(tempDir, "metricshub-keystore.p12");
	}

	@Test
	void testShouldFindApiKeyByTokenWithoutExpiration() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			// Write a key without expiration under alias "svc1"
			var token = writeApiKeyEntry("svc1", null);

			// Act
			final var found = apiKeyService.findApiKeyByToken(token);

			// Assert
			assertNotNull(found, "ApiKey should be found");
			assertEquals("svc1", found.alias(), "Alias should match");
			assertEquals(token, found.key(), "Token should match");
			assertNull(found.expiresOn(), "Expiration should be null for non-expiring key");
		}
	}

	@Test
	void testShouldFindApiKeyByTokenWithFutureExpiration() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			// Write a key expiring in the future
			final LocalDateTime expires = LocalDateTime.now().plusHours(2).withNano(0);
			var token = writeApiKeyEntry("svc2", expires);

			// Act
			final var found = apiKeyService.findApiKeyByToken(token);

			// Assert
			assertNotNull(found, "ApiKey should be found");
			assertEquals("svc2", found.alias(), "Alias should match");
			assertEquals(token, found.key(), "Token should match");
			assertEquals(expires, found.expiresOn(), "Expiration should match stored value");
		}
	}

	@Test
	void testShouldReturnNullWhenTokenNotFound() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			// Store a different token
			writeApiKeyEntry("svc3", null);

			// Act
			final var found = apiKeyService.findApiKeyByToken("does-not-exist");

			// Assert
			assertNull(found, "ApiKey should be null when token does not exist");
		}
	}

	@Test
	void testIsValidShouldBeTrueForNullExpiration() {
		final var key = new ApiKey("name", "tok", null);
		assertTrue(apiKeyService.isValid(key), "Key with null expiration should be valid");
	}

	@Test
	void testIsValidShouldBeTrueForFutureExpiration() {
		final var key = new ApiKey("name", "tok", LocalDateTime.now().plusDays(1));
		assertTrue(apiKeyService.isValid(key), "Key with future expiration should be valid");
	}

	@Test
	void testIsValidShouldBeFalseForPastExpiration() {
		final var key = new ApiKey("name", "tok", LocalDateTime.now().minusSeconds(1));
		assertFalse(apiKeyService.isValid(key), "Key with past expiration should be invalid");
	}

	/**
	 * Writes an API key entry into the keystore for testing.
	 *
	 * @param alias   the alias under which to store the key
	 * @param expires the expiration date-time, or null for no expiration
	 * @return the token string
	 * @throws Exception on error
	 */
	private String writeApiKeyEntry(final String alias, final LocalDateTime expires) throws Exception {
		return ApiKeyCliService.CreateCommand.createApiKey(alias, expires);
	}
}
