package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;

class JdbcConfigurationTest {

	@Test
	void testGenerateUrl() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.type("mysql")
			.hostname("localhost")
			.port(3306)
			.build();

		final char[] expectedUrl = "jdbc:mysql://localhost:3306/".toCharArray();
		assertArrayEquals(expectedUrl, jdbcConfiguration.generateUrl());
	}

	@Test
	void testValidateConfigurationMissingUsername() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.type("mysql")
			.database("testdb")
			.hostname("localhost")
			.username("")
			.build();

		final Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> jdbcConfiguration.validateConfiguration("resourceKey")
		);

		assertEquals(
			"Resource resourceKey - Username value is invalid for JDBC. " +
			"This resource will not be monitored. Please verify the configured username value.",
			exception.getMessage()
		);
	}

	@Test
	void testValidateConfigurationInvalidTimeout() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.type("mysql")
			.database("testdb")
			.hostname("localhost")
			.timeout(-10L)
			.build();

		final Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> jdbcConfiguration.validateConfiguration("resourceKey")
		);

		assertEquals(
			"Resource resourceKey - Timeout value is invalid for JDBC. " +
			"Timeout value returned: -10. This resource will not be monitored. " +
			"Please verify the configured timeout value.",
			exception.getMessage()
		);
	}

	@Test
	void testValidateConfigurationInvalidPort() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.type("mysql")
			.database("testdb")
			.hostname("localhost")
			.port(-1)
			.build();

		final Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> jdbcConfiguration.validateConfiguration("resourceKey")
		);

		assertEquals(
			"Resource resourceKey - Invalid port configured for JDBC. " +
			"Port value returned: -1. This resource will not be monitored. " +
			"Please verify the configured port value.",
			exception.getMessage()
		);
	}

	@Test
	void testValidateConfigurationWithNullUrl() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.type("mysql")
			.database("testdb")
			.hostname("localhost")
			.port(3306)
			.url(null)
			.build();

		assertDoesNotThrow(() -> jdbcConfiguration.validateConfiguration("resourceKey"));
		assertNotNull(jdbcConfiguration.getUrl());
		assertEquals("jdbc:mysql://localhost:3306/testdb", new String(jdbcConfiguration.getUrl()));
	}

	@Test
	void testValidateConfigurationWithEmptyUrl() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.type("unsupported_db")
			.database("testdb")
			.hostname("localhost")
			.port(3306)
			.build();

		final Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> jdbcConfiguration.validateConfiguration("resourceKey")
		);

		assertEquals(
			"Resource resourceKey - Invalid url configured for JDBC." +
			" This resource will not be monitored." +
			" Please verify the configured url value.",
			exception.getMessage()
		);
	}

	@Test
	void testValidateConfigurationWithPredefinedUrl() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.type("mysql")
			.database("testdb")
			.hostname("localhost")
			.port(3306)
			.url("jdbc:mysql://custom-host:3306/customdb".toCharArray())
			.build();

		assertDoesNotThrow(() -> jdbcConfiguration.validateConfiguration("resourceKey"));
		assertEquals("jdbc:mysql://custom-host:3306/customdb", new String(jdbcConfiguration.getUrl()));
	}

	@Test
	void testValidateConfigurationGeneratesUrlWhenUrlIsEmpty() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.type("mysql")
			.hostname("localhost")
			.port(3306)
			.url(new char[0]) // URL is empty
			.build();

		assertDoesNotThrow(() -> jdbcConfiguration.validateConfiguration("resourceKey"));
		assertNotNull(jdbcConfiguration.getUrl());
		assertEquals("jdbc:mysql://localhost:3306/", new String(jdbcConfiguration.getUrl()));
	}

	@Test
	void testValidateConfigurationAssignsDefaultPort() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.type("mysql")
			.database("testdb")
			.hostname("localhost")
			.port(null)
			.build();

		assertDoesNotThrow(() -> jdbcConfiguration.validateConfiguration("resourceKey"));
		assertEquals(3306, jdbcConfiguration.getPort());
	}

	@Test
	void testCopyConfigurationWithAllFields() {
		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.username("testUser")
			.password("password".toCharArray())
			.type("mysql")
			.database("testdb")
			.port(3306)
			.url("jdbc:mysql://localhost:3306/testdb".toCharArray())
			.timeout(200L)
			.build();
		final IConfiguration jdbcConfigurationCopy = jdbcConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(jdbcConfiguration, jdbcConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (jdbcConfiguration != jdbcConfigurationCopy);
	}
}
