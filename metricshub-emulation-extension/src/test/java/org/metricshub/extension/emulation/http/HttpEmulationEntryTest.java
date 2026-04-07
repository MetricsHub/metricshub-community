package org.metricshub.extension.emulation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.ResultContent;

/**
 * Test of {@link HttpEmulationEntry}
 */
class HttpEmulationEntryTest {

	@Test
	void testBuilderDefaults() {
		final HttpEmulationEntry entry = HttpEmulationEntry.builder().build();
		assertNull(entry.getRequest());
		assertNull(entry.getResponse());
	}

	@Test
	void testBuilderWithFields() {
		final HttpEmulationRequest request = HttpEmulationRequest.builder().method("GET").path("/api").build();
		final HttpEmulationResponse response = HttpEmulationResponse
			.builder()
			.file("response.txt")
			.resultContent(ResultContent.BODY)
			.build();

		final HttpEmulationEntry entry = HttpEmulationEntry.builder().request(request).response(response).build();

		assertEquals(request, entry.getRequest());
		assertEquals(response, entry.getResponse());
	}

	@Test
	void testNoArgsConstructor() {
		final HttpEmulationEntry entry = new HttpEmulationEntry();
		assertNull(entry.getRequest());
		assertNull(entry.getResponse());
	}

	@Test
	void testAllArgsConstructor() {
		final HttpEmulationRequest request = HttpEmulationRequest.builder().method("POST").build();
		final HttpEmulationResponse response = HttpEmulationResponse.builder().file("output.json").build();

		final HttpEmulationEntry entry = new HttpEmulationEntry(request, response);
		assertEquals(request, entry.getRequest());
		assertEquals(response, entry.getResponse());
	}

	@Test
	void testSettersAndGetters() {
		final HttpEmulationEntry entry = new HttpEmulationEntry();
		final HttpEmulationRequest request = HttpEmulationRequest.builder().method("DELETE").path("/item/1").build();
		final HttpEmulationResponse response = HttpEmulationResponse.builder().file("deleted.txt").build();

		entry.setRequest(request);
		entry.setResponse(response);

		assertEquals(request, entry.getRequest());
		assertEquals(response, entry.getResponse());
	}

	@Test
	void testEqualsAndHashCode() {
		final HttpEmulationRequest request = HttpEmulationRequest.builder().method("GET").path("/test").build();
		final HttpEmulationResponse response = HttpEmulationResponse.builder().file("test.txt").build();

		final HttpEmulationEntry entry1 = HttpEmulationEntry.builder().request(request).response(response).build();
		final HttpEmulationEntry entry2 = HttpEmulationEntry.builder().request(request).response(response).build();

		assertEquals(entry1, entry2);
		assertEquals(entry1.hashCode(), entry2.hashCode());
	}
}
