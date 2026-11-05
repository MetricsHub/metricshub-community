package org.metricshub.agent.process.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.process.io.CustomInputStream;
import org.mockito.Mockito;

class ProcessControlTest {

	@Test
	void testNewProcessBuilder() {
		final Map<String, String> emptyMap = Collections.emptyMap();
		final List<String> emptyList = Collections.emptyList();
		final List<String> cmdLine = List.of("cmd");
		assertThrows(IllegalArgumentException.class, () -> ProcessControl.newProcessBuilder(null, emptyMap, null, false));
		assertThrows(
			IllegalArgumentException.class,
			() -> ProcessControl.newProcessBuilder(emptyList, emptyMap, null, false)
		);
		assertThrows(IllegalArgumentException.class, () -> ProcessControl.newProcessBuilder(cmdLine, null, null, false));
		assertDoesNotThrow(() -> ProcessControl.newProcessBuilder(cmdLine, emptyMap, null, false));
		assertDoesNotThrow(() -> ProcessControl.newProcessBuilder(cmdLine, emptyMap, new File(""), false));
	}

	@Test
	void testStop() {
		final Process process = Mockito.mock(Process.class);
		final ProcessControl pc = new ProcessControl(process);
		doReturn(new CustomInputStream("OpenTelemetry Collector started.")).when(process).getInputStream();
		doReturn(new CustomInputStream("Error.")).when(process).getErrorStream();
		assertDoesNotThrow(() -> pc.stop());

		final OutputStream outputStream = new OutputStream() {
			@Override
			public void write(int b) throws IOException {}

			@Override
			public void close() throws IOException {
				// Throw the IO Exception
				throw new IOException("exception");
			}
		};

		doReturn(outputStream).when(process).getOutputStream();
		assertDoesNotThrow(() -> pc.stop());
	}

	@Test
	void testAddShutdownHook() {
		assertDoesNotThrow(() -> ProcessControl.addShutdownHook(() -> {}));
	}
}
