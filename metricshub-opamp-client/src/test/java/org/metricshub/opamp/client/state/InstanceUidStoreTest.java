package org.metricshub.opamp.client.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceUidStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void loadOrCreateShouldGenerateAndPersistWhenMissing() throws IOException {
		final Path file = tempDir.resolve("instance-uid");
		final InstanceUidStore store = new InstanceUidStore(file);

		final byte[] created = store.loadOrCreate();

		assertEquals(16, created.length);
		assertTrue(Files.isRegularFile(file));
		assertEquals(UuidV7.toCanonicalString(created), Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void loadOrCreateShouldReturnPersistedValue() throws IOException {
		final Path file = tempDir.resolve("instance-uid");
		final InstanceUidStore store = new InstanceUidStore(file);

		final byte[] first = store.loadOrCreate();
		final byte[] second = new InstanceUidStore(file).loadOrCreate();

		assertArrayEquals(first, second);
	}

	@Test
	void loadOrCreateShouldRegenerateOnCorruptedContent() throws IOException {
		final Path file = tempDir.resolve("instance-uid");
		Files.writeString(file, "corrupted-content");

		final byte[] created = new InstanceUidStore(file).loadOrCreate();

		assertEquals(16, created.length);
		assertFalse(Files.readString(file, StandardCharsets.UTF_8).equals("corrupted-content"));
	}

	@Test
	void storeShouldPersistAdoptedUid() throws IOException {
		final Path file = tempDir.resolve("nested").resolve("instance-uid");
		final InstanceUidStore store = new InstanceUidStore(file);

		final byte[] adopted = UuidV7.generate();
		store.store(adopted);

		assertArrayEquals(adopted, store.loadOrCreate());
	}
}
