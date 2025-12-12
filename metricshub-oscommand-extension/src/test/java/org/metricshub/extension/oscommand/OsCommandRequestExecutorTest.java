package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.ssh.SshClient;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OsCommandRequestExecutorTest {

	@Mock
	private OsCommandRequestExecutor osCommandRequestExecutor;

	@Mock
	private SshClient sshClient;

	@Test
	void authenticateSshTest() throws IOException, ClientException {
		doReturn(true).when(sshClient).authenticate(anyString(), any(File.class), any(char[].class));

		assertDoesNotThrow(() ->
			OsCommandRequestExecutor.authenticateSsh(
				sshClient,
				"hostname",
				"username",
				"password".toCharArray(),
				new File("")
			)
		);

		doReturn(true).when(sshClient).authenticate(anyString(), any(char[].class));

		assertDoesNotThrow(() ->
			OsCommandRequestExecutor.authenticateSsh(sshClient, "hostname", "username", "password".toCharArray(), null)
		);

		doReturn(true).when(sshClient).authenticate(anyString());

		assertDoesNotThrow(() -> OsCommandRequestExecutor.authenticateSsh(sshClient, "hostname", "username", null, null));

		doThrow(new IOException()).when(sshClient).authenticate(anyString(), any(File.class), any(char[].class));
		assertThrows(
			ClientException.class,
			() ->
				OsCommandRequestExecutor.authenticateSsh(
					sshClient,
					"hostname",
					"username",
					"password".toCharArray(),
					new File("")
				)
		);

		doReturn(false).when(sshClient).authenticate(anyString(), any(File.class), any(char[].class));
		assertThrows(
			ClientException.class,
			() ->
				OsCommandRequestExecutor.authenticateSsh(
					sshClient,
					"hostname",
					"username",
					"password".toCharArray(),
					new File("")
				)
		);
	}

	@Test
	void updateCommandWithLocalListTest() throws IOException {
		final String remoteDirectory = "/var/tmp/";
		final String command = "ls -la /some/path";

		// Test with null localFiles - should return command unchanged
		String result = OsCommandRequestExecutor.updateCommandWithLocalList(command, null, remoteDirectory);
		assertEquals(command, result, "Command should remain unchanged when localFiles is null");

		// Test with empty localFiles - should return command unchanged
		result = OsCommandRequestExecutor.updateCommandWithLocalList(command, Collections.emptyList(), remoteDirectory);
		assertEquals(command, result, "Command should remain unchanged when localFiles is empty");

		// Test with single file replacement
		final File tempFile1 = File.createTempFile("test1", ".sh");
		final String absolutePath1 = tempFile1.getAbsolutePath();
		final String commandWithFile1 = "bash " + absolutePath1 + " arg1";
		final List<File> localFiles1 = List.of(tempFile1);

		result = OsCommandRequestExecutor.updateCommandWithLocalList(commandWithFile1, localFiles1, remoteDirectory);
		final String expected1 = "bash " + remoteDirectory + tempFile1.getName() + " arg1";
		assertEquals(expected1, result, "Single file path should be replaced with remote directory + filename");

		// Test with multiple files replacement
		final File tempFile2 = File.createTempFile("test2", ".py");
		final String absolutePath2 = tempFile2.getAbsolutePath();
		final String commandWithFiles = "python " + absolutePath1 + " && bash " + absolutePath2;
		final List<File> localFiles2 = List.of(tempFile1, tempFile2);

		result = OsCommandRequestExecutor.updateCommandWithLocalList(commandWithFiles, localFiles2, remoteDirectory);
		final String expected2 =
			"python " + remoteDirectory + tempFile1.getName() + " && bash " + remoteDirectory + tempFile2.getName();
		assertEquals(expected2, result, "Multiple file paths should be replaced with remote directory + filename");

		// Test with two embedded files to verify reduce accumulator works correctly
		// This tests that the accumulator function is applied sequentially for each file
		final File tempFile5 = File.createTempFile("script1", ".sh");
		final File tempFile6 = File.createTempFile("script2", ".sh");
		final String absolutePath5 = tempFile5.getAbsolutePath();
		final String absolutePath6 = tempFile6.getAbsolutePath();
		// Command with both files embedded
		final String commandWithTwoEmbeddedFiles = "bash " + absolutePath5 + " && " + absolutePath6 + " && echo done";
		final List<File> localFilesTwoEmbedded = List.of(tempFile5, tempFile6);

		result =
			OsCommandRequestExecutor.updateCommandWithLocalList(
				commandWithTwoEmbeddedFiles,
				localFilesTwoEmbedded,
				remoteDirectory
			);
		final String expectedTwoEmbedded =
			"bash " +
			remoteDirectory +
			tempFile5.getName() +
			" && " +
			remoteDirectory +
			tempFile6.getName() +
			" && echo done";
		assertEquals(
			expectedTwoEmbedded,
			result,
			"Reduce accumulator should process both embedded files sequentially, replacing each file path"
		);

		// Test case-insensitive replacement
		final File tempFile3 = File.createTempFile("test3", ".txt");
		final String absolutePath3 = tempFile3.getAbsolutePath();
		// Use different case for the path in command
		final String commandCaseInsensitive = "cat " + absolutePath3.toUpperCase();
		final List<File> localFiles3 = List.of(tempFile3);

		result = OsCommandRequestExecutor.updateCommandWithLocalList(commandCaseInsensitive, localFiles3, remoteDirectory);
		final String expected3 = "cat " + remoteDirectory + tempFile3.getName();
		assertEquals(expected3, result, "File path replacement should be case-insensitive");

		// Test with Windows paths and Windows remote directory
		final String windowsRemoteDirectory = "C:\\Windows\\Temp\\";
		final File tempFileWindows = File.createTempFile("testWin", ".bat");
		final String absolutePathWindows = tempFileWindows.getAbsolutePath();
		// Use the actual absolute path (will have correct path separators for the current OS)
		final String commandWindows = "cmd /c " + absolutePathWindows + " arg1";
		final List<File> localFilesWindows = List.of(tempFileWindows);

		result =
			OsCommandRequestExecutor.updateCommandWithLocalList(commandWindows, localFilesWindows, windowsRemoteDirectory);
		final String expectedWindows = "cmd /c " + windowsRemoteDirectory + tempFileWindows.getName() + " arg1";
		assertEquals(
			expectedWindows,
			result,
			"Windows file paths should be replaced with Windows remote directory, testing cross-platform path handling"
		);

		// Test with file path not in command - should remain unchanged
		final File tempFile4 = File.createTempFile("test4", ".log");
		final String commandWithoutFile = "echo 'hello world'";
		final List<File> localFiles4 = List.of(tempFile4);

		result = OsCommandRequestExecutor.updateCommandWithLocalList(commandWithoutFile, localFiles4, remoteDirectory);
		assertEquals(commandWithoutFile, result, "Command should remain unchanged when file path is not present");

		// Clean up temporary files
		tempFile1.delete();
		tempFile2.delete();
		tempFile3.delete();
		tempFile4.delete();
		tempFile5.delete();
		tempFile6.delete();
		tempFileWindows.delete();
	}
}
