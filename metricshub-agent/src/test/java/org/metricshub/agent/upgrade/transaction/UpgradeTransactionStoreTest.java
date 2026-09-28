package org.metricshub.agent.upgrade.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.upgrade.UpgradeState;

class UpgradeTransactionStoreTest {

	@TempDir
	Path tempDir;

	private UpgradeTransaction transaction() {
		return UpgradeTransaction.builder()
			.upgradeId("test-upgrade")
			.packageName("metricshub")
			.fromVersion("3.9.05")
			.toVersion("3.10.00")
			.downloadUrl("https://repo.example.com/metricshub.deb")
			.sha256("abcd")
			.deploymentKind("DEB")
			.state(UpgradeState.DOWNLOADING)
			.createdAt(123L)
			.installTimeoutSeconds(1800)
			.build();
	}

	@Test
	void writeAndReadShouldRoundTrip() throws IOException {
		final UpgradeTransactionStore store = new UpgradeTransactionStore(tempDir);
		store.write(transaction());

		final UpgradeTransaction read = store.read();

		assertEquals("test-upgrade", read.getUpgradeId());
		assertEquals(UpgradeState.DOWNLOADING, read.getState());
		assertEquals("3.10.00", read.getToVersion());
		assertTrue(read.getUpdatedAt() > 0);
	}

	@Test
	void readShouldReturnNullWhenNoTransactionExists() {
		assertNull(new UpgradeTransactionStore(tempDir).read());
	}

	@Test
	void corruptTransactionShouldBeArchivedAndIgnored() throws IOException {
		final UpgradeTransactionStore store = new UpgradeTransactionStore(tempDir);
		Files.writeString(tempDir.resolve(UpgradeTransactionStore.TRANSACTION_FILE_NAME), "{not json");

		assertNull(store.read());
		assertTrue(Files.exists(tempDir.resolve(UpgradeTransactionStore.TRANSACTION_FILE_NAME + ".corrupt")));
		assertNull(store.read());
	}

	@Test
	void archiveShouldRenameTheTransactionFile() throws IOException {
		final UpgradeTransactionStore store = new UpgradeTransactionStore(tempDir);
		final UpgradeTransaction transaction = transaction();
		store.write(transaction);

		store.archive(transaction);

		assertNull(store.read());
		assertTrue(Files.exists(tempDir.resolve(UpgradeTransactionStore.TRANSACTION_FILE_NAME + ".test-upgrade")));
	}
}
