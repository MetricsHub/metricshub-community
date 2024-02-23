package org.sentrysoftware.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.cli.helper.StringBuilderWriter;
import org.sentrysoftware.metricshub.cli.service.MetricsHubCliService.CliPasswordReader;
import org.sentrysoftware.metricshub.cli.service.protocol.HttpConfigCli;
import org.sentrysoftware.metricshub.cli.service.protocol.IpmiConfigCli;
import org.sentrysoftware.metricshub.cli.service.protocol.SnmpConfigCli;
import org.sentrysoftware.metricshub.cli.service.protocol.SshConfigCli;
import org.sentrysoftware.metricshub.cli.service.protocol.WbemConfigCli;
import org.sentrysoftware.metricshub.cli.service.protocol.WinRmConfigCli;
import org.sentrysoftware.metricshub.cli.service.protocol.WmiConfigCli;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.sentrysoftware.metricshub.engine.connector.model.identity.Detection;

class MetricsHubCliServiceTest {

	private static final char[] PASSWORD = new char[] { 'p', 'a', 's', 's', 'w', 'o', 'r', 'd' };

	@Test
	void testListAllConnectors() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final String connectorId = "TestConnector";
		final String displayName = "Test Connector";
		final Map<String, Connector> store = Map.of(
			connectorId,
			Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).build())
						.compiledFilename(connectorId)
						.displayName(displayName)
						.build()
				)
				.build()
		);
		connectorStore.setStore(store);
		final StringBuilder builder = new StringBuilder();
		final Writer writer = new StringBuilderWriter(builder);
		final PrintWriter printWriter = new PrintWriter(writer);
		new MetricsHubCliService().listAllConnectors(connectorStore, printWriter);
		final String result = builder.toString();
		assertTrue(result.contains(displayName));
		assertTrue(result.contains(connectorId));
		assertTrue(result.contains(DeviceKind.LINUX.getDisplayName()));
	}

	@Test
	void testSetLogLevel() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		assertDoesNotThrow(() -> {
			metricsHubCliService.verbose = new boolean[] {};
			metricsHubCliService.setLogLevel();
		});
		assertDoesNotThrow(() -> {
			metricsHubCliService.verbose = new boolean[] { true };
			metricsHubCliService.setLogLevel();
		});
		assertDoesNotThrow(() -> {
			metricsHubCliService.verbose = new boolean[] { true, true };
			metricsHubCliService.setLogLevel();
		});
		assertDoesNotThrow(() -> {
			metricsHubCliService.verbose = new boolean[] { true, true, true };
			metricsHubCliService.setLogLevel();
		});
		assertDoesNotThrow(() -> {
			metricsHubCliService.verbose = new boolean[] { true, true, true, true };
			metricsHubCliService.setLogLevel();
		});
	}

	@Test
	void testTryInteractiveGlobalPassword() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		// Initialize a StringBuilder to capture the input password
		final StringBuilder builder = new StringBuilder();

		// Define a CliPasswordReader that appends the password to the StringBuilder
		final CliPasswordReader<char[]> cliPasswordReader = (format, args) -> {
			builder.append(PASSWORD, 0, PASSWORD.length);
			return PASSWORD;
		};

		// Test tryInteractiveGlobalPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveGlobalPassword(cliPasswordReader);

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveGlobalPassword hasn't triggered the password reader
		// because the username is not present
		assertTrue(builder.isEmpty());

		// Set a username in MetricsHubCliService
		metricsHubCliService.username = "user";

		// Test tryInteractiveGlobalPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveGlobalPassword(cliPasswordReader);

		// Assert that the captured password in the StringBuilder matches the expected value
		// This confirms that tryInteractiveGlobalPassword has triggered the password reader
		// because the username is present but the password is null
		assertEquals(new String(PASSWORD), builder.toString());

		// Clear the StringBuilder
		builder.delete(0, PASSWORD.length);

		// Set the password in MetricsHubCliService
		metricsHubCliService.password = PASSWORD;

		// Test tryInteractiveGlobalPassword method with the CliPasswordReader again
		metricsHubCliService.tryInteractiveGlobalPassword(cliPasswordReader);

		// Ensure that the StringBuilder is empty after the method call
		// This confirms that tryInteractiveGlobalPassword hasn't triggered the password reader
		// because both username and password are already present
		assertTrue(builder.isEmpty());
	}

	@Test
	void testTryInteractiveIpmiPassword() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		// Initialize a StringBuilder to capture the input password
		final StringBuilder builder = new StringBuilder();

		// Define a CliPasswordReader that appends the password to the StringBuilder
		final CliPasswordReader<char[]> cliPasswordReader = (format, args) -> {
			builder.append(PASSWORD, 0, PASSWORD.length);
			return PASSWORD;
		};

		// Test tryInteractiveIpmiPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveIpmiPassword(cliPasswordReader);

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveIpmiPassword hasn't triggered the password reader
		// because ipmiConfigCli is not present
		assertTrue(builder.isEmpty());

		// Set a new ipmiConfigCli in MetricsHubCliService
		metricsHubCliService.ipmiConfigCli = new IpmiConfigCli();

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveIpmiPassword hasn't triggered the password reader
		// because ipmiConfigCli is present but it doesn't define the username
		assertTrue(builder.isEmpty());

		// Set a username in ipmiConfigCli
		metricsHubCliService.ipmiConfigCli.setUsername("ipmiUser");

		// Test tryInteractiveIpmiPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveIpmiPassword(cliPasswordReader);

		// Assert that the captured password in the StringBuilder matches the expected value
		// This confirms that tryInteractiveIpmiPassword has triggered the password reader
		// because the username is present in ipmiConfigCli but the password is null
		assertEquals(new String(PASSWORD), builder.toString());

		// Set a password in ipmiConfigCli
		metricsHubCliService.ipmiConfigCli.setPassword(PASSWORD);

		// Clear the StringBuilder
		builder.delete(0, PASSWORD.length);

		// Test tryInteractiveIpmiPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveIpmiPassword(cliPasswordReader);

		// Ensure that the StringBuilder is empty after the method call
		// This confirms that tryInteractiveIpmiPassword hasn't triggered the password reader
		// because both username and password are already present in ipmiConfigCli
		assertTrue(builder.isEmpty());
	}

	@Test
	void testTryInteractiveSshPassword() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		// Initialize a StringBuilder to capture the input password
		final StringBuilder builder = new StringBuilder();

		// Define a CliPasswordReader that appends the password to the StringBuilder
		final CliPasswordReader<char[]> cliPasswordReader = (format, args) -> {
			builder.append(PASSWORD, 0, PASSWORD.length);
			return PASSWORD;
		};

		// Test tryInteractiveSshPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveSshPassword(cliPasswordReader);

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveSshPassword hasn't triggered the password reader
		// because sshConfigCli is not present
		assertTrue(builder.isEmpty());

		// Set a new sshConfigCli in MetricsHubCliService
		metricsHubCliService.sshConfigCli = new SshConfigCli();

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveSshPassword hasn't triggered the password reader
		// because sshConfigCli is present but it doesn't define the username
		assertTrue(builder.isEmpty());

		// Set a username in sshConfigCli
		metricsHubCliService.sshConfigCli.setUsername("sshUser");

		// Test tryInteractiveSshPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveSshPassword(cliPasswordReader);

		// Assert that the captured password in the StringBuilder matches the expected value
		// This confirms that tryInteractiveSshPassword has triggered the password reader
		// because the username is present in sshConfigCli but the password is null
		assertEquals(new String(PASSWORD), builder.toString());

		// Set a password in sshConfigCli
		metricsHubCliService.sshConfigCli.setPassword(PASSWORD);

		// Clear the StringBuilder
		builder.delete(0, PASSWORD.length);

		// Test tryInteractiveSshPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveSshPassword(cliPasswordReader);

		// Ensure that the StringBuilder is empty after the method call
		// This confirms that tryInteractiveSshPassword hasn't triggered the password reader
		// because the both username and password are already present in sshConfigCli
		assertTrue(builder.isEmpty());
	}

	@Test
	void testTryInteractiveHttpPassword() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		// Initialize a StringBuilder to capture the input password
		final StringBuilder builder = new StringBuilder();

		// Define a CliPasswordReader that appends the password to the StringBuilder
		final CliPasswordReader<char[]> cliPasswordReader = (format, args) -> {
			builder.append(PASSWORD, 0, PASSWORD.length);
			return PASSWORD;
		};

		// Test tryInteractiveHttpPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveHttpPassword(cliPasswordReader);

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveHttpPassword hasn't triggered the password reader
		// because httpConfigCli is not present
		assertTrue(builder.isEmpty());

		// Set a new httpConfigCli in MetricsHubCliService
		metricsHubCliService.httpConfigCli = new HttpConfigCli();

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveHttpPassword hasn't triggered the password reader
		// because httpConfigCli is present but it doesn't define the username
		assertTrue(builder.isEmpty());

		// Set a username in httpConfigCli
		metricsHubCliService.httpConfigCli.setUsername("httpUser");

		// Test tryInteractiveHttpPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveHttpPassword(cliPasswordReader);

		// Assert that the captured password in the StringBuilder matches the expected value
		// This confirms that tryInteractiveHttpPassword has triggered the password reader
		// because the username is present in httpConfigCli but the password is null
		assertEquals(new String(PASSWORD), builder.toString());

		// Set a password in httpConfigCli
		metricsHubCliService.httpConfigCli.setPassword(PASSWORD);

		// Clear the StringBuilder
		builder.delete(0, PASSWORD.length);

		// Test tryInteractiveHttpPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveHttpPassword(cliPasswordReader);

		// Ensure that the StringBuilder is empty after the method call
		// This confirms that tryInteractiveHttpPassword hasn't triggered the password reader
		// because both the username and password are already present in httpConfigCli
		assertTrue(builder.isEmpty());
	}

	@Test
	void testTryInteractiveWmiPassword() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		// Initialize a StringBuilder to capture the input password
		final StringBuilder builder = new StringBuilder();

		// Define a CliPasswordReader that appends the password to the StringBuilder
		final CliPasswordReader<char[]> cliPasswordReader = (format, args) -> {
			builder.append(PASSWORD, 0, PASSWORD.length);
			return PASSWORD;
		};

		// Test tryInteractiveWmiPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWmiPassword(cliPasswordReader);

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveWmiPassword hasn't triggered the password reader
		// because wmiConfigCli is not present
		assertTrue(builder.isEmpty());

		// Set a new wmiConfigCli in MetricsHubCliService
		metricsHubCliService.wmiConfigCli = new WmiConfigCli();

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveWmiPassword hasn't triggered the password reader
		// because wmiConfigCli is present but it doesn't define the username
		assertTrue(builder.isEmpty());

		// Set a username in wmiConfigCli
		metricsHubCliService.wmiConfigCli.setUsername("wmiUser");

		// Test tryInteractiveWmiPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWmiPassword(cliPasswordReader);

		// Assert that the captured password in the StringBuilder matches the expected value
		// This confirms that tryInteractiveWmiPassword has triggered the password reader
		// because the username is present in wmiConfigCli but the password is null
		assertEquals(new String(PASSWORD), builder.toString());

		// Set a password in wmiConfigCli
		metricsHubCliService.wmiConfigCli.setPassword(PASSWORD);

		// Clear the StringBuilder
		builder.delete(0, PASSWORD.length);

		// Test tryInteractiveWmiPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWmiPassword(cliPasswordReader);

		// Ensure that the StringBuilder is empty after the method call
		// This confirms that tryInteractiveWmiPassword hasn't triggered the password reader
		// because both the username and password are already present in wmiConfigCli
		assertTrue(builder.isEmpty());
	}

	@Test
	void testTryInteractiveWinRmPassword() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		// Initialize a StringBuilder to capture the input password
		final StringBuilder builder = new StringBuilder();

		// Define a CliPasswordReader that appends the password to the StringBuilder
		final CliPasswordReader<char[]> cliPasswordReader = (format, args) -> {
			builder.append(PASSWORD, 0, PASSWORD.length);
			return PASSWORD;
		};

		// Test tryInteractiveWinRmPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWinRmPassword(cliPasswordReader);

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveWinRmPassword hasn't triggered the password reader
		// because winRmConfigCli is not present
		assertTrue(builder.isEmpty());

		// Set a new winRmConfigCli in MetricsHubCliService
		metricsHubCliService.winRmConfigCli = new WinRmConfigCli();

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveWinRmPassword hasn't triggered the password reader
		// because winRmConfigCli is present but it doesn't define the username
		assertTrue(builder.isEmpty());

		// Set a username in winRmConfigCli
		metricsHubCliService.winRmConfigCli.setUsername("winRmUser");

		// Test tryInteractiveWinRmPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWinRmPassword(cliPasswordReader);

		// Assert that the captured password in the StringBuilder matches the expected value
		// This confirms that tryInteractiveWinRmPassword has triggered the password reader
		// because the username is present in winRmConfigCli but the password is null
		assertEquals(new String(PASSWORD), builder.toString());

		// Set a password in winRmConfigCli
		metricsHubCliService.winRmConfigCli.setPassword(PASSWORD);

		// Clear the StringBuilder
		builder.delete(0, PASSWORD.length);

		// Test tryInteractiveWinRmPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWinRmPassword(cliPasswordReader);

		// Ensure that the StringBuilder is empty after the method call
		// This confirms that tryInteractiveWinRmPassword hasn't triggered the password reader
		// because both the username and password are already present in winRmConfigCli
		assertTrue(builder.isEmpty());
	}

	@Test
	void testTryInteractiveWbemPassword() {
		final MetricsHubCliService metricsHubCliService = new MetricsHubCliService();

		// Initialize a StringBuilder to capture the input password
		final StringBuilder builder = new StringBuilder();

		// Define a CliPasswordReader that appends the password to the StringBuilder
		final CliPasswordReader<char[]> cliPasswordReader = (format, args) -> {
			builder.append(PASSWORD, 0, PASSWORD.length);
			return PASSWORD;
		};

		// Test tryInteractiveWbemPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWbemPassword(cliPasswordReader);

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveWbemPassword hasn't triggered the password reader
		// because wbemConfigCli is not present
		assertTrue(builder.isEmpty());

		// Set a new wbemConfigCli in MetricsHubCliService
		metricsHubCliService.wbemConfigCli = new WbemConfigCli();

		// Make sure the StringBuilder is blank
		// This confirms that tryInteractiveWbemPassword hasn't triggered the password reader
		// because wbemConfigCli is present but it doesn't define the username
		assertTrue(builder.isEmpty());

		// Set a username in wbemConfigCli
		metricsHubCliService.wbemConfigCli.setUsername("wbemUser");

		// Test tryInteractiveWbemPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWbemPassword(cliPasswordReader);

		// Assert that the captured password in the StringBuilder matches the expected value
		// This confirms that tryInteractiveWbemPassword has triggered the password reader
		// because the username is present in wbemConfigCli but the password is null
		assertEquals(new String(PASSWORD), builder.toString());

		// Set a password in wbemConfigCli
		metricsHubCliService.wbemConfigCli.setPassword(PASSWORD);

		// Clear the StringBuilder
		builder.delete(0, PASSWORD.length);

		// Test tryInteractiveWbemPassword method with the CliPasswordReader
		metricsHubCliService.tryInteractiveWbemPassword(cliPasswordReader);

		// Ensure that the StringBuilder is empty after the method call
		// This confirms that tryInteractiveWbemPassword hasn't triggered the password reader
		// because both username and password are already present in wbemConfigCli
		assertTrue(builder.isEmpty());
	}
}
