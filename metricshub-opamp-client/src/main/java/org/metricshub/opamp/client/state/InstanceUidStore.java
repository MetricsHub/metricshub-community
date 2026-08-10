package org.metricshub.opamp.client.state;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists the OpAMP agent {@code instance_uid} (a UUIDv7) as a canonical UUID string in a file,
 * so the agent keeps the same identity across restarts and upgrades. Writes are atomic (temporary
 * file then move).
 */
@Slf4j
public class InstanceUidStore {

	private final Path file;

	/**
	 * Creates a store backed by the given file.
	 *
	 * @param file the file in which the instance UID is persisted
	 */
	public InstanceUidStore(final Path file) {
		this.file = file;
	}

	/**
	 * Loads the persisted instance UID, or generates and persists a new UUIDv7 when the file does
	 * not exist or does not contain a valid UUID.
	 *
	 * @return the 16-byte instance UID
	 * @throws IOException if the instance UID cannot be read or persisted
	 */
	public byte[] loadOrCreate() throws IOException {
		if (Files.isRegularFile(file)) {
			try {
				return UuidV7.fromCanonicalString(Files.readString(file, StandardCharsets.UTF_8));
			} catch (IllegalArgumentException e) {
				log.warn("Invalid OpAMP instance UID in {}; a new instance UID is generated.", file);
			}
		}
		final byte[] instanceUid = UuidV7.generate();
		store(instanceUid);
		return instanceUid;
	}

	/**
	 * Persists the given instance UID atomically. Used both at creation time and when the server
	 * assigns a new identity through {@code AgentIdentification.new_instance_uid}.
	 *
	 * @param instanceUid the 16-byte instance UID to persist
	 * @throws IOException if the instance UID cannot be persisted
	 */
	public void store(final byte[] instanceUid) throws IOException {
		final String canonical = UuidV7.toCanonicalString(instanceUid);
		final Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		final Path temporaryFile = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporaryFile, canonical, StandardCharsets.UTF_8);
			try {
				Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}
}
