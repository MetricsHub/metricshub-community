package org.metricshub.engine.security;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Provides utility methods for encryption and decryption operations using a master key stored in a KeyStore.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityManager {

	private static final char[] KEY_STORE_PASSWORD = new char[] {
		'M',
		'a',
		't',
		'r',
		'i',
		'x',
		',',
		' ',
		'C',
		'r',
		'e',
		'd',
		'i',
		't',
		's',
		':',
		' ',
		'B',
		'e',
		'r',
		't',
		'r',
		'a',
		'n',
		'd',
		',',
		' ',
		'E',
		'l',
		'v',
		'i',
		's',
		',',
		' ',
		'F',
		'a',
		'd',
		'h',
		'e',
		'l',
		'a',
		',',
		' ',
		'H',
		'u',
		'a',
		'n',
		',',
		' ',
		'N',
		'a',
		's',
		's',
		'i',
		'm',
		',',
		' ',
		'R',
		'a',
		'm',
		'a',
		's',
		's',
		'h',
		' ',
		'a',
		'n',
		'd',
		' ',
		'T',
		'h',
		'o',
		'm',
		'a',
		's' };
	/**
	 * Alias for the master key stored in the KeyStore.
	 */
	private static final String MASTER_KEY_ALIAS = "masterKey";
	/**
	 * Name of the KeyStore file.
	 */
	public static final String METRICSHUB_KEY_STORE_FILE_NAME = "metricshub-keystore.p12";

	/**
	 * Serializes this process's KeyStore writers; a file lock next to the KeyStore serializes processes. A
	 * {@link FileLock} cannot be taken twice in one JVM, so only the outermost holder takes it.
	 */
	private static final ReentrantLock KEY_STORE_LOCK = new ReentrantLock();

	/**
	 * A change made to a loaded KeyStore by {@link #updateKeyStore(File, KeyStoreUpdate)}.
	 */
	@FunctionalInterface
	public interface KeyStoreUpdate {
		/**
		 * @param ks the KeyStore as it is on disk now
		 * @throws Exception when the change cannot be made: nothing is written
		 */
		void apply(KeyStore ks) throws Exception;
	}

	/**
	 * Encrypts the given password using the master key stored in the KeyStore.
	 *
	 * @param passwd       Password to encrypt.
	 * @param keyStoreFile The KeyStore file holding secret information.
	 * @return Encrypted password as a char array.
	 * @throws MetricsHubSecurityException If an error occurs during encryption.
	 */
	public static char[] encrypt(final char[] passwd, @NonNull final File keyStoreFile)
		throws MetricsHubSecurityException {
		if (passwd == null) {
			return passwd;
		}

		return CryptoCipher.encrypt(passwd, getSecretKey(keyStoreFile));
	}

	/**
	 * Decrypts the given encrypted password using the master key stored in the KeyStore.
	 *
	 * @param encrypted    The encrypted text.
	 * @param keyStoreFile The KeyStore file holding secret information.
	 * @return Decrypted password as a char array.
	 * @throws MetricsHubSecurityException If an error occurs during decryption.
	 */
	public static char[] decrypt(final char[] encrypted, final File keyStoreFile) throws MetricsHubSecurityException {
		if (encrypted != null && keyStoreFile != null && keyStoreFile.exists()) {
			return CryptoCipher.decrypt(encrypted, getSecretKey(keyStoreFile));
		}

		return encrypted;
	}

	/**
	 * Gets the secret key from the KeyStore, and generates a new one if not present.
	 *
	 * @param keyStoreFile The KeyStore file.
	 * @return {@link SecretKey} instance.
	 * @throws MetricsHubSecurityException If an error occurs during key retrieval or generation.
	 */
	private static SecretKey getSecretKey(@NonNull final File keyStoreFile) throws MetricsHubSecurityException {
		// Load the keyStore
		final KeyStore ks = loadKeyStore(keyStoreFile);

		// Get the secretKey entry by its alias
		KeyStore.SecretKeyEntry entry = null;
		try {
			entry = (KeyStore.SecretKeyEntry) ks.getEntry(MASTER_KEY_ALIAS, new PasswordProtection(KEY_STORE_PASSWORD));
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Error detected when getting the secret key entry", e);
		}

		// No entry, means the master key is not generated yet, let's generate a new one, unless another writer did
		// meanwhile
		if (entry == null) {
			final SecretKey[] masterKey = new SecretKey[1];
			updateKeyStore(keyStoreFile, fresh -> {
				final KeyStore.Entry existing = fresh.getEntry(MASTER_KEY_ALIAS, new PasswordProtection(KEY_STORE_PASSWORD));
				if (existing != null) {
					masterKey[0] = ((KeyStore.SecretKeyEntry) existing).getSecretKey();
				} else {
					masterKey[0] = newMasterKey();
					fresh.setEntry(
						MASTER_KEY_ALIAS,
						new KeyStore.SecretKeyEntry(masterKey[0]),
						new PasswordProtection(KEY_STORE_PASSWORD)
					);
				}
			});
			return masterKey[0];
		}

		return entry.getSecretKey();
	}

	/**
	 * Loads the KeyStore from the specified file, creating it if it does not exist.
	 *
	 * @param keyStoreFile The KeyStore file.
	 * @return {@link KeyStore} instance.
	 * @throws MetricsHubSecurityException If an error occurs during KeyStore loading or creation.
	 */
	public static KeyStore loadKeyStore(@NonNull final File keyStoreFile) throws MetricsHubSecurityException {
		if (!keyStoreFile.exists()) {
			// Created like any other write, so it never replaces a KeyStore another writer has just stored
			updateKeyStore(keyStoreFile, ks -> {});
		}
		try {
			final KeyStore ks = KeyStore.getInstance("PKCS12");
			// Read in one go through NIO, which lets a writer replace the file meanwhile, on Windows too
			ks.load(new ByteArrayInputStream(Files.readAllBytes(keyStoreFile.toPath())), KEY_STORE_PASSWORD);
			return ks;
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Error detected when loading " + METRICSHUB_KEY_STORE_FILE_NAME, e);
		}
	}

	/**
	 * Generates and saves a new master key in the given {@link KeyStore}.
	 *
	 * @param ks           The KeyStore holding the secret key.
	 * @param password     The password used to protect the KeyStore.
	 * @param keyStoreFile The KeyStore file.
	 * @return The newly generated {@link SecretKey}.
	 * @throws MetricsHubSecurityException If an error occurs during key generation or storage.
	 */
	public static SecretKey generateMasterKey(
		@NonNull final KeyStore ks,
		@NonNull final char[] password,
		@NonNull final File keyStoreFile
	) throws MetricsHubSecurityException {
		try {
			final SecretKey secretKey = newMasterKey();

			// Create a secret key entry to be stored in the KeyStore
			KeyStore.SecretKeyEntry skEntry = new KeyStore.SecretKeyEntry(secretKey);

			// Set the secret key entry
			ks.setEntry(MASTER_KEY_ALIAS, skEntry, new PasswordProtection(password));

			// Finally commit
			try (FileOutputStream stream = new FileOutputStream(keyStoreFile)) {
				ks.store(stream, password);
			}

			return secretKey;
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Error detected when generating the master key", e);
		}
	}

	/**
	 * Reads a secret stored with {@link #updateSecrets(File, Map)}.
	 *
	 * @param keyStoreFile The KeyStore file.
	 * @param alias        The alias of the secret.
	 * @return the secret's bytes, or {@code null} when the KeyStore file or the alias does not exist.
	 * @throws MetricsHubSecurityException If the KeyStore cannot be read.
	 */
	public static byte[] readSecret(@NonNull final File keyStoreFile, @NonNull final String alias)
		throws MetricsHubSecurityException {
		if (!keyStoreFile.exists()) {
			return null;
		}
		final KeyStore ks = loadKeyStore(keyStoreFile);
		try {
			final Key key = ks.getKey(alias, KEY_STORE_PASSWORD);
			return key == null ? null : key.getEncoded();
		} catch (Exception e) {
			throw new MetricsHubSecurityException(
				"Error detected when reading an entry of " + METRICSHUB_KEY_STORE_FILE_NAME,
				e
			);
		}
	}

	/**
	 * Sets or removes secrets through {@link #updateKeyStore(File, KeyStoreUpdate)}: every other entry, the master
	 * key included, is kept, and a crash leaves either the old KeyStore or the new one.
	 *
	 * @param keyStoreFile The KeyStore file, created when it does not exist.
	 * @param secrets      The secrets by alias; a {@code null} value removes the alias.
	 * @throws MetricsHubSecurityException If the KeyStore cannot be read or written.
	 */
	public static void updateSecrets(@NonNull final File keyStoreFile, @NonNull final Map<String, byte[]> secrets)
		throws MetricsHubSecurityException {
		updateKeyStore(keyStoreFile, ks -> {
			for (final Map.Entry<String, byte[]> secret : secrets.entrySet()) {
				if (secret.getValue() == null) {
					if (ks.containsAlias(secret.getKey())) {
						ks.deleteEntry(secret.getKey());
					}
				} else {
					ks.setEntry(
						secret.getKey(),
						new KeyStore.SecretKeyEntry(new SecretKeySpec(secret.getValue(), "AES")),
						new PasswordProtection(KEY_STORE_PASSWORD)
					);
				}
			}
		});
	}

	/**
	 * Loads the KeyStore, applies a change, then replaces the file atomically: the new KeyStore is written to a
	 * temporary file in the same directory, given the owner and permissions of the file it replaces, forced to
	 * disk, then moved over it. Every writer of the KeyStore goes through here, holding a lock that serializes the
	 * threads of this process and, through {@code metricshub-keystore.p12.lock}, other processes, so no write
	 * loses the entries of another and a crash leaves either the old KeyStore or the new one.
	 *
	 * @param keyStoreFile The KeyStore file, created when it does not exist.
	 * @param update       The change.
	 * @throws MetricsHubSecurityException If the KeyStore cannot be read or written, or the change fails.
	 */
	public static void updateKeyStore(@NonNull final File keyStoreFile, @NonNull final KeyStoreUpdate update)
		throws MetricsHubSecurityException {
		final Path target = keyStoreFile.toPath().toAbsolutePath();
		KEY_STORE_LOCK.lock();
		try {
			if (KEY_STORE_LOCK.getHoldCount() > 1) {
				replace(target, update);
				return;
			}
			Files.createDirectories(target.getParent());
			try (
				FileChannel channel = FileChannel.open(
					target.resolveSibling(target.getFileName() + ".lock"),
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE
				);
				FileLock lock = channel.lock()
			) {
				replace(target, update);
			}
		} catch (MetricsHubSecurityException e) {
			throw e;
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Error detected when updating " + METRICSHUB_KEY_STORE_FILE_NAME, e);
		} finally {
			KEY_STORE_LOCK.unlock();
		}
	}

	/**
	 * Applies a change and replaces the KeyStore file, under the lock of {@link #updateKeyStore}.
	 */
	private static void replace(final Path target, final KeyStoreUpdate update) throws Exception {
		final KeyStore ks;
		if (Files.exists(target)) {
			ks = loadKeyStore(target.toFile());
		} else {
			ks = KeyStore.getInstance("PKCS12");
			ks.load(null, null);
		}
		update.apply(ks);
		final Path temporaryFile = Files.createTempFile(target.getParent(), METRICSHUB_KEY_STORE_FILE_NAME, ".tmp");
		try {
			// The access of the KeyStore first, so the temporary file never holds a secret under a wider access
			if (Files.exists(target)) {
				copyAccess(target, temporaryFile);
			}
			try (FileOutputStream stream = new FileOutputStream(temporaryFile.toFile())) {
				ks.store(stream, KEY_STORE_PASSWORD);
				stream.getFD().sync();
			}
			moveDurably(temporaryFile, target);
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	/**
	 * Moves a file over another atomically, or, where the file system cannot, by a plain replacement as an in-place
	 * write did; then forces the directory, so a power loss cannot undo the move.
	 *
	 * @param source the file to move
	 * @param target the file it replaces
	 * @throws IOException when the move fails
	 */
	public static void moveDurably(final Path source, final Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
		try (FileChannel directory = FileChannel.open(target.toAbsolutePath().getParent(), StandardOpenOption.READ)) {
			directory.force(true);
		} catch (IOException e) {
			// A directory cannot be opened on Windows, where the move is already durable once it returns
		}
	}

	/**
	 * Gives a file the owner and permissions of another, so replacing the KeyStore never changes who may read it
	 * (a command run by an administrator must not take it away from the account the agent runs as). Changing the
	 * owner needs a privileged account: otherwise the new file stays its creator's, as with an in-place write.
	 */
	private static void copyAccess(final Path from, final Path to) throws IOException {
		final PosixFileAttributeView posix = Files.getFileAttributeView(to, PosixFileAttributeView.class);
		if (posix != null) {
			final PosixFileAttributes attributes = Files.readAttributes(from, PosixFileAttributes.class);
			posix.setPermissions(attributes.permissions());
			try {
				posix.setGroup(attributes.group());
				posix.setOwner(attributes.owner());
			} catch (IOException e) {
				// Not privileged: the file stays its creator's, as with an in-place write
			}
			return;
		}
		final AclFileAttributeView acl = Files.getFileAttributeView(to, AclFileAttributeView.class);
		if (acl != null) {
			final AclFileAttributeView source = Files.getFileAttributeView(from, AclFileAttributeView.class);
			acl.setAcl(source.getAcl());
			try {
				acl.setOwner(source.getOwner());
			} catch (IOException e) {
				// Not privileged: the file stays its creator's, as with an in-place write
			}
		}
	}

	/**
	 * @return a new random master key
	 * @throws Exception when the key cannot be derived
	 */
	private static SecretKey newMasterKey() throws Exception {
		final PBEKeySpec spec = new PBEKeySpec(
			CryptoCipher.generateRandomMasterKey(),
			CryptoCipher.getSalt(),
			2333,
			32 * 8
		);
		return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec);
	}

	/**
	 * Stores this keystore to the given output stream.
	 *
	 * @param ks  The KeyStore to store.
	 * @param fos The output stream to which this keystore is written.
	 */
	public static void store(final KeyStore ks, final OutputStream fos) throws Exception {
		ks.store(fos, KEY_STORE_PASSWORD);
	}
}
