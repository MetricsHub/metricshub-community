package org.metricshub.agent.process.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.process.config.ProcessConfig;
import org.metricshub.agent.process.config.ProcessOutput;
import org.metricshub.agent.process.io.CustomInputStream;
import org.metricshub.agent.process.io.GobblerStreamProcessor;
import org.metricshub.agent.process.io.LineReaderProcessor;
import org.metricshub.agent.process.io.ProcessorHelper;
import org.metricshub.agent.service.TestHelper;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class AbstractProcessTest {

	@BeforeAll
	static void initLoggingContext() {
		TestHelper.configureGlobalLogger();
	}

	@Test
	void test() throws IOException {
		{
			// Runs a mocked process and process STDOUT and STDERR

			final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
			final Process process = Mockito.mock(Process.class);

			try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
				processControl
					.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
					.thenReturn(pb);

				processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

				doReturn(process).when(pb).start();
				doReturn(new CustomInputStream("Process started.")).when(process).getInputStream();
				doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();

				final GobblerStreamProcessor outputProcessor = new GobblerStreamProcessor();
				final GobblerStreamProcessor errorProcessor = new GobblerStreamProcessor();

				final TestProcess testProcess = new TestProcess(
					ProcessConfig
						.builder()
						.commandLine(List.of("otelcol-contrib", "--config", "/opt/hws-otel-collector/config/otel-config.yaml"))
						.output(ProcessOutput.builder().outputProcessor(outputProcessor).errorProcessor(errorProcessor).build())
						.environment(Collections.emptyMap())
						.workingDir(new File("."))
						.build()
				);

				testProcess.start();

				Awaitility
					.await()
					.atMost(Durations.FIVE_SECONDS)
					.untilAsserted(() -> {
						assertEquals("Process started.", outputProcessor.getBlocks());
						assertEquals("Error.", errorProcessor.getBlocks());
					});

				assertTrue(testProcess.onBeforeProcess);
				assertTrue(testProcess.onBeforeProcessStart);
				assertTrue(testProcess.onAfterProcessStart);

				testProcess.stop();

				assertFalse(testProcess.started);
				assertTrue(testProcess.onBeforeProcessStop);
				assertTrue(testProcess.onAfterProcessStop);

				assertDoesNotThrow(testProcess::stop);
			}
		}

		{
			// Runs a mocked process and process STDOUT only

			final ProcessBuilder pb = Mockito.mock(ProcessBuilder.class);
			final Process process = Mockito.mock(Process.class);

			try (MockedStatic<ProcessControl> processControl = mockStatic(ProcessControl.class)) {
				processControl
					.when(() -> ProcessControl.newProcessBuilder(anyList(), anyMap(), any(File.class), anyBoolean()))
					.thenReturn(pb);

				processControl.when(() -> ProcessControl.start(any(ProcessBuilder.class))).thenCallRealMethod();

				doReturn(process).when(pb).start();
				doReturn(new CustomInputStream("Process started.")).when(process).getInputStream();

				final GobblerStreamProcessor outputProcessor = new GobblerStreamProcessor();

				final TestProcess testProcess = new TestProcess(
					ProcessConfig
						.builder()
						.commandLine(List.of("otelcol-contrib", "--config", "/opt/hws-otel-collector/config/otel-config.yaml"))
						.output(ProcessOutput.builder().outputProcessor(outputProcessor).build())
						.environment(Collections.emptyMap())
						.workingDir(new File("."))
						.build()
				);

				testProcess.start();

				Awaitility
					.await()
					.atMost(Durations.FIVE_SECONDS)
					.untilAsserted(() -> {
						assertEquals("Process started.", outputProcessor.getBlocks());
					});

				assertTrue(testProcess.onBeforeProcess);
				assertTrue(testProcess.onBeforeProcessStart);
				assertTrue(testProcess.onAfterProcessStart);

				testProcess.stop();

				assertFalse(testProcess.started);
				assertTrue(testProcess.onBeforeProcessStop);
				assertTrue(testProcess.onAfterProcessStop);

				assertDoesNotThrow(testProcess::stop);
			}
		}
	}

	class TestProcess extends AbstractProcess {

		boolean onBeforeProcess;
		boolean onBeforeProcessStart;
		boolean onAfterProcessStart;
		boolean onBeforeProcessStop;
		boolean onAfterProcessStop;

		protected TestProcess(ProcessConfig processConfig) throws IOException {
			super(processConfig);
		}

		@Override
		protected void onBeforeProcess() {
			onBeforeProcess = true;
		}

		@Override
		protected void onBeforeProcessStart(ProcessBuilder processBuilder) {
			onBeforeProcessStart = true;
		}

		@Override
		protected void onAfterProcessStart() {
			onAfterProcessStart = true;
			final ProcessOutput outputConfig = processConfig.getOutput();
			ProcessorHelper.connect(getReader(), outputConfig.getOutputProcessor(), LineReaderProcessor::new);
			if (outputConfig.getErrorProcessor() != null) {
				ProcessorHelper.connect(getError(), outputConfig.getErrorProcessor(), LineReaderProcessor::new);
			}
		}

		@Override
		protected void onBeforeProcessStop() {
			onBeforeProcessStop = true;
		}

		@Override
		protected void onAfterProcessStop() {
			onAfterProcessStop = true;
		}

		@Override
		protected void stopInternal() {
			super.stopProcess();
		}
	}
}
