package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TlsConfigurationPropertiesTest {

	@Test
	void testDefaultsShouldBeInitialized() {
		var props = new TlsConfigurationProperties();

		assertTrue(props.isEnabled(), "TLS should default to enabled");
		assertNotNull(props.getKeystore(), "Keystore defaults should be present");
		assertEquals(
			"classpath:m8b-keystore.p12",
			props.getKeystore().getPath(),
			"Default keystore path should be classpath:m8b-keystore.p12"
		);
		assertEquals("NOPWD", props.getKeystore().getPassword(), "Default keystore password should be NOPWD");
		assertNull(
			props.getKeystore().getKeyPassword(),
			"Default key password should be null (implying reuse of keystore password)"
		);
		assertNull(props.getKeystore().getKeyAlias(), "Default key alias should be null");
	}

	@Test
	void testAllowCustomKeystoreValues() {
		var keystore = new TlsConfigurationProperties.TlsKeystore();
		keystore.setPath("/custom/keystore.p12");
		keystore.setPassword("secret");
		keystore.setKeyPassword("keySecret");
		keystore.setKeyAlias("custom-alias");

		var props = new TlsConfigurationProperties();
		props.setEnabled(false);
		props.setKeystore(keystore);

		assertTrue(!props.isEnabled(), "TLS flag should reflect provided value");
		assertEquals("/custom/keystore.p12", props.getKeystore().getPath(), "Keystore path should reflect provided value");
		assertEquals("secret", props.getKeystore().getPassword(), "Keystore password should reflect provided value");
		assertEquals("keySecret", props.getKeystore().getKeyPassword(), "Key password should reflect provided value");
		assertEquals("custom-alias", props.getKeystore().getKeyAlias(), "Key alias should reflect provided value");
	}
}
