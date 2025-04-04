package org.metricshub.agent.security;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.io.Console;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.security.SecurityManager;

/**
 * Utility class for encrypting passwords using a key-store file.
 * The class provides methods for retrieving the key-store file, determining the security folder location,
 * and encrypting passwords.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordEncrypt {
	static {
		Locale.setDefault(Locale.US);
	}

	/**
	 * Get the key-store file
	 *
	 * @param mkdir Whether we should create the security directory
	 *              (<em>libPath/../security</em> or <em>%PROGRAMDATA%\PRODUCT-CODE\security</em>)
	 * @return File instance
	 */
	public static File getKeyStoreFile(final boolean mkdir) {
		final Path securityDirectoryPath = LocalOsHandler.isWindows()
			? getSecurityFolderOnWindows()
			: getSecurityFolderFromInstallDir();

		return resolveKeyStoreFile(securityDirectoryPath, mkdir);
	}

	/**
	 * Get the security folder located under <em>%PROGRAMDATA%\PRODUCT-CODE</em>.<br>
	 * If the ProgramData path is not valid then the security file will be located
	 * under the install directory.
	 *
	 * @return {@link Path} instance
	 */
	static Path getSecurityFolderOnWindows() {
		return ConfigHelper
			.getProgramDataPath()
			.stream()
			.map(path -> Paths.get(path, AgentConstants.PRODUCT_WIN_DIR_NAME, AgentConstants.SECURITY_DIRECTORY_NAME))
			.findFirst()
			.orElseGet(PasswordEncrypt::getSecurityFolderFromInstallDir);
	}

	/**
	 * Get the security folder located under the install directory in Linux systems e.g.
	 * under /opt/PRODUCT-CODE/lib/app/../security
	 *
	 * @return {@link Path} instance
	 */
	static Path getSecurityFolderFromInstallDir() {
		File sourceDirectory;
		try {
			sourceDirectory = ResourceHelper.findSourceDirectory(PasswordEncrypt.class);
		} catch (Exception e) {
			throw new IllegalStateException("Error detected when getting local source file to get the keyStore.", e);
		}

		if (sourceDirectory == null) {
			throw new IllegalStateException("Could not get the local source file to get the keyStore.");
		}

		final Path path = sourceDirectory.getAbsoluteFile().toPath();

		Path parentLibPath = path.getParent();

		// No parent? let's work with the current directory
		if (parentLibPath == null) {
			parentLibPath = path;
		}

		return Paths.get(parentLibPath.toString(), "..", AgentConstants.SECURITY_DIRECTORY_NAME);
	}

	/**
	 * Create the security directory then return the key-store file.
	 *
	 * @param securityDirectory Security folder path which contains the key-store file.
	 * @param mkdir             Whether the security directory should be created or not
	 * @return {@link File} instance
	 */
	static File resolveKeyStoreFile(Path securityDirectory, final boolean mkdir) {
		if (mkdir && !Files.isDirectory(securityDirectory)) {
			securityDirectory = ConfigHelper.createDirectories(securityDirectory);
		}

		// path/hws-keystore.p12
		return Paths
			.get(securityDirectory.toAbsolutePath().toString(), SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME)
			.toFile();
	}

	/**
	 * The entry-point of MetricsHub-encrypt which is used for encrypting passwords.
	 * It reads the password from the command line or console input and prints the encrypted password.
	 *
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		try {
			char[] password;

			// Password can be passed in the first command line argument
			if (args.length > 0) {
				password = args[0].toCharArray();
			} else {
				// Reads a password from the console
				Console console = System.console();

				if (console == null) {
					System.out.println("No console. Cannot read passwords without console."); // NOSONAR
					return;
				}

				System.out.print("Enter the password to encrypt: "); // NOSONAR
				password = console.readPassword();
			}

			// Encrypt
			System.out.println(SecurityManager.encrypt(password, getKeyStoreFile(true))); // NOSONAR
		} catch (Exception e) {
			System.err.println(String.format("Error while encrypting password: %s", StringHelper.getStackMessages(e))); // NOSONAR
		}
	}
}
