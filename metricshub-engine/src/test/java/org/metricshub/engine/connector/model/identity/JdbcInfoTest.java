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
		final DriverInfo info = DriverInfo.create("com.acme.Driver", null);
		assertEquals("com.acme.Driver", info.getClassName());
		assertNull(info.getJarPath());
	}

	@Test
	void bothFieldsValid() throws Exception {
		final DriverInfo info = DriverInfo.create(
			"com.ibm.as400.access.AS400JDBCDriver",
			"$INSTALL_DIR/lib/extensions/jdbc/jt400.jar"
		);
		assertEquals("com.ibm.as400.access.AS400JDBCDriver", info.getClassName());
		assertEquals("$INSTALL_DIR/lib/extensions/jdbc/jt400.jar", info.getJarPath());
	}

	@Test
	void blankPathIsNormalisedToNull() throws Exception {
		assertNull(DriverInfo.create("a.B", "   ").getJarPath());
		assertNull(DriverInfo.create("a.B", "").getJarPath());
	}

	@Test
	void surroundingWhitespaceTrimmed() throws Exception {
		final DriverInfo info = DriverInfo.create("a.B", "   /opt/x.jar   ");
		assertEquals("/opt/x.jar", info.getJarPath());
	}

	@Test
	void classNameRequired() {
		final JsonMappingException nullClass = assertThrows(JsonMappingException.class, () ->
			DriverInfo.create(null, "/opt/x.jar")
		);
		assertTrue(nullClass.getMessage().contains("className"));
		assertThrows(JsonMappingException.class, () -> DriverInfo.create("   ", "/opt/x.jar"));
	}

	@Test
	void parserAcceptsPathPlaceholdersAndGlobs() throws Exception {
		// Parse-time accepts everything non-blank; resolver enforces the security boundary.
		assertEquals(
			"$INSTALL_DIR/lib/extensions/jdbc/zos/db2jcc4.jar",
			DriverInfo.create("a.B", "$INSTALL_DIR/lib/extensions/jdbc/zos/db2jcc4.jar").getJarPath()
		);
		assertEquals(
			"$USER_HOME/.metricshub/drivers/acme.jar",
			DriverInfo.create("a.B", "$USER_HOME/.metricshub/drivers/acme.jar").getJarPath()
		);
		assertEquals(
			"/opt/oracle/instantclient/ojdbc11.jar",
			DriverInfo.create("a.B", "/opt/oracle/instantclient/ojdbc11.jar").getJarPath()
		);
		assertEquals(
			"$INSTALL_DIR/lib/extensions/jdbc/ojdbc*.jar",
			DriverInfo.create("a.B", "$INSTALL_DIR/lib/extensions/jdbc/ojdbc*.jar").getJarPath()
		);
	}
}
