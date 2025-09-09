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

import static org.metricshub.agent.helper.AgentConstants.USER_INFO_SEPARATOR;
import static org.metricshub.agent.helper.AgentConstants.USER_PREFIX;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import javax.crypto.spec.SecretKeySpec;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fusesource.jansi.Ansi;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.security.SecurityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Manage users via CLI.
 * Stored payload: username &lt;SEP&gt; bcrypt(password) &lt;SEP&gt; role
 */
//CHECKSTYLE:OFF
@Command(
	name = "user",
	subcommands = {
		UserCliService.CreateCommand.class, UserCliService.ListCommand.class, UserCliService.DeleteCommand.class
	},
	sortOptions = false,
	usageHelpAutoWidth = true,
	headerHeading = "%n",
	header = "Manage MetricsHub Users.",
	synopsisHeading = "%n@|bold,underline Usage|@:%n%n",
	descriptionHeading = "%n@|bold,underline Description|@:%n%n",
	description = "Create, list, and delete users stored in the secure keystore.%n" +
	"Passwords are bcrypt-hashed. Roles are 'ro' (read-only) or 'rw' (read-write).%n%n",
	parameterListHeading = "%n@|bold,underline Parameters|@:%n",
	optionListHeading = "%n@|bold,underline Options|@:%n"
)
//CHECKSTYLE:ON
@NoArgsConstructor
@Data
public class UserCliService {

	private static final Pattern USER_INFO_PATTERN = Pattern.compile(USER_INFO_SEPARATOR, Pattern.LITERAL);

	@Spec
	static CommandSpec spec;

	/**
	 * Returns the password used to protect the KeyStore.
	 *
	 * @return a character array containing the KeyStore password
	 */
	private static char[] getKeyStoreChars() {
		return new char[] { 's', 'e', 'c', 'r', 'e', 't' };
	}

	/**
	 * Generate the alias used to store/retrieve a user entry in the KeyStore.
	 *
	 * @param username the username
	 * @return the alias string
	 */
	private static String userAlias(final String username) {
		return USER_PREFIX + username;
	}

	/**
	 * Store the given KeyStore to the specified file.
	 *
	 * @param ks           the KeyStore instance
	 * @param keyStoreFile the file to store the KeyStore
	 * @throws Exception if an error occurs during storing
	 */
	private static void store(final KeyStore ks, final File keyStoreFile) throws Exception {
		try (OutputStream fos = new FileOutputStream(keyStoreFile)) {
			SecurityManager.store(ks, fos);
		}
	}

	/**
	 * Get a BCryptPasswordEncoder instance.
	 * @return the encoder instance
	 */
	private static BCryptPasswordEncoder encoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Create a new user with specified username, role, and password.
	 */
	@Command(name = "create", description = "Create a user with role 'ro' or 'rw'.")
	public static class CreateCommand implements Callable<Integer> {

		@Parameters(index = "0", paramLabel = "USERNAME", description = "The username for the new user.")
		String username;

		@Option(names = "--role", required = true, description = "User role: 'ro' (read-only) or 'rw' (read-write).")
		String role;

		@Option(
			names = "--password",
			arity = "0..1",
			interactive = true,
			description = "Password (will be prompted if not provided)."
		)
		char[] password;

		@Override
		public Integer call() throws Exception {
			final var out = spec.commandLine().getOut();

			if (username.contains(USER_INFO_SEPARATOR)) {
				throw new IllegalArgumentException("Username cannot contain the reserved separator.");
			}

			try {
				final var normalizedRole = normalizeAndValidateRole(role);
				final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
				final var ks = SecurityManager.loadKeyStore(keyStoreFile);

				final var alias = userAlias(username);
				final var existing = ks.getEntry(alias, new PasswordProtection(getKeyStoreChars()));
				if (existing != null) {
					out.println(
						Ansi.ansi().fgRed().a(String.format("A user with username '%s' already exists.", username)).reset()
					);
					return CommandLine.ExitCode.SOFTWARE;
				}

				final char[] pwd = (password == null || password.length == 0) ? promptPassword() : password;

				if (pwd == null || pwd.length == 0) {
					out.println(Ansi.ansi().fgRed().a("Password cannot be empty.").reset());
					return CommandLine.ExitCode.USAGE;
				}

				final String bcryptHash = encoder().encode(new String(pwd));

				// username<SEP>bcrypt(password)<SEP>role
				final String payload = username + USER_INFO_SEPARATOR + bcryptHash + USER_INFO_SEPARATOR + normalizedRole;
				final byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

				final var secretKey = new SecretKeySpec(bytes, "AES");
				final var secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
				final var protectionParam = new KeyStore.PasswordProtection(getKeyStoreChars());

				ks.setEntry(alias, secretKeyEntry, protectionParam);
				store(ks, keyStoreFile);

				out.println(
					Ansi
						.ansi()
						.a("User '")
						.fgGreen()
						.a(username)
						.reset()
						.a("' created with role '")
						.fgGreen()
						.a(normalizedRole)
						.reset()
						.a("'.")
						.reset()
				);
				return CommandLine.ExitCode.OK;
			} catch (Exception e) {
				out.println(
					Ansi.ansi().fgRed().a(String.format("Error creating user '%s': %s", username, e.getMessage())).reset()
				);
				return CommandLine.ExitCode.SOFTWARE;
			} finally {
				if (password != null) {
					Arrays.fill(password, '\0');
				}
			}
		}

		/**
		 * Normalize and validate the role input.
		 *
		 * @param r the input role
		 * @return the normalized role
		 */
		private static String normalizeAndValidateRole(String r) {
			final var v = r == null ? "" : r.trim().toLowerCase(Locale.ROOT);
			if (!v.equals("ro") && !v.equals("rw")) {
				throw new IllegalArgumentException("Role must be 'ro' or 'rw'");
			}
			return v;
		}

		/**
		 * Prompt the user to enter and confirm a password securely.
		 *
		 * @return the entered password
		 * @throws Exception if an error occurs during input
		 */
		private static char[] promptPassword() {
			final var console = System.console();
			if (console == null) {
				throw new IllegalStateException("No interactive console available. Use --password to provide a password.");
			}
			char[] pwd1 = console.readPassword("Enter password: ");
			char[] pwd2 = console.readPassword("Confirm password: ");
			try {
				if (pwd1 == null || pwd2 == null) {
					return null;
				}
				if (!Arrays.equals(pwd1, pwd2)) {
					throw new IllegalArgumentException("Passwords do not match.");
				}
				return pwd1;
			} finally {
				if (pwd1 != null) {
					Arrays.fill(pwd1, '\0');
				}
				if (pwd2 != null) {
					Arrays.fill(pwd2, '\0');
				}
			}
		}
	}

	/**
	 * List all stored users (username and role).
	 */
	@Command(name = "list", description = "List all stored users (username and role).")
	public static class ListCommand implements Callable<Integer> {

		@Override
		public Integer call() throws Exception {
			final var out = spec.commandLine().getOut();

			try {
				final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
				final var ks = SecurityManager.loadKeyStore(keyStoreFile);

				var hasEntries = false;
				final var aliases = ks.aliases();
				while (aliases.hasMoreElements()) {
					final var alias = aliases.nextElement();

					if (!alias.startsWith(USER_PREFIX)) {
						continue;
					}

					final var entry = ks.getEntry(alias, new PasswordProtection(getKeyStoreChars()));
					if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry) {
						final var secretKey = secretKeyEntry.getSecretKey();
						final var raw = new String(secretKey.getEncoded(), StandardCharsets.UTF_8);

						final var parts = USER_INFO_PATTERN.split(raw, -1);
						if (parts.length < 3) {
							out.println(Ansi.ansi().fgYellow().a(alias).reset().a(" (malformed entry)"));
							hasEntries = true;
							continue;
						}

						final String username = parts[0];
						final String role = parts[2];

						out.println(Ansi.ansi().fgGreen().a(username).reset().a(" (role: ").a(role).a(")"));
						hasEntries = true;
					}
				}

				if (!hasEntries) {
					out.println("No users found.");
				}
				return CommandLine.ExitCode.OK;
			} catch (Exception e) {
				out.println(Ansi.ansi().fgRed().a(String.format("Error listing users: %s%n", e.getMessage())).reset());
				return CommandLine.ExitCode.SOFTWARE;
			}
		}
	}

	/**
	 * Delete an existing user by username.
	 */
	@Command(name = "delete", description = "Delete an existing user by username.")
	public static class DeleteCommand implements Callable<Integer> {

		@Parameters(index = "0", paramLabel = "USERNAME", description = "The username to delete.")
		String username;

		@Override
		public Integer call() throws Exception {
			final var out = spec.commandLine().getOut();

			try {
				final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
				final var ks = SecurityManager.loadKeyStore(keyStoreFile);

				final var alias = userAlias(username);

				if (!ks.containsAlias(alias)) {
					out.println(Ansi.ansi().fgRed().a(String.format("No user found with username '%s'.", username)).reset());
					return CommandLine.ExitCode.SOFTWARE;
				}

				ks.deleteEntry(alias);
				store(ks, keyStoreFile);

				out.println(Ansi.ansi().a("User '").fgGreen().a(username).reset().a("' has been deleted."));
				return CommandLine.ExitCode.OK;
			} catch (Exception e) {
				out.println(
					Ansi.ansi().fgRed().a(String.format("Error deleting user '%s': %s%n", username, e.getMessage())).reset()
				);
				return CommandLine.ExitCode.SOFTWARE;
			}
		}
	}
}
