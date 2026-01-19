package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.metricshub.web.security.ApiKeyAuthFilter;
import org.metricshub.web.security.ReadOnlyAccessFilter;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.service.UserService;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

class SecurityConfigTest {

	/**
	 * Helper to create SecurityConfig with given TLS props
	 *
	 * @param props TLS configuration properties
	 * @return SecurityConfig instance
	 */
	private SecurityConfig securityConfigWith(TlsConfigurationProperties props) {
		return new SecurityConfig(
			mock(ApiKeyAuthFilter.class),
			mock(ReadOnlyAccessFilter.class),
			mock(JwtComponent.class),
			mock(UserService.class),
			props
		);
	}

	@Test
	void testShouldConfigureSslWhenTlsEnabledAndKeystoreValid() {
		var props = new TlsConfigurationProperties();
		var keystore = props.getKeystore();
		keystore.setPath("classpath:m8b-keystore.p12");
		keystore.setPassword("changeit");
		keystore.setKeyPassword("keypass");
		keystore.setKeyAlias("custom-alias");

		var factory = new TomcatServletWebServerFactory();
		securityConfigWith(props).tlsWebServerCustomizer().customize(factory);

		var ssl = factory.getSsl();
		assertNotNull(ssl, "SSL must be configured when TLS is enabled");
		assertTrue(ssl.isEnabled(), "SSL should be enabled");
		assertEquals("classpath:m8b-keystore.p12", ssl.getKeyStore(), "Keystore path should match configured value");
		assertEquals("PKCS12", ssl.getKeyStoreType(), "Keystore type should be PKCS12 for .p12 files");
		assertEquals("changeit", ssl.getKeyStorePassword(), "Keystore password should match configured value");
		assertEquals("keypass", ssl.getKeyPassword(), "Key password should match configured value");
		assertEquals("custom-alias", ssl.getKeyAlias(), "Key alias should match configured value");
	}

	@Test
	void testShouldDoNothingWhenTlsDisabled() {
		var props = new TlsConfigurationProperties();
		props.setEnabled(false);

		var factory = new TomcatServletWebServerFactory();
		securityConfigWith(props).tlsWebServerCustomizer().customize(factory);

		assertNull(factory.getSsl(), "SSL should remain unconfigured when TLS is disabled");
	}

	@Test
	void testShouldFailWhenKeystoreMissing() {
		var props = new TlsConfigurationProperties();
		props.setKeystore(null);

		var factory = new TomcatServletWebServerFactory();
		var tlsWebServerCustomizer = securityConfigWith(props).tlsWebServerCustomizer();

		assertThrows(
			IllegalStateException.class,
			() -> tlsWebServerCustomizer.customize(factory),
			"Missing keystore should throw"
		);
	}

	@Test
	void testShouldFailWhenKeystorePathMissing() {
		var props = new TlsConfigurationProperties();
		props.getKeystore().setPath(null);
		props.getKeystore().setPassword("changeit");

		var factory = new TomcatServletWebServerFactory();
		var tlsWebServerCustomizer = securityConfigWith(props).tlsWebServerCustomizer();

		assertThrows(
			IllegalStateException.class,
			() -> tlsWebServerCustomizer.customize(factory),
			"Missing keystore path should throw"
		);
	}

	@Test
	void testShouldFailWhenKeystorePasswordMissing() {
		var props = new TlsConfigurationProperties();
		props.getKeystore().setPath("classpath:m8b-keystore.p12");
		props.getKeystore().setPassword(null);

		var factory = new TomcatServletWebServerFactory();
		var tlsWebServerCustomizer = securityConfigWith(props).tlsWebServerCustomizer();

		assertThrows(
			IllegalStateException.class,
			() -> tlsWebServerCustomizer.customize(factory),
			"Missing keystore password should throw"
		);
	}
}
