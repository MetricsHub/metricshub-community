package org.metricshub.agent.process.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.process.config.Slf4jLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessorHelperTest {

	private static final String PROCESS_OUTPUT = "test";

	@Test
	void testCreators() {
		final Logger logger = LoggerFactory.getLogger(ProcessorHelperTest.class);
		assertEquals(ConsoleStreamProcessor.class, ProcessorHelper.console(false).getClass());
		assertEquals(NamedStreamProcessor.class, ProcessorHelper.namedConsole("test", false).getClass());
		assertEquals(Slf4jStreamProcessor.class, ProcessorHelper.logger(logger, Slf4jLevel.INFO).getClass());
		assertEquals(Slf4jSafeStreamProcessor.class, ProcessorHelper.safeLogger(logger, Slf4jLevel.INFO).getClass());
		assertEquals(NamedStreamProcessor.class, ProcessorHelper.namedLogger("test", logger, Slf4jLevel.INFO).getClass());
	}

	@Test
	void testConnectLineReader() {
		final CustomInputStream in = new CustomInputStream(PROCESS_OUTPUT);
		final Reader reader = new InputStreamReader(in);
		final GobblerStreamProcessor processor = new GobblerStreamProcessor();

		// Connect the reader to the process
		final Optional<Thread> readerProcessorOpt = ProcessorHelper.connect(reader, processor, LineReaderProcessor::new);

		assertTrue(readerProcessorOpt.isPresent());

		final Thread lineReaderProcessor = readerProcessorOpt.get();

		// Wait a bit until the thread terminates
		Awaitility
			.await()
			.atMost(Durations.FIVE_HUNDRED_MILLISECONDS)
			.untilAsserted(() -> assertFalse(lineReaderProcessor.isAlive()));

		assertEquals(PROCESS_OUTPUT, processor.getBlocks());

		assertEquals(Optional.empty(), ProcessorHelper.connect(null, processor, LineReaderProcessor::new));
		assertEquals(Optional.empty(), ProcessorHelper.connect(reader, null, LineReaderProcessor::new));
	}

	@Test
	void testConnectReader() {
		final CustomInputStream in = new CustomInputStream(PROCESS_OUTPUT);
		final Reader reader = new InputStreamReader(in);
		final GobblerStreamProcessor processor = new GobblerStreamProcessor();

		// Connect the reader to the process
		final Optional<Thread> readerProcessorOpt = ProcessorHelper.connect(reader, processor, ReaderProcessor::new);

		assertTrue(readerProcessorOpt.isPresent());

		final Thread lineReaderProcessor = readerProcessorOpt.get();

		// Wait a bit until the thread terminates
		Awaitility
			.await()
			.atMost(Durations.FIVE_HUNDRED_MILLISECONDS)
			.untilAsserted(() -> assertFalse(lineReaderProcessor.isAlive()));

		assertEquals(PROCESS_OUTPUT, processor.getBlocks());

		assertEquals(Optional.empty(), ProcessorHelper.connect(null, processor, ReaderProcessor::new));
		assertEquals(Optional.empty(), ProcessorHelper.connect(reader, null, ReaderProcessor::new));
	}

	@Test
	void testConnectWithThrowingReader() {
		final TestThrowingInputStream in = new TestThrowingInputStream();
		final Reader reader = new InputStreamReader(in);
		final GobblerStreamProcessor processor = new GobblerStreamProcessor();

		// Connect the reader to the process
		final Optional<Thread> readerProcessorOpt = ProcessorHelper.connect(reader, processor, LineReaderProcessor::new);

		assertTrue(readerProcessorOpt.isPresent());

		final Thread lineReaderProcessor = readerProcessorOpt.get();

		// Wait a bit until the thread terminates
		Awaitility
			.await()
			.atMost(Durations.FIVE_HUNDRED_MILLISECONDS)
			.untilAsserted(() -> assertFalse(lineReaderProcessor.isAlive()));

		assertTrue(processor.getBlocks().isEmpty());
	}

	/**
	 * Throws an exception when read is called
	 */
	class TestThrowingInputStream extends InputStream {

		@Override
		public int read() throws IOException {
			throw new IOException();
		}
	}
}
