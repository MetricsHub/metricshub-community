package org.metricshub.engine.security;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class providing methods for encryption and decryption operations using AES/GCM/NoPadding algorithm.
 * The master key is stored as a Base64-encoded string and derived using PBKDF2WithHmacSHA512.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CryptoCipher {

	/**
	 * Length of the GCM initialization vector (IV) in bytes.
	 */
	public static final int GCM_IV_LENGTH = 16;
	/**
	 * Length of the GCM authentication tag in bytes.
	 */
	public static final int GCM_TAG_LENGTH = 16;
	/**
	 * Number of iterations for the PBKDF2 key derivation.
	 */
	public static final int ITERATIONS = 2333;
	/**
	 * Length of the derived key in bytes.
	 */
	public static final int KEY_LENGTH = 32;

	/**
	 * Base64-encoded string representation of the master key.
	 */
	public static final String MASTER_KEY = "bWFzdGVyLWtleQ==";
	/**
	 * Initialization vector (IV) used in encryption and decryption.
	 */
	private static final byte[] IV = "c2VudHJ5aXY=".getBytes();
	/**
	 * Cipher algorithm used for encryption and decryption.
	 */
	private static final String CIPHER_ALGO = "AES/GCM/NoPadding";

	/**
	 * Encrypts the given plaintext using the provided secret key and initialization vector.
	 *
	 * @param plaintext Text to be encrypted in byte array.
	 * @param key       Secret key used for encryption.
	 * @param iv        Initialization vector.
	 * @return Encrypted data in byte array.
	 * @throws MetricsHubSecurityException If an error occurs during encryption.
	 */
	private static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] iv) throws MetricsHubSecurityException {
		try {
			final Cipher cipher = Cipher.getInstance(CIPHER_ALGO);

			final SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");

			final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

			cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

			return cipher.doFinal(plaintext);
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Cannot perform encryption", e);
		}
	}

	/**
	 * Decrypts the given ciphertext using the provided secret key and initialization vector.
	 *
	 * @param cipherText Ciphertext to be decrypted.
	 * @param key        Secret key used for decryption.
	 * @param iv         Initialization vector.
	 * @return Decrypted data in char array.
	 * @throws MetricsHubSecurityException If an error occurs during decryption.
	 */
	private static char[] decrypt(byte[] cipherText, SecretKey key, byte[] iv) throws MetricsHubSecurityException {
		try {
			final Cipher cipher = Cipher.getInstance(CIPHER_ALGO);

			final SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");

			final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

			cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);

			byte[] decryptedText = cipher.doFinal(cipherText);

			return bytesToChars(decryptedText);
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Cannot perform decryption", e);
		}
	}

	/**
	 * Generates a random master key.
	 *
	 * @return Char array representing the generated master key.
	 * @throws MetricsHubSecurityException If an error occurs during key generation.
	 */
	public static char[] generateRandomMasterKey() throws MetricsHubSecurityException {
		try {
			final byte[] salt = getSalt();

			final char[] masterKey = MASTER_KEY.toCharArray();
			final PBEKeySpec spec = new PBEKeySpec(masterKey, salt, ITERATIONS, KEY_LENGTH * 8);
			final SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
			return bytesToChars(Base64.getEncoder().encode(skf.generateSecret(spec).getEncoded()));
		} catch (Exception e) {
			throw new MetricsHubSecurityException("Error while building the master key", e);
		}
	}

	/**
	 * Gets a new random salt data.
	 *
	 * @return A random salt used to safeguard the password.
	 * @throws NoSuchAlgorithmException If algorithm used for salt generation does not exist.
	 */
	public static byte[] getSalt() throws NoSuchAlgorithmException {
		final SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
		final byte[] salt = new byte[64];
		sr.nextBytes(salt);
		return salt;
	}

	/**
	 * Encrypts the given plaintext using the provided secret key.
	 *
	 * @param plainText Plain text to be encrypted.
	 * @param secretKey Secret key used for encryption.
	 * @return Char array representing the encrypted data.
	 * @throws MetricsHubSecurityException If an error occurs during encryption.
	 */
	public static char[] encrypt(char[] plainText, SecretKey secretKey) throws MetricsHubSecurityException {
		final byte[] cipherText = encrypt(charsToBytes(plainText), secretKey, IV);
		return bytesToChars(Base64.getEncoder().encode(cipherText));
	}

	/**
	 * Decrypt the given text value using a secreteKey
	 *
	 * @param crypted   The text we wish to decrypt
	 * @param secretKey The {@link SecretKey} instance used to decrypt the text value
	 * @return char array of decrypted data
	 */
	public static char[] decrypt(char[] crypted, SecretKey secretKey) {
		try {
			final byte[] decodedCrypt = Base64.getDecoder().decode(charsToBytes(crypted));
			return decrypt(decodedCrypt, secretKey, IV);
		} catch (Exception e) {
			// Password cannot be decrypted, so it is probably a none encrypted password
			return crypted;
		}
	}

	/**
	 * Converts chars to bytes
	 *
	 * @param chars char array to convert
	 * @return byte array
	 */
	public static byte[] charsToBytes(char[] chars) {
		final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
		return Arrays.copyOf(byteBuffer.array(), byteBuffer.limit());
	}

	/**
	 * Converts bytes to chars
	 *
	 * @param bytes byte array
	 * @return char array
	 */
	public static char[] bytesToChars(byte[] bytes) {
		final CharBuffer charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
		return Arrays.copyOf(charBuffer.array(), charBuffer.limit());
	}
}
