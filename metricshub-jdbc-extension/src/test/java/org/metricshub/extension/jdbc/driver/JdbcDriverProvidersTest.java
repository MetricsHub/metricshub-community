package org.metricshub.extension.jdbc.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JdbcDriverProvidersTest {

	@Test
	void builtInProviderListsTheFourShadedDrivers() {
		final Set<String> classes = new HashSet<>();
		for (final JdbcDriverDescriptor d : new BuiltInJdbcDriverProvider().provide()) {
			assertEquals(DriverOrigin.BUILT_IN, d.origin(), "Built-in descriptors must be BUILT_IN");
			assertFalse(d.driverPackages().isEmpty(), "Built-in must declare driver packages for " + d.driverClass());
			classes.add(d.driverClass());
		}
		assertTrue(classes.contains("org.mariadb.jdbc.Driver"));
		assertTrue(classes.contains("org.postgresql.Driver"));
		assertTrue(classes.contains("com.mysql.cj.jdbc.Driver"));
		assertTrue(classes.contains("org.h2.Driver"));
	}

	@Test
	void externalProviderUsesUserDefaultOrigin() {
		final Collection<JdbcDriverDescriptor> descriptors = new ExternalJdbcDriverDescriptorsProvider().provide();
		assertFalse(descriptors.isEmpty(), "External provider must declare at least one descriptor");
		final Set<String> classes = new HashSet<>();
		for (final JdbcDriverDescriptor d : descriptors) {
			assertEquals(DriverOrigin.USER_DEFAULT, d.origin(), "External descriptors must be USER_DEFAULT");
			assertFalse(d.driverPackages().isEmpty(), "External descriptor must declare driver packages: " + d.driverClass());
			classes.add(d.driverClass());
		}
		assertTrue(classes.contains("oracle.jdbc.OracleDriver"));
		assertTrue(classes.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver"));
		assertTrue(classes.contains("net.sourceforge.jtds.jdbc.Driver"));
		assertTrue(classes.contains("com.informix.jdbc.IfxDriver"));
		assertTrue(classes.contains("org.apache.derby.jdbc.EmbeddedDriver"));
		assertTrue(classes.contains("com.ibm.as400.access.AS400JDBCDriver"));
		assertTrue(classes.contains("com.ibm.db2.jcc.DB2Driver"));
	}

	@Test
	void serviceLoaderDiscoversBothProviders() {
		boolean builtIn = false;
		boolean external = false;
		for (final IJdbcDriverProvider p : ServiceLoader.load(
			IJdbcDriverProvider.class,
			JdbcDriverProvidersTest.class.getClassLoader()
		)) {
			if (p instanceof BuiltInJdbcDriverProvider) {
				builtIn = true;
			} else if (p instanceof ExternalJdbcDriverDescriptorsProvider) {
				external = true;
			}
		}
		assertTrue(builtIn, "BuiltInJdbcDriverProvider must be discovered by ServiceLoader");
		assertTrue(external, "ExternalJdbcDriverDescriptorsProvider must be discovered by ServiceLoader");
	}
}
