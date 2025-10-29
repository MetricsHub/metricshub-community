package org.metricshub.extension.http.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UrlHelperTest {

	private static final String FULL_URL_1 = "http://hostname:5985/path";
	private static final String FULL_URL_2 = "http://hostname:5985/path/subpath";
	private static final String URL_1 = "http://hostname:5985/path";
	private static final String URL_2 = "http://hostname:5985/path/";
	private static final String PATH_1 = "subpath";
	private static final String PATH_2 = "/subpath";
	private static final String PATH_3 = "/path";
	private static final String HOSTNAME = "hostname";
	private static final Integer PORT = 5985;
	private static final String PROTOCOL = "http";
	private static final String EMPTY = "";

	@Test
	void testFormatUrlAndPath() {
		assertEquals(FULL_URL_2, UrlHelper.format(URL_1, PATH_1));
		assertEquals(FULL_URL_2, UrlHelper.format(URL_1, PATH_2));
		assertEquals(FULL_URL_2, UrlHelper.format(URL_2, PATH_1));
		assertEquals(FULL_URL_2, UrlHelper.format(URL_2, PATH_2));
	}

	@Test
	void testFormatPathOnly() {
		assertEquals(FULL_URL_1, UrlHelper.format(HOSTNAME, PORT, PATH_3, PROTOCOL));
		assertThrows(IllegalArgumentException.class, () -> UrlHelper.format(null, PORT, PATH_3, PROTOCOL));
		assertThrows(IllegalArgumentException.class, () -> UrlHelper.format(HOSTNAME, null, PATH_3, PROTOCOL));
		assertThrows(IllegalArgumentException.class, () -> UrlHelper.format(HOSTNAME, PORT, PATH_3, null));
	}

	@Test
	void testUrlProcessing() {
		assertEquals(FULL_URL_2, UrlHelper.format(PROTOCOL, HOSTNAME, PORT, PATH_1, FULL_URL_1));
		assertThrows(IllegalArgumentException.class, () -> UrlHelper.format(null, null, null, PATH_3, EMPTY));
	}

	@Test
	void testUrlProcessingWithUrlOnly() {
		assertEquals(FULL_URL_1, UrlHelper.format(PROTOCOL, HOSTNAME, PORT, EMPTY, FULL_URL_1));
	}

	@Test
	void testUrlProcessingWithPathOnly() {
		assertEquals(FULL_URL_1, UrlHelper.format(PROTOCOL, HOSTNAME, PORT, PATH_3, EMPTY));
	}

	@Test
	void testUrlProcessingWithoutPathNorUrl() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> UrlHelper.format(PROTOCOL, HOSTNAME, PORT, EMPTY, EMPTY));
	}
}
