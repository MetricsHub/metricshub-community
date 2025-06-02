package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.management.*;
import javax.management.remote.*;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

public class JmxSourceIntegrationTest {

	/**
	 * A very simple MBean for testing.
	 */
	public interface TestMBean {
		String getFoo();
		int getBar();
	}

	public static class Test implements TestMBean {

		@Override
		public String getFoo() {
			return "Hello";
		}

		@Override
		public int getBar() {
			return 123;
		}
	}

	static void testProcessJmxSourceWithRealServer() throws Exception {
		// 1) Start an RMI registry on a random port

		// 2) Create & register MBeanServer
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		ObjectName name = new ObjectName("com.example:type=Test,name=Integration");

		// 3) Launch a JMXConnectorServer
		String urlStr = String.format("service:jmx:rmi:///jndi/rmi://127.0.0.1:%d/jmxrmi", 7199);
		JMXServiceURL url = new JMXServiceURL(urlStr);
		JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
		cs.start();

		try {
			// 4) Build a real JmxSource and processor
			JmxSource.MBeanConfig cfg = JmxSource.MBeanConfig
				.builder()
				.objectName("com.example:type=Test,name=Integration")
				.attributes(List.of("Foo", "Bar"))
				.keysAsAttributes(List.of("name"))
				.build();

			JmxSource src = JmxSource
				.builder()
				.host("127.0.0.1")
				.port(7199)
				.mbeans(List.of(cfg))
				.forceSerialization(false)
				.build();

			// Note: build your TelemetryManager / SourceProcessor exactly as you do in your app.
			SourceProcessor proc = SourceProcessor
				.<JmxSource>builder()
				.telemetryManager(realTelemetryManager()) // your real TM
				.clientsExecutor(null)
				.connectorId("test-connector")
				.build();

			// 5) Invoke and verify
			SourceTable result = proc.process(src);
			List<List<String>> rows = result.getTable();

			// We expect one row: [ "Integration", "Hello", "123" ]
			assertEquals(1, rows.size());
			List<String> row = rows.get(0);
			assertEquals("Integration", row.get(0));
			assertEquals("Hello", row.get(1));
			assertEquals("123", row.get(2));
		} finally {
			cs.stop();
		}
	}

	// ---------------------------------------------------------------------
	// Helper stubs to get a real TelemetryManager / ExecutorService.
	// Replace these with however you wire up your real application context.
	// ---------------------------------------------------------------------
	private static TelemetryManager realTelemetryManager() {
		// e.g. load from Spring context or build manually
		return TelemetryManager.builder().build();
	}

	public static void main(String[] args) throws Exception {
		testProcessJmxSourceWithRealServer();
	}
}
