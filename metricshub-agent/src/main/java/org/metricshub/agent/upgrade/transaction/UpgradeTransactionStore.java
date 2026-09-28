package org.metricshub.agent.upgrade.transaction;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads and writes the {@link UpgradeTransaction} JSON file in the upgrade staging directory.
 * Writes are atomic (temporary file then move) so a crash can never leave a half-written
 * transaction behind.
 */
@Slf4j
public class UpgradeTransactionStore {

	/**
	 * Name of the transaction file in the staging directory.
	 */
	public static final String TRANSACTION_FILE_NAME = "upgrade-transaction.json";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final Path stagingDirectory;
	private final Path transactionFile;

	/**
	 * Creates a store backed by the given staging directory.
	 *
	 * @param stagingDirectory the upgrade staging directory
	 */
	public UpgradeTransactionStore(final Path stagingDirectory) {
		this.stagingDirectory = stagingDirectory;
		this.transactionFile = stagingDirectory.resolve(TRANSACTION_FILE_NAME);
	}

	/**
	 * Reads the pending transaction.
	 *
	 * @return the transaction, or {@code null} when none exists or the file is corrupt (a corrupt
	 *         file is archived with a {@code .corrupt} suffix)
	 */
	public UpgradeTransaction read() {
		if (!Files.isRegularFile(transactionFile)) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(
				Files.readString(transactionFile, StandardCharsets.UTF_8),
				UpgradeTransaction.class
			);
		} catch (IOException e) {
			log.error("Corrupt upgrade transaction file {}: {}", transactionFile, e.getMessage());
			archiveCorrupt();
			return null;
		}
	}

	/**
	 * Persists the transaction atomically, updating its {@code updatedAt} timestamp.
	 *
	 * @param transaction the transaction to persist
	 * @throws IOException when the transaction cannot be written
	 */
	public void write(final UpgradeTransaction transaction) throws IOException {
		transaction.setUpdatedAt(System.currentTimeMillis());
		Files.createDirectories(stagingDirectory);
		final Path temporaryFile = Files.createTempFile(stagingDirectory, TRANSACTION_FILE_NAME, ".tmp");
		try {
			Files.writeString(
				temporaryFile,
				OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(transaction),
				StandardCharsets.UTF_8
			);
			try {
				Files.move(temporaryFile, transactionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporaryFile, transactionFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	/**
	 * Archives the current transaction file with a suffix carrying the upgrade identifier, so the
	 * last outcomes remain available for troubleshooting.
	 *
	 * @param transaction the transaction being archived
	 */
	public void archive(final UpgradeTransaction transaction) {
		if (!Files.isRegularFile(transactionFile)) {
			return;
		}
		final String suffix =
			transaction != null && transaction.getUpgradeId() != null
				? transaction.getUpgradeId()
				: String.valueOf(System.currentTimeMillis());
		try {
			Files.move(
				transactionFile,
				stagingDirectory.resolve(TRANSACTION_FILE_NAME + "." + suffix),
				StandardCopyOption.REPLACE_EXISTING
			);
		} catch (IOException e) {
			log.warn("Cannot archive the upgrade transaction file {}: {}", transactionFile, e.getMessage());
		}
	}

	/**
	 * Identity of the currently installed package as learned from a completed OpAMP upgrade.
	 *
	 * @param version     the installed version
	 * @param packageHash the installed package identity hash, hexadecimal
	 */
	public record InstalledPackageRecord(String version, String packageHash) {}

	/**
	 * Name of the file recording the installed package identity.
	 */
	public static final String INSTALLED_PACKAGE_FILE_NAME = "installed-package.json";

	/**
	 * Reads the installed package identity record.
	 *
	 * @return the record, or {@code null} when none exists or it cannot be parsed
	 */
	public InstalledPackageRecord readInstalledPackage() {
		final Path file = stagingDirectory.resolve(INSTALLED_PACKAGE_FILE_NAME);
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), InstalledPackageRecord.class);
		} catch (IOException e) {
			log.warn("Cannot read the installed package record {}: {}", file, e.getMessage());
			return null;
		}
	}

	/**
	 * Persists the installed package identity record atomically.
	 *
	 * @param record the record to persist
	 */
	public void writeInstalledPackage(final InstalledPackageRecord record) {
		final Path file = stagingDirectory.resolve(INSTALLED_PACKAGE_FILE_NAME);
		try {
			Files.createDirectories(stagingDirectory);
			final Path temporaryFile = Files.createTempFile(stagingDirectory, INSTALLED_PACKAGE_FILE_NAME, ".tmp");
			try {
				Files.writeString(temporaryFile, OBJECT_MAPPER.writeValueAsString(record), StandardCharsets.UTF_8);
				try {
					Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(temporaryFile);
			}
		} catch (IOException e) {
			log.warn("Cannot persist the installed package record {}: {}", file, e.getMessage());
		}
	}

	/**
	 * Renames a corrupt transaction file so it does not block subsequent upgrades.
	 */
	private void archiveCorrupt() {
		try {
			Files.move(
				transactionFile,
				stagingDirectory.resolve(TRANSACTION_FILE_NAME + ".corrupt"),
				StandardCopyOption.REPLACE_EXISTING
			);
		} catch (IOException e) {
			log.warn("Cannot archive the corrupt upgrade transaction file {}: {}", transactionFile, e.getMessage());
		}
	}
}
