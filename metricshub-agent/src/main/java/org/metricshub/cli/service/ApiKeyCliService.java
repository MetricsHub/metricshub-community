package org.metricshub.cli.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import static org.metricshub.agent.helper.AgentConstants.API_KEY_PREFIX;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.crypto.spec.SecretKeySpec;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fusesource.jansi.Ansi;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.security.SecurityManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Service for managing API keys via CLI commands.
 */
//CHECKSTYLE:OFF
@Command(
	name = "apikey",
	subcommands = {
		ApiKeyCliService.CreateCommand.class, ApiKeyCliService.ListCommand.class, ApiKeyCliService.RevokeCommand.class
	},
	sortOptions = false,
	usageHelpAutoWidth = true,
	headerHeading = "%n",
	header = "Manage MetricsHub API Keys.",
	synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
	descriptionHeading = "%n@|bold,underline Description|@:%n%n",
	description = "This tool allows you to securely create, list, and revoke API keys used to authenticate with MetricsHub services.%n%n",
	parameterListHeading = "%n@|bold,underline Parameters|@:%n",
	optionListHeading = "%n@|bold,underline Options|@:%n"
)
//CHECKSTYLE:ON
@NoArgsConstructor
@Data
public class ApiKeyCliService {
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@Spec
	static CommandSpec spec;

	@Command(name = "create", description = "Generate a new API key for a given alias.")
	public static class CreateCommand implements Callable<Integer> {

		@Option(names = { "--alias" }, required = true, description = "The alias associated with the API key.")
		String alias;

		@Option(
			names = { "--expires-on" },
			required = false,
			description = "The expiration date time for the API key in ISO-8601 format 'YYYY-MM-DDThh:mm:ss' (optional)." +
			"%nExample: 2025-07-23T08:42:45."
		)
		String expiresOn;

		@Override
		public Integer call() throws Exception {
			final var printWriter = spec.commandLine().getOut();
			try {
				final LocalDateTime expirationDateTime = resolveExpirationDateTime(expiresOn);
				final String expirationMessage = expirationDateTime != null
					? String.format(" (Expires on %s)", expirationDateTime)
					: " (No expiration)";
				final var apiKey = createApiKey(alias, expirationDateTime);
				printWriter.println(
					Ansi
						.ansi()
						.a("API key created for alias '")
						.a(alias)
						.a("': ")
						.fgGreen()
						.a(apiKey)
						.reset()
						.a(expirationMessage)
						.reset()
				);
				printWriter.println("Please store this key securely, as it will not be shown again.");
			} catch (Exception e) {
				printWriter.println(
					Ansi
						.ansi()
						.fgRed()
						.a(String.format("Error creating API key with alias '%s': %s", alias, e.getMessage()))
						.reset()
				);
				return CommandLine.ExitCode.SOFTWARE;
			}

			return CommandLine.ExitCode.OK;
		}

		/**
		 * Resolve the expiration date time based on the provided expiration date.
		 *
		 * @param expiresOn the expiration date in the format 'YYYY-MM-DDThh:mm:ss'.
		 * @return The expiration date time as a {@link LocalDateTime}.
		 */
		private static LocalDateTime resolveExpirationDateTime(final String expiresOn) {
			if (expiresOn != null) {
				final var expirationDateTime = LocalDateTime.parse(expiresOn);
				if (expirationDateTime != null && expirationDateTime.isBefore(LocalDateTime.now())) {
					throw new IllegalArgumentException("Expiration date time must be in the future");
				}
				return expirationDateTime;
			}

			return null;
		}

		/**
		 * Creates a new API key with the specified alias.
		 *
		 * @param apiKeyAlias        the alias of the API key to create
		 * @param expirationDateTime the expiration date and time for the API key, or null for no expiration
		 * @return the generated API key as a string
		 * @throws Exception if an error occurs while creating the API key
		 */
		private static String createApiKey(final String apiKeyAlias, final LocalDateTime expirationDateTime)
			throws Exception {
			final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
			// Load the keyStore.
			final var ks = SecurityManager.loadKeyStore(keyStoreFile);

			// Get the secretKey entry by its alias.
			KeyStore.SecretKeyEntry entry = null;
			entry =
				(KeyStore.SecretKeyEntry) ks.getEntry(API_KEY_PREFIX + apiKeyAlias, new PasswordProtection(getKeyStoreChars()));

			if (entry != null) {
				throw new IllegalStateException("An API key with alias '" + apiKeyAlias + "' already exists.");
			}

			return createApiKey(apiKeyAlias, expirationDateTime, ks, keyStoreFile);
		}

		/**
		 * Returns the password used to protect the KeyStore.
		 *
		 * @return a character array containing the KeyStore password
		 */
		private static char[] getKeyStoreChars() {
			return new char[] { 's', 'e', 'c', 'r', 'e', 't' };
		}

		/**
		 * Creates a new API key and stores it in the KeyStore.
		 *
		 * @param apiKeyAlias        the name of the API key to create
		 * @param expirationDateTime the expiration date and time for the API key, or null for no expiration
		 * @param ks                 the KeyStore where the API key will be stored
		 * @param keyStoreFile       the file where the KeyStore is stored
		 * @return the generated API key as a string
		 * @throws Exception if an error occurs while creating or storing the API key
		 */
		private static String createApiKey(
			final String apiKeyAlias,
			final LocalDateTime expirationDateTime,
			final KeyStore ks,
			final File keyStoreFile
		) throws Exception {
			final var apiKey = UUID.randomUUID().toString();
			var keyToStore = apiKey;
			if (expirationDateTime != null) {
				keyToStore += "__" + expirationDateTime.toString();
			}
			final byte[] apiKeyBytes = keyToStore.getBytes(StandardCharsets.UTF_8);

			try (OutputStream fos = new FileOutputStream(keyStoreFile)) {
				// Generate a SecretKeySpec with a valid algorithm.
				final var secretKey = new SecretKeySpec(apiKeyBytes, "AES");

				final var secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
				final var protectionParam = new KeyStore.PasswordProtection(getKeyStoreChars());

				ks.setEntry(API_KEY_PREFIX + apiKeyAlias, secretKeyEntry, protectionParam);

				// Finally commit.
				SecurityManager.store(ks, fos);

				return apiKey;
			}
		}
	}

	@Command(name = "list", description = "List all stored API keys.")
	public static class ListCommand implements Callable<Integer> {

		@Override
		public Integer call() throws Exception {
			final var printWriter = spec.commandLine().getOut();

			try {
				final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
				final var ks = SecurityManager.loadKeyStore(keyStoreFile);

				var hasEntries = false;
				final var aliases = ks.aliases();
				while (aliases.hasMoreElements()) {
					final var alias = aliases.nextElement();
					if (!alias.startsWith(API_KEY_PREFIX)) {
						continue;
					}

					final var entry = ks.getEntry(alias, new PasswordProtection(CreateCommand.getKeyStoreChars()));
					if (entry instanceof KeyStore.SecretKeyEntry secreKeyEntry) {
						final var secretKey = secreKeyEntry.getSecretKey();
						final var apiKeyId = new String(secretKey.getEncoded(), StandardCharsets.UTF_8);
						final var parts = apiKeyId.split("__");
						final var apiKeyName = alias.substring(API_KEY_PREFIX.length());
						final var maskedId = mask(parts[0]);
						final var expirationDate = parts.length > 1
							? String.format(" (Expires on %s)", parts[1])
							: " (No expiration)";
						printWriter.println(
							Ansi.ansi().fgGreen().a(apiKeyName).reset().a(" ").a(maskedId).reset().a(expirationDate).reset()
						);
						hasEntries = true;
					}
				}

				if (!hasEntries) {
					printWriter.println("No API keys found.");
				}

				return CommandLine.ExitCode.OK;
			} catch (Exception e) {
				printWriter.println(
					Ansi.ansi().fgRed().a(String.format("Error listing API keys: %s%n", e.getMessage())).reset()
				);
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

		/**
		 * Masks the API key ID for display purposes.
		 *
		 * @param keyId the API key ID to mask
		 * @return the masked API key ID
		 */
		private static String mask(String keyId) {
			if (keyId.length() <= 4) {
				return "*".repeat(keyId.length());
			}
			int maskLength = keyId.length() - 4;
			return "*".repeat(maskLength) + keyId.substring(maskLength);
		}
	}

	@Command(name = "revoke", description = "Revoke an existing API key by alias.")
	public static class RevokeCommand implements Callable<Integer> {

		@Option(names = { "--alias" }, required = true, description = "The alias of the API key to revoke.")
		String alias;

		@Override
		public Integer call() throws Exception {
			final var printWriter = spec.commandLine().getOut();

			try {
				final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
				final var ks = SecurityManager.loadKeyStore(keyStoreFile);

				final var apiKeyAlias = API_KEY_PREFIX + alias;

				if (!ks.containsAlias(apiKeyAlias)) {
					printWriter.println(Ansi.ansi().fgRed().a(String.format("No API key found with alias '%s'.", alias)).reset());
					return CommandLine.ExitCode.SOFTWARE;
				}

				ks.deleteEntry(apiKeyAlias);

				try (OutputStream fos = new FileOutputStream(keyStoreFile)) {
					SecurityManager.store(ks, fos);
				}

				printWriter.println(Ansi.ansi().a("API key '").fgGreen().a(alias).reset().a("' has been revoked.").reset());
				return CommandLine.ExitCode.OK;
			} catch (Exception e) {
				printWriter.println(
					Ansi.ansi().fgRed().a(String.format("Error revoking API key '%s': %s%n", alias, e.getMessage())).reset()
				);
				return CommandLine.ExitCode.SOFTWARE;
			}
		}
	}
}
