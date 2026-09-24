package org.metricshub.opamp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpampClientSettingsTest {

	private static OpampClientSettings.OpampClientSettingsBuilder builder() {
		return OpampClientSettings.builder()
			.withEndpoint(URI.create("https://opamp.example.com/v1/opamp"))
			.withInstanceUidFile(Path.of("instance-uid"));
	}

	@Test
	void headersShouldBeDefensivelyCopied() {
		final Map<String, String> mutable = new HashMap<>();
		mutable.put("Authorization", "Bearer token");

		final OpampClientSettings settings = builder().withHeaders(mutable).build();

		// Mutating the caller's map must not affect the settings: dropping the Authorization
		// header after construction would silently unauthenticate every later request
		mutable.remove("Authorization");
		mutable.put("X-Injected", "value");

		assertEquals(Map.of("Authorization", "Bearer token"), settings.getHeaders());
	}

	@Test
	void exposedHeadersShouldBeImmutable() {
		final OpampClientSettings settings = builder().withHeaders(Map.of("Authorization", "Bearer token")).build();

		assertThrows(UnsupportedOperationException.class, () -> settings.getHeaders().put("X-Injected", "value"));
	}

	@Test
	void headersShouldDefaultToAnEmptyMap() {
		assertEquals(Map.of(), builder().build().getHeaders());
	}
}
