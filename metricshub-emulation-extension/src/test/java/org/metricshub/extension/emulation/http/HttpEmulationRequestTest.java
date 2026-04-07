package org.metricshub.extension.emulation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/**
 * Test of {@link HttpEmulationRequest}
 */
class HttpEmulationRequestTest {

	@Test
	void testBuilderDefaults() {
		final HttpEmulationRequest request = HttpEmulationRequest.builder().build();
		assertNull(request.getMethod());
		assertNull(request.getPath());
		assertNull(request.getBody());
		assertNull(request.getHeaders());
	}

	@Test
	void testBuilderWithAllFields() {
		final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "text/plain");

		final HttpEmulationRequest request = HttpEmulationRequest
			.builder()
			.method("POST")
			.path("/api/v1/data")
			.body("{\"key\": \"value\"}")
			.headers(headers)
			.build();

		assertEquals("POST", request.getMethod());
		assertEquals("/api/v1/data", request.getPath());
		assertEquals("{\"key\": \"value\"}", request.getBody());
		assertEquals(headers, request.getHeaders());
	}

	@Test
	void testNoArgsConstructor() {
		final HttpEmulationRequest request = new HttpEmulationRequest();
		assertNull(request.getMethod());
		assertNull(request.getPath());
		assertNull(request.getBody());
		assertNull(request.getHeaders());
	}

	@Test
	void testAllArgsConstructor() {
		final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer token");

		final HttpEmulationRequest request = new HttpEmulationRequest("GET", "/test", "body", headers);

		assertEquals("GET", request.getMethod());
		assertEquals("/test", request.getPath());
		assertEquals("body", request.getBody());
		assertNotNull(request.getHeaders());
		assertEquals("Bearer token", request.getHeaders().get("Authorization"));
	}

	@Test
	void testSettersAndGetters() {
		final HttpEmulationRequest request = new HttpEmulationRequest();
		request.setMethod("PUT");
		request.setPath("/update");
		request.setBody("update body");
		final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		headers.put("X-Custom", "value");
		request.setHeaders(headers);

		assertEquals("PUT", request.getMethod());
		assertEquals("/update", request.getPath());
		assertEquals("update body", request.getBody());
		assertEquals("value", request.getHeaders().get("X-Custom"));
	}

	@Test
	void testEqualsAndHashCode() {
		final HttpEmulationRequest request1 = HttpEmulationRequest.builder().method("GET").path("/test").build();
		final HttpEmulationRequest request2 = HttpEmulationRequest.builder().method("GET").path("/test").build();
		assertEquals(request1, request2);
		assertEquals(request1.hashCode(), request2.hashCode());
	}
}
