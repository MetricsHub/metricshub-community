package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.util.List;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JmxRequestExecutorIT {

	private static JMXConnectorServer connectorServer;
	private static int jmxPort;

	public interface TestJmxMBean {
		String getName();
	}

	public static class TestJmx implements TestJmxMBean {

		@Override
		public String getName() {
			return "test-name";
		}
	}

	@BeforeAll
	static void startJmxServer() throws Exception {
		final var mbeanServer = ManagementFactory.getPlatformMBeanServer();

		// Register a simple MBean
		final var name = new ObjectName("org.metricshub.extension.jmx:type=TestJmx");
		mbeanServer.registerMBean(new TestJmx(), name);

		// Dynamically allocate a free port
		jmxPort = findFreePort();
		LocateRegistry.createRegistry(jmxPort);

		final var url = new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://localhost:%d/jmxrmi", jmxPort));

		connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanServer);
		connectorServer.start();
	}

	@AfterAll
	static void stopJmxServer() throws Exception {
		if (connectorServer != null) {
			connectorServer.stop();
		}
	}

	@Test
	void testShouldFetchAttributeFromLiveJmxServer() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("localhost").port(jmxPort).build();

		final List<List<String>> result = new JmxRequestExecutor()
			.fetchBeanInfo(config, "org.metricshub.extension.jmx:type=TestJmx", List.of("Name"), List.of());

		assertNotNull(result, "Result should not be null");
		assertEquals(1, result.size(), "Should return one row");
		assertEquals("test-name", result.get(0).get(0), "Attribute value should match");
	}

	@Test
	void testShouldSucceedHealthCheck() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("localhost").port(jmxPort).build();

		final boolean isAlive = new JmxRequestExecutor().checkConnection(config);
		assertTrue(isAlive, "checkConnection should return true");
	}

	/**
	 * Find a free port number by creating a temporary server socket.
	 *
	 * @return return an int value representing a free port
	 * @throws IOException if an I/O error occurs when creating the socket
	 */
	private static int findFreePort() throws IOException {
		try (var socket = new ServerSocket(0)) {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		}
	}
}
