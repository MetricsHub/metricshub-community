package org.metricshub.engine.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityManagerTest {

	private static File securityPath;

	@BeforeAll
	static void beforeEeach() throws Exception {
		securityPath = new File("src/test/resources/security/metricshub-keystore.p12");
	}

	@Test
	void testEncryptDecrypt() throws Exception {
		// null password
		assertNull(SecurityManager.decrypt(null, securityPath));
		assertNull(SecurityManager.encrypt(null, securityPath));

		char[] password = {};
		char[] encryptedPassword = SecurityManager.encrypt(password, securityPath);
		assertArrayEquals(password, SecurityManager.decrypt(encryptedPassword, securityPath));

		password = "password".toCharArray();
		encryptedPassword = SecurityManager.encrypt(password, securityPath);
		assertArrayEquals(password, SecurityManager.decrypt(encryptedPassword, securityPath));

		password = "password2".toCharArray();
		encryptedPassword = SecurityManager.encrypt(password, securityPath);
		assertArrayEquals(password, SecurityManager.decrypt(encryptedPassword, securityPath));
	}

	@Test
	void updateSecretsShouldKeepTheOtherEntriesAndReplaceTheFile(@TempDir final Path directory) throws Exception {
		final File keyStoreFile = directory.resolve("metricshub-keystore.p12").toFile();
		final char[] encrypted = SecurityManager.encrypt("password".toCharArray(), keyStoreFile);
		final byte[] secret = "c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U".getBytes(StandardCharsets.US_ASCII);

		SecurityManager.updateSecrets(keyStoreFile, Map.of("first", secret, "second", new byte[] { 1, 2, 3 }));

		assertArrayEquals("password".toCharArray(), SecurityManager.decrypt(encrypted, keyStoreFile));
		assertArrayEquals(secret, SecurityManager.readSecret(keyStoreFile, "first"));

		final Map<String, byte[]> removal = new HashMap<>();
		removal.put("second", null);
		SecurityManager.updateSecrets(keyStoreFile, removal);

		assertNull(SecurityManager.readSecret(keyStoreFile, "second"));
		assertArrayEquals(secret, SecurityManager.readSecret(keyStoreFile, "first"));
		assertArrayEquals("password".toCharArray(), SecurityManager.decrypt(encrypted, keyStoreFile));
		try (var files = Files.list(directory)) {
			assertTrue(files.noneMatch(file -> file.toString().endsWith(".tmp")), "A temporary file was left");
		}
	}

	@Test
	void readSecretShouldBeNullWithoutKeyStore(@TempDir final Path directory) throws Exception {
		final File keyStoreFile = directory.resolve("metricshub-keystore.p12").toFile();
		assertNull(SecurityManager.readSecret(keyStoreFile, "first"));
		assertFalse(keyStoreFile.exists());
	}

	@Test
	void concurrentWritersShouldKeepEveryEntry(@TempDir final Path directory) throws Exception {
		final File keyStoreFile = directory.resolve("metricshub-keystore.p12").toFile();
		final ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			final List<Future<?>> writers = new ArrayList<>();
			for (int writer = 0; writer < 8; writer++) {
				final int id = writer;
				writers.add(executor.submit(() -> {
					for (int entry = 0; entry < 5; entry++) {
						SecurityManager.updateSecrets(keyStoreFile, Map.of("w" + id + "-" + entry, new byte[] { (byte) id }));
					}
					return null;
				}));
			}
			for (final Future<?> writer : writers) {
				writer.get();
			}
		} finally {
			executor.shutdownNow();
		}

		for (int writer = 0; writer < 8; writer++) {
			for (int entry = 0; entry < 5; entry++) {
				assertArrayEquals(
					new byte[] { (byte) writer },
					SecurityManager.readSecret(keyStoreFile, "w" + writer + "-" + entry)
				);
			}
		}
	}

	@Test
	void anUpdateShouldKeepThePosixPermissionsOfTheKeyStore(@TempDir final Path directory) throws Exception {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		final File keyStoreFile = directory.resolve("metricshub-keystore.p12").toFile();
		SecurityManager.updateSecrets(keyStoreFile, Map.of("first", new byte[] { 1 }));
		Files.setPosixFilePermissions(keyStoreFile.toPath(), PosixFilePermissions.fromString("rw-r-----"));

		SecurityManager.updateSecrets(keyStoreFile, Map.of("second", new byte[] { 2 }));

		assertEquals("rw-r-----", PosixFilePermissions.toString(Files.getPosixFilePermissions(keyStoreFile.toPath())));
	}

	@Test
	void anUpdateShouldKeepTheAccessListOfTheKeyStore(@TempDir final Path directory) throws Exception {
		final File keyStoreFile = directory.resolve("metricshub-keystore.p12").toFile();
		SecurityManager.updateSecrets(keyStoreFile, Map.of("first", new byte[] { 1 }));
		final AclFileAttributeView view = Files.getFileAttributeView(keyStoreFile.toPath(), AclFileAttributeView.class);
		assumeTrue(view != null && Files.getFileAttributeView(keyStoreFile.toPath(), PosixFileAttributeView.class) == null);
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

		SecurityManager.updateSecrets(keyStoreFile, Map.of("second", new byte[] { 2 }));

		assertTrue(
			Files.getFileAttributeView(keyStoreFile.toPath(), AclFileAttributeView.class)
				.getAcl()
				.stream()
				.anyMatch(entry -> entry.principal().equals(users))
		);
	}
}
