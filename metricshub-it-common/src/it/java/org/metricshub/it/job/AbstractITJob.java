package org.metricshub.it.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import lombok.Data;
import lombok.NonNull;
import org.metricshub.engine.alert.AlertRule;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.strategy.IStrategy;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorsVo;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;

@Data
public abstract class AbstractITJob implements ITJob {

	@NonNull
	protected final TelemetryManager telemetryManager;

	/**
	 * Assert that expected and actual are equal.
	 *
	 * @param expected
	 * @param actual
	 */
	private static void assertMonitor(final Monitor expected, final Monitor actual) {
		assertMetrics(expected, actual);
		assertMonitorAttributes(expected, actual);
		assertConditionalCollection(expected, actual);
		assertLegacyTextParameters(expected, actual);
		assertAlertRules(expected, actual);
		assertNotNull(actual.getDiscoveryTime());

		final String expectedMonitorId = expected.getId();
		assertEquals(
			expected.getType(),
			actual.getType(),
			() -> String.format("Type doesn't match actual on monitor identifier: %s.", expectedMonitorId)
		);
		assertEquals(
			expected.getId(),
			actual.getId(),
			() -> String.format("ID doesn't match actual on monitor identifier: %s.", expectedMonitorId)
		);
		assertEquals(
			expected.isEndpoint(),
			actual.isEndpoint(),
			() -> String.format("isEndpoint doesn't match actual on monitor identifier: %s.", expectedMonitorId)
		);
		assertEquals(
			expected.isEndpointHost(),
			actual.isEndpointHost(),
			() -> String.format("isEndpointHost doesn't match actual on monitor identifier: %s.", expectedMonitorId)
		);
	}

	/**
	 * Assert that expected and actual alert rules are equal. <br>
	 * We only test testable/important data. For example the {@link AlertRule} conditionsChecker cannot be checked as it is a function
	 *
	 * @param expectedMonitor Expected monitor defined in the expected JSON file
	 * @param actualMonitor   Actual collected monitor from the {@link TelemetryManager}
	 */
	private static void assertAlertRules(final Monitor expectedMonitor, final Monitor actualMonitor) {
		// Alert rules are not available yet
		assertTrue(
			actualMonitor.getAlertRules().isEmpty(),
			() -> String.format("Alert rules are not empty on monitor identifier: %s.", actualMonitor.getId())
		);
	}

	/**
	 * Assert that expected and actual metrics are equal. <br>
	 *
	 * @param expectedMonitor Expected monitor defined in the expected JSON file
	 * @param actualMonitor   Actual collected monitor from the {@link TelemetryManager}
	 */
	private static void assertMetrics(final Monitor expectedMonitor, final Monitor actualMonitor) {
		for (final Entry<String, AbstractMetric> expectedEntry : expectedMonitor.getMetrics().entrySet()) {
			final AbstractMetric expectedMetric = expectedEntry.getValue();
			final String expectedKey = expectedEntry.getKey();
			final String expectedMonitorId = expectedMonitor.getId();

			assertNotNull(
				expectedMetric,
				() -> String.format("Expected metric cannot be null for monitor identifier: %s.", expectedMonitorId)
			);

			final String expectedMetricName = expectedMetric.getName();

			final AbstractMetric actualMetric = actualMonitor.getMetric(expectedMetricName, expectedMetric.getClass());

			assertNotNull(
				actualMetric,
				() ->
					String.format(
						"Cannot find actual metric on monitor identifier: %s. Metric name: %s.",
						expectedMonitorId,
						expectedKey
					)
			);

			assertEquals(
				expectedMetricName,
				actualMetric.getName(),
				() ->
					String.format(
						"Name doesn’t match actual on monitor identifier: %s. Metric name: %s.",
						expectedMonitorId,
						expectedMetricName
					)
			);

			assertNotNull(
				actualMetric.getCollectTime(),
				() ->
					String.format(
						"CollectTime doesn’t match actual on monitor identifier: %s. Metric name: %s.",
						expectedMonitorId,
						expectedMetricName
					)
			);

			assertMetricAttributes(expectedMetric, actualMetric, expectedMonitorId);

			assertEquals(
				expectedMetric.isResetMetricTime(),
				actualMetric.isResetMetricTime(),
				() ->
					String.format(
						"IsResetMetricTime doesn't match actual on monitor identifier: %s.  Metric name: %s.",
						expectedMonitorId,
						expectedMetricName
					)
			);

			final Object expectedValue = expectedMetric.getValue();
			final Object actualValue = actualMetric.getValue();
			assertEquals(
				expectedValue,
				actualValue,
				() ->
					String.format(
						"Value doesn't match actual on monitor identifier: %s. Metric name: %s.",
						expectedMonitorId,
						expectedMetricName
					)
			);
		}
	}

	/**
	 * Assert that expected and actual monitor attributes are equal
	 *
	 * @param expectedMonitor Expected monitor defined in the expected JSON file
	 * @param actualMonitor   Actual collected monitor from the {@link TelemetryManager}
	 */
	private static void assertMonitorAttributes(final Monitor expectedMonitor, final Monitor actualMonitor) {
		for (final Entry<String, String> expectedEntry : expectedMonitor.getAttributes().entrySet()) {
			final String expected = expectedEntry.getValue();
			final String expectedKey = expectedEntry.getKey();

			final String actual = actualMonitor.getAttribute(expectedKey);

			assertEquals(
				expected,
				actual,
				() ->
					String.format(
						"Actual monitor's attribute did not match expected: %s on monitor identifier: %s.",
						expectedKey,
						expectedMonitor.getId()
					)
			);
		}
	}

	/**
	 * Assert that expected and actual metric attributes are equal
	 *
	 * @param expectedMetric    Expected metric defined in the expected JSON file
	 * @param actualMetric      Actual collected metric from the {@link TelemetryManager}
	 * @param expectedMonitorId Used to add more context in case the test has failed
	 */
	private static void assertMetricAttributes(
		final AbstractMetric expectedMetric,
		final AbstractMetric actualMetric,
		final String expectedMonitorId
	) {
		for (final Entry<String, String> expectedEntry : expectedMetric.getAttributes().entrySet()) {
			final String expected = expectedEntry.getValue();

			final String expectedKey = expectedEntry.getKey();

			final String actual = actualMetric.getAttributes().get(expectedKey);

			assertEquals(
				expected,
				actual,
				() ->
					String.format(
						"actual attribute did not match expected: %s on monitor identifier: %s.",
						expectedKey,
						expectedMonitorId
					)
			);
		}
	}

	/**
	 * Assert that expected and actual conditional collection are equal
	 *
	 * @param expectedMonitor Expected monitor defined in the expected JSON file
	 * @param actualMonitor   Actual collected monitor from the {@link TelemetryManager}
	 */
	private static void assertConditionalCollection(final Monitor expectedMonitor, final Monitor actualMonitor) {
		for (final Entry<String, String> expectedEntry : expectedMonitor.getConditionalCollection().entrySet()) {
			final String expected = expectedEntry.getValue();
			final String expectedKey = expectedEntry.getKey();

			final String actual = actualMonitor.getConditionalCollection().get(expectedKey);

			assertEquals(
				expected,
				actual,
				() ->
					String.format(
						"Actual conditional collection did not match expected: %s on monitor identifier: %s." + expectedKey,
						expectedMonitor.getId()
					)
			);
		}
	}

	/**
	 * Assert that expected and actual legacy text parameters are equal
	 *
	 * @param expectedMonitor Expected monitor defined in the expected JSON file
	 * @param actualMonitor   Actual collected monitor from the {@link TelemetryManager}
	 */
	private static void assertLegacyTextParameters(final Monitor expectedMonitor, final Monitor actualMonitor) {
		for (final Entry<String, String> expectedEntry : expectedMonitor.getLegacyTextParameters().entrySet()) {
			final String expected = expectedEntry.getValue();
			final String expectedKey = expectedEntry.getKey();

			final String actual = actualMonitor.getLegacyTextParameters().get(expectedKey);

			assertEquals(
				expected,
				actual,
				() ->
					String.format(
						"Actual LegacyTextParameter did not match expected: %s on monitor identifier: %s.",
						expectedKey,
						expectedMonitor.getId()
					)
			);
		}
	}

	@Override
	public ITJob verifyExpected(final String expectedPath) throws Exception {
		stopServer();

		final InputStream is = ITJobUtils.getItResourceAsInputStream(expectedPath);
		final MonitorsVo expectedMonitors = JsonHelper.deserialize(is, MonitorsVo.class);

		final MonitorsVo actual = telemetryManager.getVo();

		assertEquals(expectedMonitors.getTotal(), actual.getTotal());

		expectedMonitors
			.getMonitors()
			.forEach(expectedMonitor -> {
				final String expectedType = expectedMonitor.getType();
				assertNotNull(
					expectedType,
					() ->
						String.format("Expected monitor 'type' cannot be null for monitor identifier: %s.", expectedMonitor.getId())
				);
				final Monitor actualMonitor = telemetryManager.findMonitorByTypeAndId(expectedType, expectedMonitor.getId());
				assertMonitor(expectedMonitor, actualMonitor);
			});

		return this;
	}

	@Override
	public ITJob executeStrategies(final IStrategy... strategies) {
		assertTrue(isServerStarted(), () -> "Server not started.");

		for (IStrategy strategy : strategies) {
			telemetryManager.run(strategy);
		}

		return this;
	}

	@Override
	public ITJob saveTelemetryManagerJson(Path path) throws IOException {
		Files.write(path, telemetryManager.toJson().getBytes());

		return this;
	}
}
