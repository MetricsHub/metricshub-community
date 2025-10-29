package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.io.File;
import java.io.IOException;
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
}
