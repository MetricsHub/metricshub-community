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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.util.Map;
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

		// No entry, means the master key is not generated yet, let's generate a new one
		if (entry == null) {
			return generateMasterKey(ks, KEY_STORE_PASSWORD, keyStoreFile);
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
		try {
			final KeyStore ks = KeyStore.getInstance("PKCS12");

			if (keyStoreFile.exists()) {
				// if exists, load
				try (FileInputStream stream = new FileInputStream(keyStoreFile)) {
					ks.load(stream, KEY_STORE_PASSWORD);
				}
			} else {
				// if not exists, create
				ks.load(null, null);

				// Store the key store password
				try (FileOutputStream stream = new FileOutputStream(keyStoreFile)) {
					ks.store(stream, KEY_STORE_PASSWORD);
				}
			}

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
			// Random master key
			char[] masterKey = CryptoCipher.generateRandomMasterKey();

			// Create the KeySpec for password based encryption (PBE)
			final PBEKeySpec spec = new PBEKeySpec(masterKey, CryptoCipher.getSalt(), 2333, 32 * 8);

			// Get the secrete key factory
			final SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

			// Generate a secret key
			final SecretKey secretKey = skf.generateSecret(spec);

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
	 * Sets or removes secrets, then replaces the KeyStore file atomically: the new KeyStore is written to a
	 * temporary file in the same directory, forced to disk, then moved over the old one. Every other entry,
	 * the master key included, is kept, and a crash leaves either the old KeyStore or the new one.
	 *
	 * @param keyStoreFile The KeyStore file, created when it does not exist.
	 * @param secrets      The secrets by alias; a {@code null} value removes the alias.
	 * @throws MetricsHubSecurityException If the KeyStore cannot be read or written.
	 */
	public static void updateSecrets(@NonNull final File keyStoreFile, @NonNull final Map<String, byte[]> secrets)
		throws MetricsHubSecurityException {
		Path temporaryFile = null;
		try {
			final KeyStore ks;
			if (keyStoreFile.exists()) {
				ks = loadKeyStore(keyStoreFile);
			} else {
				ks = KeyStore.getInstance("PKCS12");
				ks.load(null, null);
			}
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
			final Path target = keyStoreFile.toPath().toAbsolutePath();
			temporaryFile = Files.createTempFile(target.getParent(), METRICSHUB_KEY_STORE_FILE_NAME, ".tmp");
			try (FileOutputStream stream = new FileOutputStream(temporaryFile.toFile())) {
				ks.store(stream, KEY_STORE_PASSWORD);
				stream.getFD().sync();
			}
			Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (MetricsHubSecurityException e) {
			throw e;
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Error detected when updating " + METRICSHUB_KEY_STORE_FILE_NAME, e);
		} finally {
			if (temporaryFile != null) {
				try {
					Files.deleteIfExists(temporaryFile);
				} catch (Exception e) {
					// Nothing more to do: a leftover temporary file holds no more than the KeyStore itself
				}
			}
		}
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
