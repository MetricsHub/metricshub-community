package org.metricshub.engine.connector.model.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;

class JdbcInfoTest {

	@Test
	void minimalValidBlock() throws Exception {
		final JdbcInfo info = JdbcInfo.create("com.acme.Driver", null);
		assertEquals("com.acme.Driver", info.getDriverClass());
		assertNull(info.getDriverPath());
	}

	@Test
	void bothFieldsValid() throws Exception {
		final JdbcInfo info = JdbcInfo.create(
			"com.ibm.as400.access.AS400JDBCDriver",
			"$INSTALL_DIR/lib/extensions/jdbc/jt400.jar"
		);
		assertEquals("com.ibm.as400.access.AS400JDBCDriver", info.getDriverClass());
		assertEquals("$INSTALL_DIR/lib/extensions/jdbc/jt400.jar", info.getDriverPath());
	}

	@Test
	void blankPathIsNormalisedToNull() throws Exception {
		assertNull(JdbcInfo.create("a.B", "   ").getDriverPath());
		assertNull(JdbcInfo.create("a.B", "").getDriverPath());
	}

	@Test
	void surroundingWhitespaceTrimmed() throws Exception {
		final JdbcInfo info = JdbcInfo.create("a.B", "   /opt/x.jar   ");
		assertEquals("/opt/x.jar", info.getDriverPath());
	}

	@Test
	void driverClassRequired() {
		final JsonMappingException nullClass = assertThrows(JsonMappingException.class, () ->
			JdbcInfo.create(null, "/opt/x.jar")
		);
		assertTrue(nullClass.getMessage().contains("driverClass"));
		assertThrows(JsonMappingException.class, () -> JdbcInfo.create("   ", "/opt/x.jar"));
	}

	@Test
	void parserAcceptsPathPlaceholdersAndGlobs() throws Exception {
		// Parse-time accepts everything non-blank; resolver enforces the security boundary.
		assertEquals(
			"$INSTALL_DIR/lib/extensions/jdbc/zos/db2jcc4.jar",
			JdbcInfo.create("a.B", "$INSTALL_DIR/lib/extensions/jdbc/zos/db2jcc4.jar").getDriverPath()
		);
		assertEquals(
			"$USER_HOME/.metricshub/drivers/acme.jar",
			JdbcInfo.create("a.B", "$USER_HOME/.metricshub/drivers/acme.jar").getDriverPath()
		);
		assertEquals(
			"/opt/oracle/instantclient/ojdbc11.jar",
			JdbcInfo.create("a.B", "/opt/oracle/instantclient/ojdbc11.jar").getDriverPath()
		);
		assertEquals(
			"$INSTALL_DIR/lib/extensions/jdbc/ojdbc*.jar",
			JdbcInfo.create("a.B", "$INSTALL_DIR/lib/extensions/jdbc/ojdbc*.jar").getDriverPath()
		);
	}
}
