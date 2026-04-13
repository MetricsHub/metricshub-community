package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.security.MetricsHubSecurityException;
import org.metricshub.engine.security.SecurityManager;
import org.mockito.MockedStatic;

class PasswordEncryptionServiceTest {

	private PasswordEncryptionService service;
	private File keystoreFile;

	@TempDir
	private File tempDir;

	@BeforeEach
	void setUp() {
		service = new PasswordEncryptionService();
		keystoreFile = new File(tempDir, "keystore.p12");
	}

	@Test
	void encryptPassword_nullOrBlankThrowsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> service.encryptPassword(null));
		assertThrows(IllegalArgumentException.class, () -> service.encryptPassword(""));
		assertThrows(IllegalArgumentException.class, () -> service.encryptPassword("   "));
	}

	@Test
	void encryptPassword_invalidBase64ThrowsIllegalArgumentExceptionWithCause() {
		final IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> service.encryptPassword("not-valid-base64!!!")
		);
		assertEquals("Invalid Base64 in request body.", ex.getMessage());
		assertInstanceOf(IllegalArgumentException.class, ex.getCause());
	}

	@Test
	void encryptPassword_successReturnsStringFromSecurityManager() throws Exception {
		final String plain = "mySecret";
		final String transport = Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
		final char[] fakeCipher = "enc:abc".toCharArray();

		try (
			MockedStatic<PasswordEncrypt> mockedPasswordEncrypt = mockStatic(PasswordEncrypt.class);
			MockedStatic<SecurityManager> mockedSecurityManager = mockStatic(SecurityManager.class)
		) {
			mockedPasswordEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(keystoreFile);
			mockedSecurityManager
				.when(() -> SecurityManager.encrypt(any(char[].class), eq(keystoreFile)))
				.thenReturn(fakeCipher);

			final String result = service.encryptPassword(transport);

			assertEquals("enc:abc", result);
		}
	}

	@Test
	void encryptPassword_trimsWhitespaceAroundBase64() throws Exception {
		final String plain = "x";
		final String inner = Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
		final String transport = "  \t" + inner + "  ";

		try (
			MockedStatic<PasswordEncrypt> mockedPasswordEncrypt = mockStatic(PasswordEncrypt.class);
			MockedStatic<SecurityManager> mockedSecurityManager = mockStatic(SecurityManager.class)
		) {
			mockedPasswordEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(keystoreFile);
			mockedSecurityManager
				.when(() -> SecurityManager.encrypt(any(char[].class), eq(keystoreFile)))
				.thenReturn("ok".toCharArray());

			assertEquals("ok", service.encryptPassword(transport));
		}
	}

	@Test
	void encryptPassword_securityExceptionPropagates() {
		final String transport = Base64.getEncoder().encodeToString("p".getBytes(StandardCharsets.UTF_8));

		try (
			MockedStatic<PasswordEncrypt> mockedPasswordEncrypt = mockStatic(PasswordEncrypt.class);
			MockedStatic<SecurityManager> mockedSecurityManager = mockStatic(SecurityManager.class)
		) {
			mockedPasswordEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(keystoreFile);
			mockedSecurityManager
				.when(() -> SecurityManager.encrypt(any(char[].class), eq(keystoreFile)))
				.thenThrow(new MetricsHubSecurityException("keystore failed"));

			final MetricsHubSecurityException ex = assertThrows(
				MetricsHubSecurityException.class,
				() -> service.encryptPassword(transport)
			);
			assertEquals("keystore failed", ex.getMessage());
		}
	}
}
