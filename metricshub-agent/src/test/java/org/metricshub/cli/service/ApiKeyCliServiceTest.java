package org.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.cli.service.ApiKeyCliService.CreateCommand;
import org.metricshub.cli.service.ApiKeyCliService.ListCommand;
import org.metricshub.cli.service.ApiKeyCliService.RevokeCommand;
import org.mockito.MockedStatic;
import picocli.CommandLine;

class ApiKeyCliServiceTest {

	@TempDir
	Path tempDir;

	private File tempKeystore;

	@BeforeEach
	void setup() {
		tempKeystore = tempDir.resolve("metricshub-keystore.p12").toFile();
	}

	@Test
	void testCreateApiKey() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final ApiKeyCliService apiKeyCliService = new ApiKeyCliService();

			// Setup CLI spec manually
			ApiKeyCliService.spec = new CommandLine(apiKeyCliService).getCommandSpec();

			final CreateCommand createCommand = new ApiKeyCliService.CreateCommand();
			createCommand.alias = "test-alias";
			final int exit = createCommand.call();

			assertEquals(0, exit, "Exit code should be 0 for successful API key creation");
		}
	}

	@Test
	void testListApiKeys() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			// First create a key so that list has content
			final ApiKeyCliService apiKeyCliService = new ApiKeyCliService();
			ApiKeyCliService.spec = new CommandLine(apiKeyCliService).getCommandSpec();

			final CreateCommand createCommand = new CreateCommand();
			createCommand.alias = "to-list";
			final int createExit = createCommand.call();
			assertEquals(0, createExit, "API key creation should succeed");

			final ListCommand listCommand = new ListCommand();
			final int listExit = listCommand.call();
			assertEquals(0, listExit, "Exit code should be 0 for listing API keys");
		}
	}

	@Test
	void testRevokeApiKey() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final ApiKeyCliService apiKeyCliService = new ApiKeyCliService();
			ApiKeyCliService.spec = new CommandLine(apiKeyCliService).getCommandSpec();

			final CreateCommand createCommand = new CreateCommand();
			createCommand.alias = "to-revoke";
			final int createExit = createCommand.call();
			assertEquals(0, createExit, "API key creation should succeed");

			final RevokeCommand revokeCommand = new RevokeCommand();
			revokeCommand.alias = "to-revoke";
			final int revokeExit = revokeCommand.call();
			assertEquals(0, revokeExit, "Exit code should be 0 for successful API key revocation");
		}
	}
}
