package org.metricshub.it.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractITJobTest {

	@Test
	void testSkipMetric() {
		final record TestCase(String metricName, boolean expected) {}
		final var testCases = List.of(
			new TestCase("metricshub.job.duration", true),
			new TestCase("metricshub.job.duration.last_run", false),
			new TestCase("metricshub.job.duration{}", true),
			new TestCase("metricshub.job.duration{job.type=\"discovery\", monitor.type=\"connector\"}", true),
			new TestCase("metricshub.job.success", false),
			new TestCase("metricshub.system.cpu.usage", false)
		);
		// Execute test cases
		for (var testCase : testCases) {
			assertEquals(
				testCase.expected(),
				AbstractITJob.skipMetric(testCase.metricName()),
				"skipMetric failed for metric name: " + testCase.metricName()
			);
		}
	}
}
