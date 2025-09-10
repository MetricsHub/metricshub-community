package org.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.security.PasswordEncrypt;
import org.mockito.MockedStatic;
import picocli.CommandLine;

class UserCliServiceTest {

	@TempDir
	Path tempDir;

	private File tempKeystore;

	@BeforeEach
	void setup() {
		tempKeystore = tempDir.resolve("metricshub-keystore.p12").toFile();
	}

	@Test
	void testCreateUserWithProvidedPasswordRw() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final UserCliService svc = new UserCliService();
			UserCliService.spec = new CommandLine(svc).getCommandSpec();

			final UserCliService.CreateCommand create = new UserCliService.CreateCommand();
			create.username = "alice";
			create.role = "rw";
			create.password = "supersafe".toCharArray(); // avoid interactive console
			final int exit = create.call();

			assertEquals(0, exit, "User creation should succeed");
		}
	}

	@Test
	void testCreateUserRejectsSeparatorInUsername() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final UserCliService svc = new UserCliService();
			UserCliService.spec = new CommandLine(svc).getCommandSpec();

			final UserCliService.CreateCommand create = new UserCliService.CreateCommand();
			// Intentionally include your reserved separator; adapt if the constant changes
			create.username = "bad" + AgentConstants.USER_INFO_SEPARATOR + "name"; // matches the default separator used in impl
			create.role = "ro";
			create.password = "x".toCharArray();

			// The command throws before returning an exit code (by design)
			int exit;
			try {
				exit = create.call();
			} catch (IllegalArgumentException ex) {
				// Expected path
				exit = CommandLine.ExitCode.SOFTWARE;
			}
			assertNotEquals(0, exit, "Creation must fail if the username contains the reserved separator");
		}
	}

	@Test
	void testCreateDuplicateUserFails() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final UserCliService svc = new UserCliService();
			UserCliService.spec = new CommandLine(svc).getCommandSpec();

			final UserCliService.CreateCommand c1 = new UserCliService.CreateCommand();
			c1.username = "dup";
			c1.role = "ro";
			c1.password = "pw1".toCharArray();
			assertEquals(0, c1.call(), "First creation should succeed");

			final UserCliService.CreateCommand c2 = new UserCliService.CreateCommand();
			c2.username = "dup";
			c2.role = "ro";
			c2.password = "pw2".toCharArray();
			final int exit2 = c2.call();

			assertEquals(CommandLine.ExitCode.SOFTWARE, exit2, "Second creation (duplicate) should return SOFTWARE");
		}
	}

	@Test
	void testListUsersEmpty() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final UserCliService svc = new UserCliService();
			UserCliService.spec = new CommandLine(svc).getCommandSpec();

			final UserCliService.ListCommand list = new UserCliService.ListCommand();
			final int exit = list.call();
			assertEquals(0, exit, "Listing when empty should still succeed");
		}
	}

	@Test
	void testListUsersAfterCreate() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final UserCliService svc = new UserCliService();
			UserCliService.spec = new CommandLine(svc).getCommandSpec();

			final UserCliService.CreateCommand create = new UserCliService.CreateCommand();
			create.username = "bob";
			create.role = "ro";
			create.password = "pw".toCharArray();
			assertEquals(0, create.call(), "Creation should succeed");

			final UserCliService.ListCommand list = new UserCliService.ListCommand();
			final int listExit = list.call();
			assertEquals(0, listExit, "Listing should succeed and include at least one user");
		}
	}

	@Test
	void testDeleteUser() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final UserCliService svc = new UserCliService();
			UserCliService.spec = new CommandLine(svc).getCommandSpec();

			final UserCliService.CreateCommand create = new UserCliService.CreateCommand();
			create.username = "charlie";
			create.role = "rw";
			create.password = "pw".toCharArray();
			assertEquals(0, create.call(), "Creation should succeed");

			final UserCliService.DeleteCommand del = new UserCliService.DeleteCommand();
			del.username = "charlie";
			final int delExit = del.call();
			assertEquals(0, delExit, "Delete should succeed");
		}
	}

	@Test
	void testDeleteUserNotFound() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final UserCliService svc = new UserCliService();
			UserCliService.spec = new CommandLine(svc).getCommandSpec();

			final UserCliService.DeleteCommand del = new UserCliService.DeleteCommand();
			del.username = "ghost";
			final int exit = del.call();

			// Your implementation returns SOFTWARE if user doesn't exist
			assertEquals(CommandLine.ExitCode.SOFTWARE, exit, "Deleting a non-existing user should return SOFTWARE");
		}
	}
}
