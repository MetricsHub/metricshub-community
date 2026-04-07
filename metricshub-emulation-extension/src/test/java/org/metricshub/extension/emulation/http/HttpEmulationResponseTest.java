package org.metricshub.extension.emulation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.ResultContent;

/**
 * Test of {@link HttpEmulationResponse}
 */
class HttpEmulationResponseTest {

	@Test
	void testBuilderDefaults() {
		final HttpEmulationResponse response = HttpEmulationResponse.builder().build();
		assertNull(response.getFile());
		assertNull(response.getResultContent());
	}

	@Test
	void testBuilderWithAllFields() {
		final HttpEmulationResponse response = HttpEmulationResponse
			.builder()
			.file("response-body.txt")
			.resultContent(ResultContent.BODY)
			.build();

		assertEquals("response-body.txt", response.getFile());
		assertEquals(ResultContent.BODY, response.getResultContent());
	}

	@Test
	void testNoArgsConstructor() {
		final HttpEmulationResponse response = new HttpEmulationResponse();
		assertNull(response.getFile());
		assertNull(response.getResultContent());
	}

	@Test
	void testAllArgsConstructor() {
		final HttpEmulationResponse response = new HttpEmulationResponse("status.txt", ResultContent.HTTP_STATUS);
		assertEquals("status.txt", response.getFile());
		assertEquals(ResultContent.HTTP_STATUS, response.getResultContent());
	}

	@Test
	void testSettersAndGetters() {
		final HttpEmulationResponse response = new HttpEmulationResponse();
		response.setFile("headers.txt");
		response.setResultContent(ResultContent.HEADER);

		assertEquals("headers.txt", response.getFile());
		assertEquals(ResultContent.HEADER, response.getResultContent());
	}

	@Test
	void testEqualsAndHashCode() {
		final HttpEmulationResponse response1 = HttpEmulationResponse
			.builder()
			.file("response.txt")
			.resultContent(ResultContent.ALL)
			.build();
		final HttpEmulationResponse response2 = HttpEmulationResponse
			.builder()
			.file("response.txt")
			.resultContent(ResultContent.ALL)
			.build();
		assertEquals(response1, response2);
		assertEquals(response1.hashCode(), response2.hashCode());
	}

	@Test
	void testAllResultContentValues() {
		for (final ResultContent rc : ResultContent.values()) {
			final HttpEmulationResponse response = HttpEmulationResponse.builder().file("test.txt").resultContent(rc).build();
			assertEquals(rc, response.getResultContent());
		}
	}
}
