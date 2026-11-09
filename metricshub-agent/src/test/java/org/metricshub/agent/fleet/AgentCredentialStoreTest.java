package org.metricshub.agent.fleet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.agent.fleet.AgentCredentialStore.LocalIdentity;
import org.metricshub.agent.fleet.AgentCredentialStore.Status;
import org.metricshub.engine.security.SecurityManager;

class AgentCredentialStoreTest {

	private static final String ATTEMPT_ID = "a5ccfb10-f1fa-4ae9-803d-32bd1b74b49d";
	private static final String AGENT_UID = "0192a6f4-7c1e-7b3a-9f20-4d5e6a7b8c91";
	private static final String SECRET = "c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U";
	private static final byte[] RECOVERY = "cmVjb3ZlcnlyZWNvdmVyeXJlY292ZXJ5cmVjb3Zlcnk".getBytes(
		StandardCharsets.US_ASCII
	);

	@TempDir
	Path directory;

	private File keyStoreFile() {
		return directory.resolve(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME).toFile();
	}

	@Test
	void shouldGoFromAbsentToReadyKeepingTheMasterKey() throws Exception {
		final char[] encrypted = SecurityManager.encrypt("password".toCharArray(), keyStoreFile());
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		assertEquals(Status.ABSENT, store.read().status());

		store.beginAttempt(ATTEMPT_ID, RECOVERY);
		final LocalIdentity pending = store.read();
		assertEquals(Status.PENDING, pending.status());
		assertEquals(ATTEMPT_ID, pending.attemptId());
		assertArrayEquals(RECOVERY, pending.recoverySecret());

		store.complete(AGENT_UID, SECRET.toCharArray());
		final LocalIdentity ready = store.read();
		assertEquals(Status.READY, ready.status());
		assertEquals(AGENT_UID, ready.agentUid());
		assertArrayEquals(SECRET.toCharArray(), ready.secret());
		assertEquals(AGENT_UID, Files.readString(store.uidFile()));

		store.clearRecovery();
		assertNull(SecurityManager.readSecret(keyStoreFile(), AgentCredentialStore.RECOVERY_ALIAS));
		assertEquals(Status.READY, store.read().status());
		assertArrayEquals("password".toCharArray(), SecurityManager.decrypt(encrypted, keyStoreFile()));
	}

	@ParameterizedTest
	@ValueSource(strings = { SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME, AgentInstanceUid.FILENAME })
	void anInterruptedCompletionShouldResumeTheSameAttempt(final String interruptedAfter) throws Exception {
		new AgentCredentialStore(directory).beginAttempt(ATTEMPT_ID, RECOVERY);
		final AgentCredentialStore crashing = new AgentCredentialStore(directory, written -> {
			if (written.equals(interruptedAfter)) {
				throw new IllegalStateException("crash after " + written);
			}
		});
		assertThrows(IllegalStateException.class, () -> crashing.complete(AGENT_UID, SECRET.toCharArray()));

		final LocalIdentity reopened = new AgentCredentialStore(directory).read();
		assertEquals(Status.INCOMPLETE, reopened.status());
		assertEquals(ATTEMPT_ID, reopened.attemptId());
		assertArrayEquals(RECOVERY, reopened.recoverySecret());
	}

	@Test
	void beginAttemptShouldRemoveAUidLeftByAnAgentWithoutEnrollment() throws Exception {
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		AgentInstanceUid.loadOrCreate(store.uidFile());

		store.beginAttempt(ATTEMPT_ID, RECOVERY);

		assertFalse(Files.exists(store.uidFile()));
		assertEquals(Status.PENDING, store.read().status());
	}

	@Test
	void beginAttemptShouldNeverReplaceAnAttempt() throws Exception {
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		store.beginAttempt(ATTEMPT_ID, RECOVERY);

		assertThrows(IOException.class, () -> store.beginAttempt("b5ccfb10-f1fa-4ae9-803d-32bd1b74b49d", new byte[] { 1 }));

		final LocalIdentity pending = store.read();
		assertEquals(ATTEMPT_ID, pending.attemptId());
		assertArrayEquals(RECOVERY, pending.recoverySecret());
	}

	@Test
	void restrictShouldKeepTheDenyEntriesOnWindows() throws Exception {
		final Path file = Files.createFile(directory.resolve("denied"));
		final AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
		assumeTrue(view != null && Files.getFileAttributeView(file, PosixFileAttributeView.class) == null);
		final UserPrincipal users = FileSystems.getDefault()
			.getUserPrincipalLookupService()
			.lookupPrincipalByName("BUILTIN\\Users");
		final List<AclEntry> entries = new ArrayList<>();
		entries.add(
			AclEntry.newBuilder()
				.setType(AclEntryType.DENY)
				.setPrincipal(users)
				.setPermissions(EnumSet.of(AclEntryPermission.READ_DATA))
				.build()
		);
		entries.addAll(view.getAcl());
		entries.add(
			AclEntry.newBuilder()
				.setType(AclEntryType.ALLOW)
				.setPrincipal(users)
				.setPermissions(EnumSet.of(AclEntryPermission.WRITE_ACL, AclEntryPermission.READ_DATA))
				.build()
		);
		view.setAcl(entries);

		AgentCredentialStore.restrict(file);

		assertTrue(
			view.getAcl().stream().anyMatch(entry -> entry.type() == AclEntryType.DENY && entry.principal().equals(users))
		);
	}

	@Test
	void shouldRestrictFilesToTheOwnerOnPosix() throws Exception {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		final AgentCredentialStore store = new AgentCredentialStore(directory);
		store.beginAttempt(ATTEMPT_ID, RECOVERY);
		store.complete(AGENT_UID, SECRET.toCharArray());

		assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
		for (final String file : List.of(
			SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME,
			AgentInstanceUid.FILENAME,
			AgentCredentialStore.STATE_FILENAME,
			AgentCredentialStore.LOCK_FILENAME
		)) {
			assertEquals(
				"rw-------",
				PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.resolve(file))),
				file
			);
		}
	}

	@Test
	void shouldRemoveOtherPrincipalsFromTheAccessListOnWindows() throws Exception {
		final Path file = Files.createFile(directory.resolve("file"));
		final AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
		assumeTrue(view != null && Files.getFileAttributeView(file, PosixFileAttributeView.class) == null);
		final UserPrincipal users = FileSystems.getDefault()
			.getUserPrincipalLookupService()
			.lookupPrincipalByName("BUILTIN\\Users");
		final List<AclEntry> entries = new ArrayList<>(view.getAcl());
		entries.add(
			AclEntry.newBuilder()
				.setType(AclEntryType.ALLOW)
				.setPrincipal(users)
				.setPermissions(EnumSet.of(AclEntryPermission.READ_DATA))
				.build()
		);
		view.setAcl(entries);

		AgentCredentialStore.restrict(file);

		assertTrue(view.getAcl().stream().noneMatch(entry -> entry.principal().equals(users)));
		final UserPrincipal owner = view.getOwner();
		assertTrue(view.getAcl().stream().anyMatch(entry -> entry.principal().equals(owner)));
	}

	@Test
	void anotherProcessShouldNotTakeTheLockWhileItIsHeld() throws Exception {
		final Path probe = directory.resolve("LockProbe.java");
		Files.writeString(
			probe,
			"""
				import java.nio.channels.FileChannel;
				import java.nio.file.Path;
				import java.nio.file.StandardOpenOption;
				public class LockProbe {
					public static void main(String[] args) throws Exception {
						try (FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
							System.out.print(channel.tryLock() == null ? "busy" : "free");
						}
					}
				}
				"""
		);
		final Path security = Files.createDirectory(directory.resolve("security"));
		final Path lockFile = security.resolve(AgentCredentialStore.LOCK_FILENAME);
		final AgentCredentialStore store = new AgentCredentialStore(security);

		assertEquals("busy", store.locked(() -> runProbe(probe, lockFile)));
		assertEquals("free", runProbe(probe, lockFile));
	}

	private static String runProbe(final Path probe, final Path lockFile) throws java.io.IOException {
		final Process process = new ProcessBuilder(
			Path.of(System.getProperty("java.home"), "bin", "java").toString(),
			probe.toString(),
			lockFile.toString()
		).redirectErrorStream(true).start();
		try {
			assertTrue(process.waitFor(60, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new java.io.IOException(e);
		}
		return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
	}
}
