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

	@Test
	void suppliedHeadersShouldBeAnImmutableSnapshot() {
		final Map<String, String> backing = new HashMap<>();
		backing.put("Authorization", "Bearer token");
		final OpampClientSettings settings = builder().withHeadersSupplier(() -> backing).build();

		final Map<String, String> current = settings.currentHeaders();

		assertThrows(UnsupportedOperationException.class, () -> current.put("Authorization", "Bearer injected"));
		backing.put("Authorization", "Bearer renewed");
		assertEquals(Map.of("Authorization", "Bearer token"), current);
		assertEquals(Map.of(), builder().withHeadersSupplier(() -> null).build().currentHeaders());
	}

	@Test
	void theConstructorWithoutSupplierShouldRemain() {
		final OpampClientSettings settings = new OpampClientSettings(
			URI.create("https://opamp.example.com/v1/opamp"),
			Map.of("Authorization", "Bearer token"),
			null,
			OpampClientSettings.DEFAULT_POLL_INTERVAL,
			OpampClientSettings.DEFAULT_REQUEST_TIMEOUT,
			OpampClientSettings.DEFAULT_MAX_BACKOFF,
			Path.of("instance-uid"),
			true
		);

		assertEquals(Map.of("Authorization", "Bearer token"), settings.currentHeaders());
		settings.getOnUnauthorized().run();
	}
}
