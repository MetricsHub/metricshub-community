package org.metricshub.extension.emulation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.ResultContent;

/**
 * Tests for {@link HttpEmulationImage}.
 */
class HttpEmulationImageTest {

	@Test
	void testBuilderDefaults() {
		final HttpEmulationImage image = HttpEmulationImage.builder().build();
		assertNull(image.getImage());
	}

	@Test
	void testBuilderWithEntries() {
		final HttpEmulationEntry entry = HttpEmulationEntry
			.builder()
			.request(HttpEmulationRequest.builder().method("GET").path("/test").build())
			.response(HttpEmulationResponse.builder().file("response.txt").resultContent(ResultContent.BODY).build())
			.build();

		final HttpEmulationImage image = HttpEmulationImage.builder().image(List.of(entry)).build();

		assertNotNull(image.getImage());
		assertEquals(1, image.getImage().size());
		assertEquals(entry, image.getImage().get(0));
	}

	@Test
	void testNoArgsConstructor() {
		final HttpEmulationImage image = new HttpEmulationImage();
		assertNull(image.getImage());
	}

	@Test
	void testAllArgsConstructor() {
		final List<HttpEmulationEntry> entries = new ArrayList<>();
		entries.add(
			HttpEmulationEntry
				.builder()
				.request(HttpEmulationRequest.builder().method("GET").path("/one").build())
				.response(HttpEmulationResponse.builder().file("one.txt").build())
				.build()
		);
		entries.add(
			HttpEmulationEntry
				.builder()
				.request(HttpEmulationRequest.builder().method("POST").path("/two").build())
				.response(HttpEmulationResponse.builder().file("two.txt").build())
				.build()
		);

		final HttpEmulationImage image = new HttpEmulationImage(entries);
		assertEquals(2, image.getImage().size());
	}

	@Test
	void testSettersAndGetters() {
		final HttpEmulationImage image = new HttpEmulationImage();
		final List<HttpEmulationEntry> entries = List.of(
			HttpEmulationEntry
				.builder()
				.request(HttpEmulationRequest.builder().method("GET").path("/api").build())
				.response(HttpEmulationResponse.builder().file("api.txt").build())
				.build()
		);
		image.setImage(entries);

		assertNotNull(image.getImage());
		assertEquals(1, image.getImage().size());
	}

	@Test
	void testEmptyImage() {
		final HttpEmulationImage image = HttpEmulationImage.builder().image(List.of()).build();
		assertNotNull(image.getImage());
		assertTrue(image.getImage().isEmpty());
	}

	@Test
	void testEqualsAndHashCode() {
		final List<HttpEmulationEntry> entries = List.of(
			HttpEmulationEntry
				.builder()
				.request(HttpEmulationRequest.builder().method("GET").path("/test").build())
				.response(HttpEmulationResponse.builder().file("test.txt").build())
				.build()
		);

		final HttpEmulationImage image1 = HttpEmulationImage.builder().image(entries).build();
		final HttpEmulationImage image2 = HttpEmulationImage.builder().image(entries).build();

		assertEquals(image1, image2);
		assertEquals(image1.hashCode(), image2.hashCode());
	}
}
