package org.metricshub.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

public class JdbcCliTest {

	public static final String SQL_QUERY = "SELECT * FROM Win32_OperatingSystem";
	public static final String URL = "jdbc:postgresql://hostname:5432/SentrySoftwareDB";
	public static final String USERNAME = "username";
	public static final String PASSWORD = "password";
	public static final String HOSTNAME = "hostname";

	JdbcCli jdbcCli;
	CommandLine commandLine;

	void initCli() {
		jdbcCli = new JdbcCli();
		commandLine = new CommandLine(jdbcCli);
	}

	@Test
	void testGetQuery() {
		initCli();
		jdbcCli.setQuery(SQL_QUERY);
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(SQL_QUERY));
		assertEquals(queryNode, jdbcCli.getQuery());
	}

	@Test
	void testValidate() {
		initCli();

		// testing url validation
		// testing empty url
		jdbcCli.setUrl(new char[] {});
		ParameterException parameterException = assertThrows(ParameterException.class, () -> jdbcCli.validate());
		assertEquals("SQL url must not be empty nor blank.", parameterException.getMessage());
		// testing blank url
		jdbcCli.setUrl(new char[] { ' ' });
		parameterException = assertThrows(ParameterException.class, () -> jdbcCli.validate());
		assertEquals("SQL url must not be empty nor blank.", parameterException.getMessage());
		jdbcCli.setUrl(URL.toCharArray());

		// testing query validation
		// testing empty query
		jdbcCli.setQuery("");
		parameterException = assertThrows(ParameterException.class, () -> jdbcCli.validate());
		assertEquals("SQL query must not be empty nor blank.", parameterException.getMessage());
		// testing blank query
		jdbcCli.setQuery(" ");
		parameterException = assertThrows(ParameterException.class, () -> jdbcCli.validate());
		assertEquals("SQL query must not be empty nor blank.", parameterException.getMessage());
		jdbcCli.setQuery(SQL_QUERY);
	}

	@Test
	void testIsCharArrayBlank() {
		initCli();
		assertTrue(jdbcCli.isCharArrayBlank(null));
		assertTrue(jdbcCli.isCharArrayBlank(new char[] {}));
		assertTrue(jdbcCli.isCharArrayBlank(new char[] { ' ', ' ', ' ' }));
		assertFalse(jdbcCli.isCharArrayBlank(new char[] { ' ', ' ', ' ', 's' }));
	}
}
