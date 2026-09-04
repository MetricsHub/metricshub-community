package org.metricshub.agent.upgrade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpgradeLockTest {

	@TempDir
	Path tempDir;

	@Test
	void lockShouldBeExclusive() {
		final UpgradeLock lock = new UpgradeLock(tempDir);

		assertTrue(lock.tryAcquire());
		assertFalse(lock.tryAcquire());
		assertTrue(Files.exists(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME)));

		lock.release();
		assertFalse(Files.exists(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME)));
		assertTrue(lock.tryAcquire());
	}

	@Test
	void staleLockFileShouldBlockUntilReleased() throws Exception {
		Files.createDirectories(tempDir);
		Files.createFile(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME));
		final UpgradeLock lock = new UpgradeLock(tempDir);

		// The stale file left by a previous process blocks acquisition
		assertFalse(lock.tryAcquire());

		lock.releaseStale();
		assertTrue(lock.tryAcquire());
	}
}
