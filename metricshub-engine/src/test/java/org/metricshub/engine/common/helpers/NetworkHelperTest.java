package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class NetworkHelperTest {

	@Test
	void testIsLocalhost() throws Exception {
		assertTrue(NetworkHelper.isLocalhost("localhost"));
		assertTrue(NetworkHelper.isLocalhost("127.0.0.1"));
		assertTrue(NetworkHelper.isLocalhost("0:0:0:0:0:0:0:1"));
		assertTrue(NetworkHelper.isLocalhost("::1"));
		assertTrue(NetworkHelper.isLocalhost("0000:0000:0000:0000:0000:0000:0000:0001"));
		assertTrue(NetworkHelper.isLocalhost(InetAddress.getLocalHost().getHostName()));
		assertFalse(NetworkHelper.isLocalhost(UUID.randomUUID().toString()));
		assertTrue(NetworkHelper.isLocalhost(WHITE_SPACE));
		assertTrue(NetworkHelper.isLocalhost(""));
		assertTrue(NetworkHelper.isLocalhost(null));
		try (MockedStatic<InetAddress> inetAddressMock = mockStatic(InetAddress.class)) {
			final String randomHost = UUID.randomUUID().toString();
			inetAddressMock.when(() -> InetAddress.getByName(eq(randomHost))).thenReturn(null);
			assertFalse(NetworkHelper.isLocalhost(randomHost));
		}

		try (MockedStatic<NetworkInterface> networkInterface = mockStatic(NetworkInterface.class)) {
			final String randomHost = UUID.randomUUID().toString();
			final InetAddress mockedInetAddress = Mockito.mock(InetAddress.class);
			try (MockedStatic<InetAddress> inetAddressMock = mockStatic(InetAddress.class)) {
				inetAddressMock.when(() -> InetAddress.getByName(eq(randomHost))).thenReturn(mockedInetAddress);
				networkInterface
					.when(() -> NetworkInterface.getByInetAddress(mockedInetAddress))
					.thenThrow(new SocketException());
				assertFalse(NetworkHelper.isLocalhost(randomHost));
			}
		}
	}

	@Test
	void testGetFqdn() throws Exception {
		// hostname is null
		assertNull(NetworkHelper.getFqdn(null));

		// hostname is blank
		String hostname = "   ";
		assertEquals(hostname, NetworkHelper.getFqdn(hostname));

		// hostname cannot be resolved
		hostname = UUID.randomUUID().toString();
		assertEquals(hostname, NetworkHelper.getFqdn(hostname));

		// hostname can be resolved
		assertNotNull(NetworkHelper.getFqdn("localhost"));
	}

	@Test
	void testResolveDns() throws Exception {
		// hostname is null
		assertNull(NetworkHelper.resolveDns(null));

		// hostname is blank
		String hostname = "   ";
		assertNull(NetworkHelper.resolveDns(hostname));

		// hostname is empty
		hostname = "";
		assertNull(NetworkHelper.resolveDns(hostname));

		// hostname is an illegal hostname
		hostname = "-host";
		assertNull(NetworkHelper.resolveDns(hostname));

		// hostname can be resolved
		hostname = "localhost";
		assertNotNull(NetworkHelper.resolveDns(hostname));
	}

	@Test
	void testCreateUrlWithLocalhostIPv4() throws Exception {
		final URL url = NetworkHelper.createUrl("http", "127.0.0.1", 8080);
		assertEquals("http://127.0.0.1:8080", url.toString());
	}

	@Test
	void testCreateUrlWithLocalhostIPv6() throws Exception {
		{
			final URL url = NetworkHelper.createUrl("https", "::1", 443);
			assertEquals("https://[::1]:443", url.toString());
		}
		{
			final URL url = NetworkHelper.createUrl("https", "0:0:0:0:0:0:0:1", 443);
			assertEquals("https://[0:0:0:0:0:0:0:1]:443", url.toString());
		}
		{
			final URL url = NetworkHelper.createUrl("https", "0000:0000:0000:0000:0000:0000:0000:0001", 443);
			assertEquals("https://[0000:0000:0000:0000:0000:0000:0000:0001]:443", url.toString());
		}
	}

	@Test
	void testCreateUrlWithIPv6() throws Exception {
		final URL url = NetworkHelper.createUrl("https", "2001:db8::ff00:42:8329", 443);
		assertEquals("https://[2001:db8::ff00:42:8329]:443", url.toString());
		assertEquals("[2001:db8::ff00:42:8329]", url.getHost());
	}

	@Test
	void testCreateUrlWithIPv6AlreadyWrapped() throws Exception {
		final URL url = NetworkHelper.createUrl("https", "[2001:db8::ff00:42:8329]", 443);
		assertEquals("https://[2001:db8::ff00:42:8329]:443", url.toString());
		assertEquals("[2001:db8::ff00:42:8329]", url.getHost());
	}

	@Test
	void testCreateUrlWithIPv4() throws Exception {
		final URL url = NetworkHelper.createUrl("http", "192.168.1.1", 80);
		assertEquals("http://192.168.1.1:80", url.toString());
	}

	@Test
	void testCreateUrlWithNonStandardPort() throws Exception {
		final URL url = NetworkHelper.createUrl("ftp", "192.168.1.1", 2121);
		assertEquals("ftp://192.168.1.1:2121", url.toString());
	}

	@Test
	void testCreateUrlWithDefaultHttpPort() throws Exception {
		final URL url = NetworkHelper.createUrl("http", "example.com", 80);
		assertEquals("http://example.com:80", url.toString());
	}

	@Test
	void testCreateUrlWithDefaultHttpsPort() throws Exception {
		final URL url = NetworkHelper.createUrl("https", "example.com", 443);
		assertEquals("https://example.com:443", url.toString());
	}

	@Test
	void testCreateUrlWithoutPort() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> NetworkHelper.createUrl("http", "example.com", null));
	}

	@Test
	void testCreateUrlWithoutHostnameOrIp() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> NetworkHelper.createUrl("http", null, 80));
	}

	@Test
	void testCreateUrlWithoutProtocol() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> NetworkHelper.createUrl(null, "example.com", 80));
	}

	@Test
	void testCreateUrlWithInvalidProtocol() throws Exception {
		assertThrows(MalformedURLException.class, () -> NetworkHelper.createUrl("htp", "example.com", 80));
	}

	@Test
	void testCreateUrlWithInvalidIp() throws Exception {
		assertThrows(UnknownHostException.class, () -> NetworkHelper.createUrl("http", "999.999.999.999", 80));
	}
}
