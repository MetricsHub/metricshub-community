package org.metricshub.agent.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.otel.OtelCollectorConfig;
import org.metricshub.agent.process.config.ProcessConfig;
import org.metricshub.agent.process.config.ProcessOutput;
import org.metricshub.agent.process.io.CustomInputStream;
import org.metricshub.agent.process.io.GobblerStreamProcessor;
import org.metricshub.agent.process.runtime.ProcessControl;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class OtelCollectorProcessServiceTest {

	@Test
	void test() throws IOException {
		final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
		final Process process = Mockito.mock(Process.class);

		try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
			processControl
				.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
				.thenReturn(pb);

			processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

			doReturn(process).when(pb).start();
			doReturn(new CustomInputStream("OpenTelemetry Collector started.")).when(process).getInputStream();
			doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();

			final GobblerStreamProcessor outputProcessor = new GobblerStreamProcessor();
			final GobblerStreamProcessor errorProcessor = new GobblerStreamProcessor();
			final ProcessConfig processConfig = ProcessConfig
				.builder()
				.commandLine(List.of("otelcol-contrib", "--config", "/opt/metricshub/otel/otel-config.yaml"))
				.output(ProcessOutput.builder().outputProcessor(outputProcessor).errorProcessor(errorProcessor).build())
				.workingDir(new File("."))
				.build();

			final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
			final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
			doReturn(processConfig).when(otelCollectorConfigMock).toProcessConfig();

			final OtelCollectorProcessService otelProcess = new OtelCollectorProcessService(agentConfig);

			otelProcess.start();

			Awaitility
				.await()
				.atMost(Durations.FIVE_SECONDS)
				.untilAsserted(() -> {
					assertEquals("OpenTelemetry Collector started.", outputProcessor.getBlocks());
					assertEquals("Error.", errorProcessor.getBlocks());
				});

			otelProcess.stop();

			assertFalse(otelProcess.isStarted(), "Process should not be started after stop");
		}
	}

	@Test
	void testWithoutOutputProcessor() throws IOException {
		final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
		final Process process = Mockito.mock(Process.class);

		try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
			processControl
				.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
				.thenReturn(pb);

			processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

			doReturn(process).when(pb).start();
			doReturn(new CustomInputStream("OpenTelemetry Collector started.")).when(process).getInputStream();
			doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();

			final ProcessConfig processConfig = ProcessConfig
				.builder()
				.commandLine(List.of("otelcol-contrib", "--config", "/opt/metricshub/otel/otel-config.yaml"))
				.output(null) // No output
				.workingDir(new File("."))
				.build();

			final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
			final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
			doReturn(processConfig).when(otelCollectorConfigMock).toProcessConfig();

			final OtelCollectorProcessService otelProcess = new OtelCollectorProcessService(agentConfig);

			assertDoesNotThrow(otelProcess::start);

			otelProcess.stop();

			assertFalse(otelProcess.isStarted(), "Process should not be started after stop");
		}
	}

	@Test
	void testWithoutErrorOutputProcessor() throws IOException {
		final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
		final Process process = Mockito.mock(Process.class);

		try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
			processControl
				.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
				.thenReturn(pb);

			processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

			doReturn(process).when(pb).start();
			doReturn(new CustomInputStream("OpenTelemetry Collector started.")).when(process).getInputStream();
			doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();

			final GobblerStreamProcessor outputProcessor = new GobblerStreamProcessor();
			final ProcessConfig processConfig = ProcessConfig
				.builder()
				.commandLine(List.of("otelcol-contrib", "--config", "/opt/metricshub/otel/otel-config.yaml"))
				.output(ProcessOutput.builder().outputProcessor(outputProcessor).build())
				.workingDir(new File("."))
				.build();

			final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
			final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
			doReturn(processConfig).when(otelCollectorConfigMock).toProcessConfig();

			final OtelCollectorProcessService otelProcess = new OtelCollectorProcessService(agentConfig);

			otelProcess.start();

			Awaitility
				.await()
				.atMost(Durations.FIVE_SECONDS)
				.untilAsserted(() -> {
					assertEquals("OpenTelemetry Collector started.", outputProcessor.getBlocks());
				});

			otelProcess.stop();

			assertFalse(otelProcess.isStarted(), "Process should not be started after stop");
		}
	}

	@Test
	void testLaunchOtelCollectorDisabled() {
		final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
		final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
		doReturn(true).when(otelCollectorConfigMock).isDisabled();
		final OtelCollectorProcessService otelCollectorProcessService = new OtelCollectorProcessService(agentConfig);

		assertDoesNotThrow(
			otelCollectorProcessService::launch,
			"The collector is disabled, so it should not throw an exception"
		);
	}

	@Test
	void testLaunchExecutableFileDoesntExist() {
		final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
		final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
		doReturn(ProcessConfig.builder().commandLine(List.of(UUID.randomUUID().toString())).build())
			.when(otelCollectorConfigMock)
			.toProcessConfig();
		final OtelCollectorProcessService otelCollectorProcessService = new OtelCollectorProcessService(agentConfig);

		assertDoesNotThrow(
			otelCollectorProcessService::launch,
			"The collector executable does not exist, so it should not throw an exception"
		);
	}

	@Test
	void testLaunchExecutableMissing() {
		final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
		final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
		doReturn(ProcessConfig.builder().commandLine(List.of()).build()).when(otelCollectorConfigMock).toProcessConfig();
		final OtelCollectorProcessService otelCollectorProcessService = new OtelCollectorProcessService(agentConfig);

		assertDoesNotThrow(
			otelCollectorProcessService::launch,
			"The collector executable is missing, so it should not throw an exception"
		);
	}
}
