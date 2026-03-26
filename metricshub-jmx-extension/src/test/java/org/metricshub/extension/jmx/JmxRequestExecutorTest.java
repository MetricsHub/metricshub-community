package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JmxRequestExecutorTest {

	private JmxRequestExecutor jmxRequestExecutor;
	private JMXConnector jmxConnectorMock;
	private MBeanServerConnection mBeanServerConnectionMock;

	@BeforeEach
	void setUp() throws Exception {
		jmxRequestExecutor = spy(new JmxRequestExecutor());
		jmxConnectorMock = mock(JMXConnector.class);
		mBeanServerConnectionMock = mock(MBeanServerConnection.class);

		// By default, stub connect to return our mock connector
		doReturn(jmxConnectorMock).when(jmxRequestExecutor).connect(any(), any(), any(), any());
		when(jmxConnectorMock.getMBeanServerConnection()).thenReturn(mBeanServerConnectionMock);
	}

	@Test
	void testFetchMBeanReturnsMatchedAttributes() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(eq(objectName), eq("HeapMemoryUsage"))).thenReturn("1024");

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("1024", result.get(0).get(0));
	}

	@Test
	void testFetchMBeanReturnsEmptyWhenNoMatches() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Collections.emptySet());

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=NonExistent",
			List.of("attr"),
			List.of(),
			null
		);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanIncludesKeyProperties() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final Hashtable<String, String> keyTable = new Hashtable<>();
		keyTable.put("type", "GarbageCollector");
		keyTable.put("name", "G1 Young Gen");
		final ObjectName objectName = new ObjectName("java.lang", keyTable);

		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(eq(objectName), eq("CollectionCount"))).thenReturn(42L);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=GarbageCollector,name=*",
			List.of("CollectionCount"),
			List.of("name", "type"),
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		final List<String> row = result.get(0);
		assertEquals(3, row.size());
		assertEquals("G1 Young Gen", row.get(0));
		assertEquals("GarbageCollector", row.get(1));
		assertEquals("42", row.get(2));
	}

	@Test
	void testFetchMBeanHandlesMissingKeyProperty() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Runtime");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Runtime",
			List.of(),
			List.of("nonExistentKey"),
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("", result.get(0).get(0));
	}

	@Test
	void testFetchMBeanHandlesAttributeException() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(any(), eq("BadAttr")))
			.thenThrow(new javax.management.AttributeNotFoundException("BadAttr"));

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("BadAttr"),
			List.of(),
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("", result.get(0).get(0));
	}

	@Test
	void testFetchMBeanReturnsEmptyOnConnectionFailure() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("badhost").port(1099).timeout(30L).build();

		doThrow(new IOException("Connection refused")).when(jmxRequestExecutor).connect(any(), any(), any(), any());

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			null
		);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanWithResourceHostname() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Runtime");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(eq(objectName), eq("Uptime"))).thenReturn(12345L);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Runtime",
			List.of("Uptime"),
			List.of(),
			"myHost"
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("12345", result.get(0).get(0));
	}

	@Test
	void testFetchMBeanMultipleMatches() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName1 = new ObjectName("java.lang:type=GarbageCollector,name=G1YoungGen");
		final ObjectName objectName2 = new ObjectName("java.lang:type=GarbageCollector,name=G1OldGen");
		final Set<ObjectName> matches = new HashSet<>();
		matches.add(objectName1);
		matches.add(objectName2);

		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(matches);
		when(mBeanServerConnectionMock.getAttribute(eq(objectName1), eq("CollectionCount"))).thenReturn(10);
		when(mBeanServerConnectionMock.getAttribute(eq(objectName2), eq("CollectionCount"))).thenReturn(20);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=GarbageCollector,name=*",
			List.of("CollectionCount"),
			List.of("name"),
			null
		);

		assertNotNull(result);
		assertEquals(2, result.size());
	}

	@Test
	void testCheckConnectionReturnsTrue() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final boolean result = jmxRequestExecutor.checkConnection(config, null);
		assertTrue(result);
	}

	@Test
	void testCheckConnectionReturnsFalseOnFailure() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("badhost").port(1099).timeout(30L).build();

		doThrow(new IOException("Connection refused")).when(jmxRequestExecutor).connect(any(), any(), any(), any());

		final boolean result = jmxRequestExecutor.checkConnection(config, null);
		assertFalse(result);
	}

	@Test
	void testCheckConnectionWithResourceHostname() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final boolean result = jmxRequestExecutor.checkConnection(config, "myHost");
		assertTrue(result);
	}

	@Test
	void testCheckConnectionWithCredentials() throws Exception {
		final JmxConfiguration config = JmxConfiguration
			.builder()
			.hostname("testhost")
			.port(9999)
			.username("admin")
			.password("secret".toCharArray())
			.timeout(30L)
			.build();

		final boolean result = jmxRequestExecutor.checkConnection(config, null);
		assertTrue(result);
	}

	@Test
	void testFetchMBeanWithNoAttributes() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Runtime");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Runtime",
			List.of(),
			List.of("type"),
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(1, result.get(0).size());
		assertEquals("Runtime", result.get(0).get(0));
	}

	@Test
	void testBuildJmxRmiUrl() {
		final String url = jmxRequestExecutor.buildJmxRmiUrl("myhost", 1099);
		assertEquals("service:jmx:rmi:///jndi/rmi://myhost:1099/jmxrmi", url);
	}

	@Test
	void testBuildJmxRmiUrlCustomPort() {
		final String url = jmxRequestExecutor.buildJmxRmiUrl("192.168.1.1", 9999);
		assertEquals("service:jmx:rmi:///jndi/rmi://192.168.1.1:9999/jmxrmi", url);
	}
}
