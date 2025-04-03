package org.metricshub.extension.win.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinCommandService;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandResult;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class WinCommandLineSourceProcessorTest {

	private static final String COMMAND_LINE =
		"systeminfo | findstr /C:\"Total Physical Memory\" /C:\"Available Physical Memory\"";
	private static final String CONNECTOR_ID = "connector_id";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";

	@Mock
	WinCommandService winCommandServiceMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	WinCommandLineSourceProcessor winCommandLineSourceProcessor;

	@BeforeEach
	void setup() {
		winCommandLineSourceProcessor =
			new WinCommandLineSourceProcessor(winCommandServiceMock, configurationRetrieverMock, CONNECTOR_ID);
	}

	@Test
	void testProcessCommandLineMalformed() throws Exception {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();

		// Command line null
		assertEquals(SourceTable.empty(), winCommandLineSourceProcessor.process(null, telemetryManager));

		// Command line empty
		final String commandLine = "";
		final String keepOnlyRegExp = "";
		final String separators = ":";
		final String selectColumns = "2";

		final CommandLineSource commandLineSource = new CommandLineSource();
		commandLineSource.setCommandLine(commandLine);
		commandLineSource.setKeep(keepOnlyRegExp);
		commandLineSource.setSeparators(separators);
		commandLineSource.setSelectColumns(selectColumns);
		commandLineSource.setExecuteLocally(false);

		assertEquals(SourceTable.empty(), winCommandLineSourceProcessor.process(commandLineSource, telemetryManager));
	}

	@Test
	void testProcessCommandLineSourceOk() throws Exception {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final CommandLineSource commandLineSource = new CommandLineSource();
		commandLineSource.setCommandLine(COMMAND_LINE);
		commandLineSource.setKeep("Total\\|Available");
		commandLineSource.setSeparators(":");
		commandLineSource.setSelectColumns("2");
		commandLineSource.setExecuteLocally(false);

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();
		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);

		final String expectedCommandLineResult =
			"""
			Total Physical Memory:16384 MB
			Available Physical Memory:8192 MB
			""";
		doReturn(new OsCommandResult(expectedCommandLineResult, COMMAND_LINE))
			.when(winCommandServiceMock)
			.runOsCommand(COMMAND_LINE, HOST_NAME, wmiConfiguration, Map.of());

		final List<List<String>> expected = List.of(List.of("16384 MB"), List.of("8192 MB"));
		assertEquals(
			SourceTable.builder().table(expected).rawData("16384 MB\n8192 MB").build(),
			winCommandLineSourceProcessor.process(commandLineSource, telemetryManager)
		);
	}

	@Test
	void testProcessCommandLineSourceThrowsException() throws Exception {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final CommandLineSource commandLineSource = new CommandLineSource();
		commandLineSource.setCommandLine(COMMAND_LINE);
		commandLineSource.setKeep("Total\\|Available");
		commandLineSource.setSeparators(":");
		commandLineSource.setSelectColumns("2");
		commandLineSource.setExecuteLocally(false);

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();
		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);

		doThrow(new ClientException())
			.when(winCommandServiceMock)
			.runOsCommand(COMMAND_LINE, HOST_NAME, wmiConfiguration, Map.of());

		assertEquals(SourceTable.empty(), winCommandLineSourceProcessor.process(commandLineSource, telemetryManager));
	}
}
