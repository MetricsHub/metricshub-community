package org.metricshub.agent.service;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import lombok.Getter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.opentelemetry.LogContextSetter;
import org.metricshub.agent.opentelemetry.client.IOtelClient;

/**
 * Test helper class.
 */
public class TestHelper {

	/**
	 * Mock IOtelClient to capture the ExportMetricsServiceRequest.
	 */
	public static class TestOtelClient implements IOtelClient {

		@Getter
		private ExportMetricsServiceRequest request;

		@Override
		public void send(ExportMetricsServiceRequest request, LogContextSetter logContextSetter) {
			this.request = request;
		}

		@Override
		public void shutdown() {}
	}

	/**
	 * Configure the 'org.metricshub' logger based on the user's command.<br>
	 * See src/main/resources/log4j2.xml for the logger configuration.
	 */
	public static void configureGlobalLogger() {
		final Level loggerLevel = Level.ERROR;

		ThreadContext.put("logId", "metricshub-agent-global");
		ThreadContext.put("loggerLevel", loggerLevel.toString());
		ThreadContext.put("outputDirectory", "target");
	}
}
