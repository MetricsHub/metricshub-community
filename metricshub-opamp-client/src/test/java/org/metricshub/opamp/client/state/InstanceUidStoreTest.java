package org.metricshub.opamp.client.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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

	@Test
	void loadOrCreateShouldGiveEveryFleetChannelTheSameUid() throws Exception {
		// An agent has more than one fleet channel, and they are supervised by separate threads: on
		// a fresh installation both can find no file, both generate, and both write. Check then
		// generate then replace means each caller returns its OWN uid, the two channels register as
		// different agents, and after a restart whichever lost the write changes identity.
		final Path file = tempDir.resolve("shared").resolve("instance-uid");
		final int channels = 8;
		final CyclicBarrier together = new CyclicBarrier(channels);

		final List<byte[]> loaded;
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			final List<Future<byte[]>> claims = IntStream.range(0, channels)
				.mapToObj(index ->
					pool.submit(() -> {
						together.await(10, TimeUnit.SECONDS);
						// A store each, as the real callers have: the OpAMP client builds its own
						return new InstanceUidStore(file).loadOrCreate();
					})
				)
				.toList();
			loaded = claims.stream().map(InstanceUidStoreTest::join).toList();
		}

		final byte[] persisted = UuidV7.fromCanonicalString(Files.readString(file, StandardCharsets.UTF_8));
		for (final byte[] uid : loaded) {
			assertArrayEquals(
				persisted,
				uid,
				"Every channel must be handed the uid that is actually on disk, not the one it generated"
			);
		}
	}

	private static byte[] join(final Future<byte[]> claim) {
		try {
			return claim.get(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
