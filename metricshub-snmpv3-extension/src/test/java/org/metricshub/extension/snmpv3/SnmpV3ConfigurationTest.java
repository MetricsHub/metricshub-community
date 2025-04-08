package org.metricshub.extension.snmpv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.extension.snmpv3.SnmpV3Configuration.AuthType;
import org.metricshub.extension.snmpv3.SnmpV3Configuration.Privacy;

class SnmpV3ConfigurationTest {

	@Test
	void testValidateConfiguration() {
		final String resourceKey = "resourceKey";

		// Test when port is negative
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(-1)
				.timeout(60L)
				.authType(AuthType.NO_AUTH)
				.privacy(Privacy.NO_ENCRYPTION)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}

		// Test when port is greater than maximum allowed value
		{
			final SnmpV3Configuration snmpConfig = SnmpV3Configuration
				.builder()
				.port(66666)
				.timeout(60L)
				.authType(AuthType.NO_AUTH)
				.privacy(Privacy.NO_ENCRYPTION)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		// Test when port is null
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(null)
				.timeout(60L)
				.authType(AuthType.NO_AUTH)
				.privacy(Privacy.NO_ENCRYPTION)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}

		// Test when timeout is negative
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(1234)
				.timeout(-60L)
				.authType(AuthType.NO_AUTH)
				.privacy(Privacy.NO_ENCRYPTION)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}

		// Test when timeout is null
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(1234)
				.timeout(null)
				.authType(AuthType.NO_AUTH)
				.privacy(Privacy.NO_ENCRYPTION)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}

		// Test when username is null
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(1234)
				.timeout(60L)
				.authType(AuthType.NO_AUTH)
				.privacy(Privacy.AES)
				.username(null)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}

		// Test when timeout is zero
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(1234)
				.timeout(0L)
				.authType(AuthType.NO_AUTH)
				.privacy(Privacy.NO_ENCRYPTION)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}

		// Test when username is empty
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(1234)
				.timeout(60L)
				.authType(AuthType.SHA)
				.privacy(Privacy.AES)
				.username("")
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}

		// Test when authType is null
		{
			final SnmpV3Configuration snmpV3Config = SnmpV3Configuration
				.builder()
				.port(1234)
				.timeout(60L)
				.authType(null)
				.privacy(Privacy.AES)
				.username("user")
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpV3Config.validateConfiguration(resourceKey));
		}
	}

	@Test
	void testPrivacyInterpretValueOfValidValues() {
		assertEquals(Privacy.NO_ENCRYPTION, Privacy.interpretValueOf("no"));
		assertEquals(Privacy.AES, Privacy.interpretValueOf("AES"));
		assertEquals(Privacy.DES, Privacy.interpretValueOf("DES"));
	}

	@Test
	void testPrivacyInterpretValueOfInvalidValue() {
		assertThrows(IllegalArgumentException.class, () -> Privacy.interpretValueOf("INVALID_PRIVACY"));
	}

	@Test
	void testInterpretValueOfNoAuth() {
		assertEquals(AuthType.NO_AUTH, AuthType.interpretValueOf("no authentication"));
		assertEquals(AuthType.NO_AUTH, AuthType.interpretValueOf("NoAuth"));
		assertEquals(AuthType.NO_AUTH, AuthType.interpretValueOf("NOAUTH"));
	}

	@Test
	void testInterpretValueOfMD5() {
		assertEquals(AuthType.MD5, AuthType.interpretValueOf("MD5"));
		assertEquals(AuthType.MD5, AuthType.interpretValueOf("md5"));
		assertEquals(AuthType.MD5, AuthType.interpretValueOf("Md5Hash"));
	}

	@Test
	void testInterpretValueOfSHA256() {
		assertEquals(AuthType.SHA256, AuthType.interpretValueOf("SHA256"));
		assertEquals(AuthType.SHA256, AuthType.interpretValueOf("SecureSha256"));
	}

	@Test
	void testInterpretValueOfSHA512() {
		assertEquals(AuthType.SHA512, AuthType.interpretValueOf("SHA512"));
		assertEquals(AuthType.SHA512, AuthType.interpretValueOf("MySha512Auth"));
	}

	@Test
	void testInterpretValueOfSHA224() {
		assertEquals(AuthType.SHA224, AuthType.interpretValueOf("SHA224"));
		assertEquals(AuthType.SHA224, AuthType.interpretValueOf("sha224Auth"));
	}

	@Test
	void testInterpretValueOfSHA384() {
		assertEquals(AuthType.SHA384, AuthType.interpretValueOf("SHA384"));
		assertEquals(AuthType.SHA384, AuthType.interpretValueOf("CustomSha384"));
	}

	@Test
	void testInterpretValueOfSHA() {
		assertEquals(AuthType.SHA, AuthType.interpretValueOf("SHA"));
		assertEquals(AuthType.SHA, AuthType.interpretValueOf("sha"));
		assertEquals(AuthType.SHA, AuthType.interpretValueOf("ShaAuthentication"));
	}

	@Test
	void testInterpretValueOfInvalidAuthType() {
		IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> AuthType.interpretValueOf("invalidauth")
		);

		assertTrue(exception.getMessage().contains("Invalid authentication type"));
	}

	@Test
	void testInterpretValueOfCaseInsensitive() {
		assertEquals(AuthType.SHA256, AuthType.interpretValueOf("sHa256"));
		assertEquals(AuthType.SHA384, AuthType.interpretValueOf("SHA384"));
		assertEquals(AuthType.NO_AUTH, AuthType.interpretValueOf("NO auth"));
	}

	@Test
	void testCopy() {
		final SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration
			.builder()
			.authType(AuthType.MD5)
			.contextName("context")
			.password("password".toCharArray())
			.port(100)
			.privacy(Privacy.AES)
			.privacyPassword("privacyPassword".toCharArray())
			.retryIntervals(new int[] { 10, 10 })
			.timeout(100L)
			.username("username")
			.build();

		final IConfiguration snmpV3ConfigurationCopy = snmpV3Configuration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(snmpV3Configuration, snmpV3ConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (snmpV3Configuration != snmpV3ConfigurationCopy);
	}
}
