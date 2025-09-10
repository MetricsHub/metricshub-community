package org.metricshub.extension.snmp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.extension.snmp.SnmpConfiguration.SnmpVersion;

class SnmpConfigurationTest {

	@Test
	void testValidateConfiguration() {
		final String resourceKey = "resourceKey";

		final char[] community = "public".toCharArray();
		final char[] emptyCommunity = new char[] {};

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(emptyCommunity)
				.port(1234)
				.timeout(60L)
				.version(SnmpVersion.V1)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(null)
				.port(1234)
				.timeout(60L)
				.version(SnmpVersion.V1)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(community)
				.port(-1)
				.timeout(60L)
				.version(SnmpVersion.V1)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(community)
				.port(66666)
				.timeout(60L)
				.version(SnmpVersion.V1)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(community)
				.port(null)
				.timeout(60L)
				.version(SnmpVersion.V1)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(community)
				.port(1234)
				.timeout(-60L)
				.version(SnmpVersion.V1)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(community)
				.port(1234)
				.timeout(null)
				.version(SnmpVersion.V1)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(community)
				.port(1234)
				.timeout(60L)
				.version(SnmpVersion.V2C)
				.retryIntervals(null)
				.build();

			assertDoesNotThrow(() -> snmpConfig.validateConfiguration(resourceKey));
		}

		{
			final SnmpConfiguration snmpConfig = SnmpConfiguration
				.builder()
				.community(community)
				.port(1234)
				.timeout(60L)
				.version(SnmpVersion.V2C)
				.retryIntervals(new int[] { 100, 500 })
				.build();

			assertDoesNotThrow(() -> snmpConfig.validateConfiguration(resourceKey));
		}
	}

	@Test
	void testSnmpVersionInterpretValueOf() {
		assertEquals(SnmpVersion.V1, SnmpVersion.interpretValueOf("1"));
		assertEquals(SnmpVersion.V1, SnmpVersion.interpretValueOf("v1"));
		assertEquals(SnmpVersion.V2C, SnmpVersion.interpretValueOf("2"));
		assertEquals(SnmpVersion.V2C, SnmpVersion.interpretValueOf("v2"));
		assertEquals(SnmpVersion.V2C, SnmpVersion.interpretValueOf("v2c"));
		assertEquals(SnmpVersion.V2C, SnmpVersion.interpretValueOf("2c"));
	}

	@Test
	void testCopy() {
		final SnmpConfiguration snmpConfiguration = SnmpConfiguration
			.builder()
			.community("public".toCharArray())
			.port(100)
			.timeout(100L)
			.version(SnmpVersion.V2C)
			.build();

		final IConfiguration snmpConfigurationCopy = snmpConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(snmpConfiguration, snmpConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (snmpConfiguration != snmpConfigurationCopy);
	}

	@Test
	void testGetProperty() {
		final SnmpConfiguration snmpConfiguration = SnmpConfiguration
			.builder()
			.community("myCommunity".toCharArray())
			.port(443)
			.retryIntervals(new int[] { 100 })
			.version(SnmpVersion.V2C)
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(snmpConfiguration.getProperty(null));
		assertNull(snmpConfiguration.getProperty(""));
		assertNull(snmpConfiguration.getProperty("badProperty"));

		final Object propertyCommunityObject = snmpConfiguration.getProperty("community");
		assertInstanceOf(char[].class, propertyCommunityObject);
		assertArrayEquals("myCommunity".toCharArray(), (char[]) propertyCommunityObject);

		final Object propertyRetryIntervalsObject = snmpConfiguration.getProperty("retryintervals");
		assertInstanceOf(int[].class, propertyRetryIntervalsObject);
		assertArrayEquals(new int[] { 100 }, (int[]) propertyRetryIntervalsObject);

		assertEquals(SnmpVersion.V2C, snmpConfiguration.getProperty("version"));
		assertEquals(443, snmpConfiguration.getProperty("port"));
		assertEquals(100L, snmpConfiguration.getProperty("timeout"));
		assertEquals("myHostname", snmpConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final SnmpConfiguration snmpConfiguration = new SnmpConfiguration();
		assertFalse(snmpConfiguration.isCorrespondingProtocol(null));
		assertFalse(snmpConfiguration.isCorrespondingProtocol(""));
		assertFalse(snmpConfiguration.isCorrespondingProtocol("http"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("SNMP"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("snmp"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("SnMp"));

		assertTrue(snmpConfiguration.isCorrespondingProtocol("SNMP1"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("snmp1"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("SnMp1"));

		assertTrue(snmpConfiguration.isCorrespondingProtocol("SNMPV1"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("snmpv1"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("SnMpV1"));

		assertTrue(snmpConfiguration.isCorrespondingProtocol("SNMP2"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("snmp2"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("SnMp2"));

		assertTrue(snmpConfiguration.isCorrespondingProtocol("SNMPV2"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("snmpv2"));
		assertTrue(snmpConfiguration.isCorrespondingProtocol("SnMpV2"));
	}
}
