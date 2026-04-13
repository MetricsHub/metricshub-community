package org.metricshub.engine.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
