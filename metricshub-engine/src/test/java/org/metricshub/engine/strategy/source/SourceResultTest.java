package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;

class SourceResultTest {

	private static final String HARDCODED_SOURCE = "Hardcoded Source";
	private static final String SOURCE_REF_KEY = "${source::monitors.cpu.discovery.sources.source5}";

	/**
	 * A text is stored with LF line endings, whatever produced it. A carriage return that no line feed follows stays.
	 * CR CR LF is one line ending too.
	 */
	@Test
	void testTextIsStoredWithLfLineEndings() {
		assertEquals("a\nb\rc\n", SourceResult.ofText("a\r\nb\rc\r\n").getText());
		assertEquals("a\nb", SourceResult.ofText("a\r\r\nb").getText());

		final SourceResult sourceResult = SourceResult.empty();
		sourceResult.setText("a;b\r\n\r\nc;");
		assertEquals("a;b\n\nc;", sourceResult.getText());
		assertEquals("a;b\n\nc;", sourceResult.asText());
		assertEquals(SourceResult.ofText("a;b\n\nc;"), sourceResult);
	}

	@Test
	void testLookupSourceResultFromHardcodedSource() {
		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME);

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostProperties(hostProperties).build();

		// What is not a source reference is no source: a hard-coded table is never parsed
		assertTrue(SourceResult.lookupSourceResult(HARDCODED_SOURCE, MY_CONNECTOR_1_NAME, telemetryManager).isEmpty());
		assertTrue(SourceResult.lookupSourceResult("a;b;c", MY_CONNECTOR_1_NAME, telemetryManager).isEmpty());
	}

	@Test
	void testLookupSourceResultFromReferencedSource() {
		final HostProperties hostProperties = HostProperties.builder().build();

		final SourceResult expected = SourceResult.ofTable(List.of(List.of("value")));

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceResult(SOURCE_REF_KEY, expected);

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostProperties(hostProperties).build();

		final Optional<SourceResult> sourceResultOpt = SourceResult.lookupSourceResult(
			SOURCE_REF_KEY,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);
		assertTrue(sourceResultOpt.isPresent());

		assertEquals(expected, sourceResultOpt.get());
	}

	@Test
	void testLookupSourceResultFromReferencedSourceNotFound() {
		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME);

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostProperties(hostProperties).build();

		assertTrue(SourceResult.lookupSourceResult(SOURCE_REF_KEY, MY_CONNECTOR_1_NAME, telemetryManager).isEmpty());
	}

	@Test
	void testLineToCsvAppendsTrailingSeparator() {
		assertEquals("a;b;c;", SourceResult.lineToCsv(List.of("a", "b", "c"), ";", false));
	}

	@Test
	void testLineToCsvConvertsNullsToEmpty() {
		assertEquals("a;;c;", SourceResult.lineToCsv(Arrays.asList("a", null, "c"), ";", false));
	}

	@Test
	void testLineToCsvReplacesSeparatorWhenEnabled() {
		assertEquals("a,b;c;", SourceResult.lineToCsv(List.of("a;b", "c"), ";", true));
	}
}
