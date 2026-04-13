package org.metricshub.agent.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.security.SecurityManager;
import org.mockito.MockedStatic;

class PasswordEncryptTest {

	@TempDir
	static Path tempDir;

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testGetKeyStoreFileWindows() throws IOException {
		{
			try (final MockedStatic<PasswordEncrypt> mockedPasswordEncrypt = mockStatic(PasswordEncrypt.class)) {
				mockedPasswordEncrypt
					.when(() -> PasswordEncrypt.getSecurityFolderOnWindows())
					.thenAnswer(invocation -> Files.createDirectories(tempDir.resolve("metricshub/security")));

				mockedPasswordEncrypt
					.when(() -> PasswordEncrypt.resolveKeyStoreFile(any(Path.class), any(Boolean.class)))
					.thenCallRealMethod();

				mockedPasswordEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenCallRealMethod();

				final File actual = PasswordEncrypt.getKeyStoreFile(true);
				assertNotNull(actual);
				assertEquals(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME, actual.getName());
			}
		}

		{
			final File keyStoreFile = PasswordEncrypt.getKeyStoreFile(false);
			assertNotNull(keyStoreFile);
			assertEquals(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME, keyStoreFile.getName());
		}
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testGetKeyStoreFileLinux() {
		{
			try (final MockedStatic<ResourceHelper> mockedResourceHelper = mockStatic(ResourceHelper.class)) {
				mockedResourceHelper
					.when(() -> ResourceHelper.findSourceDirectory(PasswordEncrypt.class))
					.thenAnswer(invocation -> Files.createDirectories(tempDir.resolve("metricshub/lib/app/jar")).toFile());

				final File actual = PasswordEncrypt.getKeyStoreFile(true);
				assertNotNull(actual);
				assertEquals(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME, actual.getName());
			}
		}

		{
			try (final MockedStatic<ResourceHelper> mockedResourceHelper = mockStatic(ResourceHelper.class)) {
				mockedResourceHelper
					.when(() -> ResourceHelper.findSourceDirectory(PasswordEncrypt.class))
					.thenAnswer(invocation -> tempDir.resolve("metricshub/lib/app/jar").toFile());

				final File keyStoreFile = PasswordEncrypt.getKeyStoreFile(false);
				assertNotNull(keyStoreFile);
				assertEquals(SecurityManager.METRICSHUB_KEY_STORE_FILE_NAME, keyStoreFile.getName());
			}
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testGetSecurityFolderOnWindows() throws IOException {
		// ProgramData invalid
		{
			try (
				final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class);
				final MockedStatic<ResourceHelper> mockedResourceHelper = mockStatic(ResourceHelper.class)
			) {
				mockedResourceHelper
					.when(() -> ResourceHelper.findSourceDirectory(PasswordEncrypt.class))
					.thenAnswer(invocation -> tempDir.resolve("metricshub/app/jar").toFile());

				mockedConfigHelper.when(() -> ConfigHelper.getProgramDataPath()).thenReturn(Optional.empty());

				final Path securityFolderOnWindows = PasswordEncrypt.getSecurityFolderOnWindows();

				final String expectedPath = "metricshub\\app\\..\\security";

				assertNotNull(securityFolderOnWindows);
				assertTrue(
					() -> securityFolderOnWindows.endsWith(expectedPath),
					String.format("Found path %s. Expected path ends with %s.", securityFolderOnWindows.toString(), expectedPath)
				);
			}
		}

		// ProgramData valid
		{
			try (final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class)) {
				mockedConfigHelper.when(() -> ConfigHelper.getProgramDataPath()).thenReturn(Optional.of(tempDir.toString()));

				final Path securityFolderOnWindows = PasswordEncrypt.getSecurityFolderOnWindows();

				final String expectedPath = "metricshub\\security";

				assertNotNull(securityFolderOnWindows);
				assertTrue(
					() -> securityFolderOnWindows.endsWith(expectedPath),
					String.format("Found path %s. Expected path ends with %s.", securityFolderOnWindows.toString(), expectedPath)
				);
			}
		}
	}
}
