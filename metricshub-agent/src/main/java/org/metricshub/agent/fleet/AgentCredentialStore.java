package org.metricshub.agent.fleet;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.metricshub.engine.security.MetricsHubSecurityException;
import org.metricshub.engine.security.SecurityManager;

/**
 * Owns the identity an agent receives when it enrolls into Central, in the MetricsHub {@code security}
 * directory, never in {@code metricshub.yaml}:
 * <ul>
 * <li>the agent uid in {@code metricshub-agent-uid} ({@link AgentInstanceUid#FILENAME});</li>
 * <li>its client secret in {@code metricshub-keystore.p12} under {@value #SECRET_ALIAS};</li>
 * <li>the recovery proof of the enrollment attempt under {@value #RECOVERY_ALIAS}, until the first token;</li>
 * <li>the local state in {@value #STATE_FILENAME}: {@code pending} with the attempt id, then {@code ready}.</li>
 * </ul>
 * <p>
 * Each transition holds an inter-process lock on {@value #LOCK_FILENAME}, and each write replaces its file
 * atomically after forcing it to disk, so a crash between two writes leaves a state {@link #read()} classifies
 * and the enrollment resumes with the same attempt, never a new one. {@code ready} is written only after the uid
 * and the secret read back.
 * </p>
 * <p>
 * <b>Audit note:</b> the KeyStore password is a constant shipped in the binary ({@link SecurityManager}), so the
 * secret at rest is protected by the file modes this class applies ({@code 0700} on the directory, {@code 0600}
 * on its files, or an access list reduced to the principals that may already change it on Windows), not by the
 * KeyStore. This is a known deviation, accepted for the MVP.
 * </p>
 */
public final class AgentCredentialStore {

	/**
	 * KeyStore alias of the agent's client secret.
	 */
	public static final String SECRET_ALIAS = "metricshub-agent-secret";

	/**
	 * KeyStore alias of the recovery proof of the pending enrollment attempt.
	 */
	public static final String RECOVERY_ALIAS = "metricshub-enrollment-recovery";

	/**
	 * Name of the local state file.
	 */
	public static final String STATE_FILENAME = "metricshub-agent-enrollment.json";

	/**
	 * Name of the lock file.
	 */
	public static final String LOCK_FILENAME = "metricshub-agent.lock";

	private static final String STATE = "state";
	private static final String ATTEMPT_ID = "attempt_id";
	private static final String PENDING = "pending";
	private static final String READY = "ready";

	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Serializes this process's transitions; the file lock serializes processes. A {@link FileLock} cannot be
	 * taken twice in one JVM, so the file lock is taken by the outermost holder only.
	 */
	private static final ReentrantLock PROCESS_LOCK = new ReentrantLock();

	/**
	 * How {@link #read()} classifies the local identity.
	 */
	public enum Status {
		/**
		 * No enrollment was started.
		 */
		ABSENT,
		/**
		 * An attempt is recorded, and neither its uid nor its secret is stored.
		 */
		PENDING,
		/**
		 * The uid or the secret is missing or not confirmed: the same attempt is resumed. Without an attempt id
		 * (a {@code ready} state whose uid or secret was removed), nothing can be resumed.
		 */
		INCOMPLETE,
		/**
		 * The uid and the secret are stored and read back.
		 */
		READY
	}

	/**
	 * The local identity. Its secrets are arrays, so the record's {@code toString()} never shows them.
	 *
	 * @param status         the classification
	 * @param attemptId      the enrollment attempt to resume, or {@code null}
	 * @param agentUid       the agent uid, or {@code null}
	 * @param secret         the client secret when {@link Status#READY}, else {@code null}
	 * @param recoverySecret the recovery proof of the attempt to resume, or {@code null}
	 */
	public record LocalIdentity(Status status, String attemptId, String agentUid, char[] secret, byte[] recoverySecret) {}

	/**
	 * A transition run under the lock.
	 *
	 * @param <T> the result type
	 */
	@FunctionalInterface
	public interface LockedAction<T> {
		/**
		 * @return the result
		 * @throws IOException when the transition fails
		 */
		T run() throws IOException;
	}

	private final Path directory;
	private final Consumer<String> afterWrite;

	/**
	 * Creates a store in the given directory.
	 *
	 * @param directory the MetricsHub {@code security} directory
	 */
	public AgentCredentialStore(final Path directory) {
		this(directory, write -> {});
	}

	/**
	 * Creates a store that reports each completed write, so tests can interrupt a transition between two
	 * writes.
	 *
	 * @param directory  the MetricsHub {@code security} directory
	 * @param afterWrite called with the name of each file written
	 */
	AgentCredentialStore(final Path directory, final Consumer<String> afterWrite) {
		this.directory = directory.toAbsolutePath();
		this.afterWrite = afterWrite;
	}

	/**
	 * @return the store of the MetricsHub {@code security} directory, where {@link AgentInstanceUid#file()} is
	 */
	public static AgentCredentialStore standard() {
		return new AgentCredentialStore(AgentInstanceUid.file().getParent());
	}

	/**
	 * Runs an action holding this process's lock and the inter-process lock. Reentrant within a thread.
	 *
	 * @param <T>    the result type
	 * @param action the action
	 * @return the action's result
	 * @throws IOException when the lock cannot be taken or the action fails
	 */
	public <T> T locked(final LockedAction<T> action) throws IOException {
		PROCESS_LOCK.lock();
		try {
			if (PROCESS_LOCK.getHoldCount() > 1) {
				return action.run();
			}
			Files.createDirectories(directory);
			restrict(directory);
			final Path lockFile = directory.resolve(LOCK_FILENAME);
			try (
				FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				FileLock lock = channel.lock()
			) {
				restrict(lockFile);
				return action.run();
			}
		} finally {
			PROCESS_LOCK.unlock();
		}
	}

	/**
	 * Reads and classifies the local identity.
	 *
	 * @return the local identity
	 * @throws IOException when a file cannot be read, or the state file is not one this class writes
	 */
	public LocalIdentity read() throws IOException {
		return locked(() -> {
			final JsonNode state = readState();
			if (state == null) {
				return new LocalIdentity(Status.ABSENT, null, null, null, null);
			}
			final String agentUid = readUid();
			final byte[] secret = readSecret(SECRET_ALIAS);
			final String name = state.path(STATE).asText();
			if (READY.equals(name)) {
				if (agentUid != null && secret != null) {
					return new LocalIdentity(Status.READY, null, agentUid, toChars(secret), null);
				}
				wipe(secret);
				return new LocalIdentity(Status.INCOMPLETE, null, agentUid, null, null);
			}
			final boolean secretStored = secret != null;
			wipe(secret);
			final String attemptId = state.path(ATTEMPT_ID).asText(null);
			final byte[] recoverySecret = readSecret(RECOVERY_ALIAS);
			if (!PENDING.equals(name) || !isCanonicalUuid(attemptId) || recoverySecret == null) {
				wipe(recoverySecret);
				throw new IOException("The enrollment state in " + directory.resolve(STATE_FILENAME) + " is not valid.");
			}
			final Status status = agentUid == null && !secretStored ? Status.PENDING : Status.INCOMPLETE;
			return new LocalIdentity(status, attemptId, null, null, recoverySecret);
		});
	}

	/**
	 * Records a new enrollment attempt, before its first request: the recovery proof first, then the
	 * {@code pending} state, which is what makes the attempt exist. A uid or a secret left without a state (an agent
	 * that ran without enrollment) is removed, so no uid file exists before {@code ready}. It never replaces an
	 * attempt or an identity: an interrupted replacement would pair the old attempt with the new proof.
	 *
	 * @param attemptId      the attempt id, a canonical UUID
	 * @param recoverySecret the recovery proof
	 * @throws IOException when a state already exists, or a write fails
	 */
	public void beginAttempt(final String attemptId, final byte[] recoverySecret) throws IOException {
		if (!isCanonicalUuid(attemptId)) {
			throw new IllegalArgumentException("The attempt id is not a canonical UUID.");
		}
		if (recoverySecret == null || recoverySecret.length == 0) {
			throw new IllegalArgumentException("The recovery proof is empty.");
		}
		locked(() -> {
			if (readState() != null) {
				throw new IOException("An enrollment attempt or identity already exists in " + directory + ".");
			}
			final Map<String, byte[]> secrets = new HashMap<>();
			secrets.put(RECOVERY_ALIAS, recoverySecret);
			secrets.put(SECRET_ALIAS, null);
			updateSecrets(secrets);
			Files.deleteIfExists(uidFile());
			writeState(Map.of(STATE, PENDING, ATTEMPT_ID, attemptId));
			return null;
		});
	}

	/**
	 * Stores the identity the pending attempt received, in order: the secret, the uid, then, once both read back,
	 * the {@code ready} state. Interrupted, it leaves an {@link Status#INCOMPLETE} identity with the same attempt.
	 *
	 * @param agentUid the agent uid, a canonical UUID
	 * @param secret   the client secret
	 * @throws IOException when no attempt is pending, a write fails or a value does not read back
	 */
	public void complete(final String agentUid, final char[] secret) throws IOException {
		if (!isCanonicalUuid(agentUid)) {
			throw new IllegalArgumentException("The agent uid is not a canonical UUID.");
		}
		locked(() -> {
			final JsonNode state = readState();
			if (state == null || !PENDING.equals(state.path(STATE).asText())) {
				throw new IOException("No enrollment attempt is pending in " + directory + ".");
			}
			final byte[] secretBytes = toBytes(secret);
			try {
				updateSecrets(Map.of(SECRET_ALIAS, secretBytes));
				writeAtomically(uidFile(), agentUid.getBytes(StandardCharsets.UTF_8));
				final byte[] storedSecret = readSecret(SECRET_ALIAS);
				final boolean readBack = agentUid.equals(readUid()) && Arrays.equals(secretBytes, storedSecret);
				if (storedSecret != null) {
					Arrays.fill(storedSecret, (byte) 0);
				}
				if (!readBack) {
					throw new IOException("The agent identity did not read back from " + directory + ".");
				}
			} finally {
				Arrays.fill(secretBytes, (byte) 0);
			}
			writeState(Map.of(STATE, READY));
			return null;
		});
	}

	/**
	 * Removes the recovery proof, which is void once the agent obtained its first token. Does nothing when it is
	 * already gone.
	 *
	 * @throws IOException when the KeyStore cannot be written
	 */
	public void clearRecovery() throws IOException {
		locked(() -> {
			final byte[] recoverySecret = readSecret(RECOVERY_ALIAS);
			if (recoverySecret != null) {
				wipe(recoverySecret);
				final Map<String, byte[]> secrets = new HashMap<>();
				secrets.put(RECOVERY_ALIAS, null);
				updateSecrets(secrets);
			}
			return null;
		});
	}

	/**
	 * @return the uid file, {@link AgentInstanceUid#FILENAME} in this store's directory
	 */
	public Path uidFile() {
		return directory.resolve(AgentInstanceUid.FILENAME);
	}

	private Path keyStoreFile() {
		return directory.resolve(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME);
	}

	private JsonNode readState() throws IOException {
		try {
			return JSON.readTree(Files.readAllBytes(directory.resolve(STATE_FILENAME)));
		} catch (NoSuchFileException e) {
			return null;
		}
	}

	private void writeState(final Map<String, String> state) throws IOException {
		writeAtomically(directory.resolve(STATE_FILENAME), JSON.writeValueAsBytes(state));
	}

	/**
	 * @return the canonical uid in the uid file, or {@code null} when the file is missing or holds no canonical
	 *         UUID
	 */
	private String readUid() throws IOException {
		try {
			final String uid = Files.readString(uidFile(), StandardCharsets.UTF_8).trim();
			return isCanonicalUuid(uid) ? uid : null;
		} catch (NoSuchFileException e) {
			return null;
		}
	}

	private byte[] readSecret(final String alias) throws IOException {
		try {
			return SecurityManager.readSecret(keyStoreFile().toFile(), alias);
		} catch (MetricsHubSecurityException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	private void updateSecrets(final Map<String, byte[]> secrets) throws IOException {
		try {
			SecurityManager.updateSecrets(keyStoreFile().toFile(), secrets);
		} catch (MetricsHubSecurityException e) {
			throw new IOException(e.getMessage(), e);
		}
		restrict(keyStoreFile());
		afterWrite.accept(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME);
	}

	/**
	 * Writes a file through a temporary file in the same directory, forced to disk, then moved over the target.
	 */
	private void writeAtomically(final Path target, final byte[] content) throws IOException {
		final Path temporaryFile = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
		try {
			restrict(temporaryFile);
			try (FileOutputStream stream = new FileOutputStream(temporaryFile.toFile())) {
				stream.write(content);
				stream.getFD().sync();
			}
			SecurityManager.moveDurably(temporaryFile, target);
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
		afterWrite.accept(target.getFileName().toString());
	}

	/**
	 * Restricts a path to its owner: {@code 0700} for a directory and {@code 0600} for a file on POSIX systems. On
	 * Windows, the access list keeps its deny entries, first, then allows full control to the owner and to the
	 * principals that may already change it (SYSTEM, Administrators), so no other user or group keeps any access and
	 * a denied principal stays denied; entries inherit to the files of a directory. Names are never looked up, so
	 * this holds whatever the system's language.
	 *
	 * @param path a file or directory
	 * @throws IOException when the permissions cannot be set
	 */
	static void restrict(final Path path) throws IOException {
		final boolean isDirectory = Files.isDirectory(path);
		final PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
		if (posix != null) {
			posix.setPermissions(PosixFilePermissions.fromString(isDirectory ? "rwx------" : "rw-------"));
			return;
		}
		final AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
		if (acl == null) {
			return;
		}
		final Set<AclEntryFlag> flags = isDirectory
			? EnumSet.of(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
			: EnumSet.noneOf(AclEntryFlag.class);
		final UserPrincipal owner = acl.getOwner();
		final List<AclEntry> entries = new ArrayList<>();
		for (final AclEntry entry : acl.getAcl()) {
			if (entry.type() == AclEntryType.DENY) {
				entries.add(entry);
			}
		}
		entries.add(fullControl(owner, flags));
		for (final AclEntry entry : acl.getAcl()) {
			if (
				entry.type() == AclEntryType.ALLOW &&
					entry.permissions().contains(AclEntryPermission.WRITE_ACL) &&
					!entry.flags().contains(AclEntryFlag.INHERIT_ONLY) &&
					!entry.principal().equals(owner)
			) {
				entries.add(fullControl(entry.principal(), flags));
			}
		}
		acl.setAcl(entries);
	}

	private static AclEntry fullControl(final UserPrincipal principal, final Set<AclEntryFlag> flags) {
		return AclEntry.newBuilder()
			.setType(AclEntryType.ALLOW)
			.setPrincipal(principal)
			.setPermissions(EnumSet.allOf(AclEntryPermission.class))
			.setFlags(flags)
			.build();
	}

	private static boolean isCanonicalUuid(final String value) {
		if (value == null) {
			return false;
		}
		try {
			return UUID.fromString(value).toString().equals(value);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void wipe(final byte[] secret) {
		if (secret != null) {
			Arrays.fill(secret, (byte) 0);
		}
	}

	private static byte[] toBytes(final char[] chars) {
		final ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
		final byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		Arrays.fill(buffer.array(), (byte) 0);
		return bytes;
	}

	private static char[] toChars(final byte[] bytes) {
		final CharBuffer buffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
		final char[] chars = new char[buffer.remaining()];
		buffer.get(chars);
		Arrays.fill(buffer.array(), '\0');
		Arrays.fill(bytes, (byte) 0);
		return chars;
	}
}
