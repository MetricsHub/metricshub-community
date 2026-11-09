package org.metricshub.engine.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityManagerTest {

	private static File securityPath;

	@BeforeAll
	static void beforeEeach() throws Exception {
		securityPath = new File("src/test/resources/security/metricshub-keystore.p12");
	}

	@Test
	void testEncryptDecrypt() throws Exception {
		// null password
		assertNull(SecurityManager.decrypt(null, securityPath));
		assertNull(SecurityManager.encrypt(null, securityPath));

		char[] password = {};
		char[] encryptedPassword = SecurityManager.encrypt(password, securityPath);
		assertArrayEquals(password, SecurityManager.decrypt(encryptedPassword, securityPath));

		password = "password".toCharArray();
		encryptedPassword = SecurityManager.encrypt(password, securityPath);
		assertArrayEquals(password, SecurityManager.decrypt(encryptedPassword, securityPath));

		password = "password2".toCharArray();
		encryptedPassword = SecurityManager.encrypt(password, securityPath);
		assertArrayEquals(password, SecurityManager.decrypt(encryptedPassword, securityPath));
	}

	@Test
	void updateSecretsShouldKeepTheOtherEntriesAndReplaceTheFile(@TempDir final Path directory) throws Exception {
		final File keyStoreFile = directory.resolve("metricshub-keystore.p12").toFile();
		final char[] encrypted = SecurityManager.encrypt("password".toCharArray(), keyStoreFile);
		final byte[] secret = "c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U".getBytes(StandardCharsets.US_ASCII);

		SecurityManager.updateSecrets(keyStoreFile, Map.of("first", secret, "second", new byte[] { 1, 2, 3 }));

		assertArrayEquals("password".toCharArray(), SecurityManager.decrypt(encrypted, keyStoreFile));
		assertArrayEquals(secret, SecurityManager.readSecret(keyStoreFile, "first"));

		final Map<String, byte[]> removal = new HashMap<>();
		removal.put("second", null);
		SecurityManager.updateSecrets(keyStoreFile, removal);

		assertNull(SecurityManager.readSecret(keyStoreFile, "second"));
		assertArrayEquals(secret, SecurityManager.readSecret(keyStoreFile, "first"));
		assertArrayEquals("password".toCharArray(), SecurityManager.decrypt(encrypted, keyStoreFile));
		try (var files = Files.list(directory)) {
			assertEquals(List.of(keyStoreFile.toPath()), files.toList());
		}
	}

	@Test
	void readSecretShouldBeNullWithoutKeyStore(@TempDir final Path directory) throws Exception {
		final File keyStoreFile = directory.resolve("metricshub-keystore.p12").toFile();
		assertNull(SecurityManager.readSecret(keyStoreFile, "first"));
		assertFalse(keyStoreFile.exists());
	}
}
