package org.metricshub.agent.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.CentralAuthConfig;
import org.metricshub.agent.config.CentralConfig;
import org.metricshub.agent.config.otel.OtelCollectorConfig;
import org.metricshub.agent.fleet.EnrollmentCoordinator;
import org.metricshub.agent.process.config.ProcessConfig;
import org.metricshub.agent.process.config.ProcessOutput;
import org.metricshub.agent.process.io.CustomInputStream;
import org.metricshub.agent.process.io.GobblerStreamProcessor;
import org.metricshub.agent.process.runtime.ProcessControl;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class OtelCollectorProcessServiceTest {

	@TempDir
	Path directory;

	/**
	 * @param central    the {@code central} configuration
	 * @param enrollment the enrollment coordinator
	 * @return a service whose installed collector is never really started
	 */
	private OtelCollectorProcessService service(final CentralConfig central, final EnrollmentCoordinator enrollment)
		throws IOException {
		final Path executable = Files.createFile(directory.resolve("otelcol-contrib"));
		final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
		doReturn(ProcessConfig.builder().commandLine(List.of(executable.toString())).build()).when(otelCollectorConfigMock)
			.toProcessConfig();
		// Long enough for the launch task to run before the executor is shut down
		doReturn(5L).when(otelCollectorConfigMock).getStartupDelay();
		final AgentConfig agentConfig = AgentConfig.builder()
			.otelCollector(otelCollectorConfigMock)
			.central(central)
			.build();
		final OtelCollectorProcessService service = Mockito.spy(new OtelCollectorProcessService(agentConfig));
		doNothing().when(service).start();
		service.setEnrollment(enrollment);
		return service;
	}

	private static CentralConfig withAuth() {
		return CentralConfig.builder()
			.enabled(true)
			.auth(CentralAuthConfig.builder().issuerUrl("https://central.example").build())
			.build();
	}

	/**
	 * @return a coordinator whose token file is not written yet: the launch it is given is captured
	 */
	private static EnrollmentCoordinator pending(final AtomicReference<Consumer<Path>> launch) {
		final EnrollmentCoordinator enrollment = Mockito.mock(EnrollmentCoordinator.class);
		doAnswer(invocation -> {
			launch.set(invocation.getArgument(1));
			return true;
		}).when(enrollment).whenTokenFileWritten(any(), any());
		return enrollment;
	}

	@Test
	void beforeReadyTheLaunchShouldWaitForTheTokenFile() throws Exception {
		final AtomicReference<Consumer<Path>> launch = new AtomicReference<>();
		final OtelCollectorProcessService service = service(withAuth(), pending(launch));

		service.launch();

		verify(service, never()).start();
		launch.get().accept(directory.resolve("metricshub-agent-token"));
		verify(service, timeout(5000)).start();
	}

	@Test
	void aStopShouldWinOverADeferredLaunchThatStartsAfterIt() throws Exception {
		final AtomicReference<Consumer<Path>> launch = new AtomicReference<>();
		final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
		doReturn(
			ProcessConfig.builder()
				.commandLine(List.of(Files.createFile(directory.resolve("otelcol-contrib")).toString()))
				.build()
		).when(otelCollectorConfigMock).toProcessConfig();
		final OtelCollectorProcessService service = new OtelCollectorProcessService(
			AgentConfig.builder().otelCollector(otelCollectorConfigMock).central(withAuth()).build()
		);
		service.setEnrollment(pending(launch));
		service.launch();

		try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
			// A reload stops the service, then the deferred launch, which passed its checks before, reaches start()
			service.stop();
			service.start();

			processControl.verify(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(), anyBoolean()), never());
			assertFalse(service.isStarted());
		}
	}

	@Test
	void whenReadyTheCollectorShouldGetTheTokenFilePathAndNothingElse() throws Exception {
		final Path tokenFile = directory.resolve("metricshub-agent-token");
		final EnrollmentCoordinator enrollment = Mockito.mock(EnrollmentCoordinator.class);
		doAnswer(invocation -> {
			invocation.<Consumer<Path>>getArgument(1).accept(tokenFile);
			return false;
		}).when(enrollment).whenTokenFileWritten(any(), any());
		final OtelCollectorProcessService service = service(withAuth(), enrollment);

		service.launch();
		verify(service, timeout(5000)).start();
		final ProcessBuilder processBuilder = new ProcessBuilder("otelcol-contrib");
		service.onBeforeProcessStart(processBuilder);

		final Map<String, String> expected = new HashMap<>(new ProcessBuilder().environment());
		expected.put(OtelCollectorProcessService.TOKEN_FILE_VARIABLE, tokenFile.toString());
		assertEquals(expected, processBuilder.environment());
	}

	@Test
	void withoutCentralAuthTheLaunchAndTheEnvironmentShouldBeUnchanged() throws Exception {
		final EnrollmentCoordinator enrollment = Mockito.mock(EnrollmentCoordinator.class);
		final OtelCollectorProcessService service = service(CentralConfig.builder().enabled(true).build(), enrollment);

		service.launch();
		verify(service, timeout(5000)).start();
		final ProcessBuilder processBuilder = new ProcessBuilder("otelcol-contrib");
		service.onBeforeProcessStart(processBuilder);

		verifyNoInteractions(enrollment);
		assertEquals(new ProcessBuilder().environment(), processBuilder.environment());
	}

	@Test
	void test() throws IOException {
		final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
		final Process process = Mockito.mock(Process.class);

		try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
			processControl.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
				.thenReturn(pb);

			processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

			doReturn(process).when(pb).start();
			doReturn(new CustomInputStream("OpenTelemetry Collector started.")).when(process).getInputStream();
			doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();

			final GobblerStreamProcessor outputProcessor = new GobblerStreamProcessor();
			final GobblerStreamProcessor errorProcessor = new GobblerStreamProcessor();
			final ProcessConfig processConfig = ProcessConfig.builder()
				.commandLine(List.of("otelcol-contrib", "--config", "/opt/metricshub/otel/otel-config.yaml"))
				.output(ProcessOutput.builder().outputProcessor(outputProcessor).errorProcessor(errorProcessor).build())
				.workingDir(new File("."))
				.build();

			final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
			final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
			doReturn(processConfig).when(otelCollectorConfigMock).toProcessConfig();

			final OtelCollectorProcessService otelProcess = new OtelCollectorProcessService(agentConfig);

			otelProcess.start();

			Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
				assertEquals("OpenTelemetry Collector started.", outputProcessor.getBlocks());
				assertEquals("Error.", errorProcessor.getBlocks());
			});

			otelProcess.stop();

			assertFalse(otelProcess.isStarted(), "Process should not be started after stop");
		}
	}

	@Test
	void aSecondStartShouldNotStartASecondCollector() throws IOException {
		final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
		final Process process = Mockito.mock(Process.class);

		try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
			processControl.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
				.thenReturn(pb);
			processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();
			doReturn(process).when(pb).start();
			doReturn(new CustomInputStream("")).when(process).getInputStream();
			doReturn(new CustomInputStream("")).when(process).getErrorStream();
			final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
			doReturn(
				ProcessConfig.builder()
					.commandLine(List.of("otelcol-contrib", "--config", "/opt/metricshub/otel/otel-config.yaml"))
					.workingDir(new File("."))
					.build()
			).when(otelCollectorConfigMock).toProcessConfig();
			final OtelCollectorProcessService otelProcess = new OtelCollectorProcessService(
				AgentConfig.builder().otelCollector(otelCollectorConfigMock).build()
			);

			// A deferred launch released after a relaunch that already started the collector
			otelProcess.start();
			otelProcess.start();

			Mockito.verify(pb, Mockito.times(1)).start();
			otelProcess.stop();
		}
	}

	@Test
	void testWithoutOutputProcessor() throws IOException {
		final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
		final Process process = Mockito.mock(Process.class);

		try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
			processControl.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
				.thenReturn(pb);

			processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

			doReturn(process).when(pb).start();
			doReturn(new CustomInputStream("OpenTelemetry Collector started.")).when(process).getInputStream();
			doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();

			final ProcessConfig processConfig = ProcessConfig.builder()
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
			processControl.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
				.thenReturn(pb);

			processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

			doReturn(process).when(pb).start();
			doReturn(new CustomInputStream("OpenTelemetry Collector started.")).when(process).getInputStream();
			doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();

			final GobblerStreamProcessor outputProcessor = new GobblerStreamProcessor();
			final ProcessConfig processConfig = ProcessConfig.builder()
				.commandLine(List.of("otelcol-contrib", "--config", "/opt/metricshub/otel/otel-config.yaml"))
				.output(ProcessOutput.builder().outputProcessor(outputProcessor).build())
				.workingDir(new File("."))
				.build();

			final OtelCollectorConfig otelCollectorConfigMock = Mockito.mock(OtelCollectorConfig.class);
			final AgentConfig agentConfig = AgentConfig.builder().otelCollector(otelCollectorConfigMock).build();
			doReturn(processConfig).when(otelCollectorConfigMock).toProcessConfig();

			final OtelCollectorProcessService otelProcess = new OtelCollectorProcessService(agentConfig);

			otelProcess.start();

			Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
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
		doReturn(ProcessConfig.builder().commandLine(List.of(UUID.randomUUID().toString())).build()).when(
			otelCollectorConfigMock
		).toProcessConfig();
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
