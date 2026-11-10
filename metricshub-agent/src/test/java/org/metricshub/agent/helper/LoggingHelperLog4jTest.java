package org.metricshub.agent.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.status.StatusData;
import org.apache.logging.log4j.status.StatusListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.LoggingHelper;

/**
 * Runs {@link LoggingHelper} through the real Log4j SLF4J binding. When a message pattern
 * has more placeholders than arguments, Log4j reports it on stderr through its
 * {@link StatusLogger}, outside the configured appenders.
 */
class LoggingHelperLog4jTest {

	private static final String LOGGER_NAME = LoggingHelper.class.getName();

	private final List<String> loggedMessages = new CopyOnWriteArrayList<>();

	private final List<String> statusMessages = new CopyOnWriteArrayList<>();

	private final StatusListener statusListener = new StatusListener() {
		@Override
		public void log(final StatusData data) {
			statusMessages.add(data.getFormattedStatus());
		}

		@Override
		public Level getStatusLevel() {
			return Level.WARN;
		}

		@Override
		public void close() {}
	};

	private final AbstractAppender appender = new AbstractAppender("capture", null, null, true, Property.EMPTY_ARRAY) {
		@Override
		public void append(final LogEvent event) {
			// Formatting the message is what triggers Log4j's placeholder check
			loggedMessages.add(event.getMessage().getFormattedMessage());
		}
	};

	@BeforeEach
	void setUp() {
		appender.start();
		final LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, Level.TRACE, false);
		loggerConfig.addAppender(appender, null, null);
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		context.getConfiguration().addLogger(LOGGER_NAME, loggerConfig);
		context.updateLoggers();
		StatusLogger.getLogger().registerListener(statusListener);
	}

	@AfterEach
	void tearDown() {
		StatusLogger.getLogger().removeListener(statusListener);
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		final Configuration configuration = context.getConfiguration();
		configuration.removeLogger(LOGGER_NAME);
		context.updateLoggers();
		appender.stop();
	}

	@Test
	void testLogWithHiddenPasswordMessageWithPlaceholders() {
		final String message = "Executed HTTP request\n- password: secret\n- response-body:\n{\"a\": {}, \"b\": {}}";
		LoggingHelper.logWithHiddenPassword(org.slf4j.event.Level.TRACE, () -> message);
		assertEquals(
			List.of("Executed HTTP request\n- password**********\n- response-body:\n{\"a\": {}, \"b\": {}}"),
			loggedMessages
		);
		assertEquals(List.of(), statusMessages);
	}
}
