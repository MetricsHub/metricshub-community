package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SourceFormat;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;

class FormatAsDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/formatAs/";
	}

	private Source getTestSource(final String file) throws IOException {
		return getConnector(file).getBeforeAll().get("testStaticSource");
	}

	@Test
	void testDefaultIsTable() throws IOException {
		assertEquals(SourceFormat.TABLE, getTestSource("formatAsDefault").getFormatAs());
	}

	@Test
	void testSupportedValueIsCaseInsensitive() throws IOException {
		assertEquals(SourceFormat.TABLE, getTestSource("formatAsTable").getFormatAs());
	}

	@Test
	void testUnsupportedValue() {
		final IOException e = assertThrows(IOException.class, () -> getConnector("formatAsText"));
		checkMessage(
			e,
			"Source ${source::beforeAll.testStaticSource}: formatAs 'text' is not supported. Supported formats: [table]."
		);
	}

	@Test
	void testUnknownValue() {
		final IOException e = assertThrows(IOException.class, () -> getConnector("formatAsUnknown"));
		checkMessage(e, "formatAs 'csv' is not supported. Supported formats: [table].");
	}

	@Test
	void testFormatAsIsKeptByCopyAndToString() {
		// A source declaring another supported format
		final StaticSource source = new StaticSource() {
			private static final long serialVersionUID = 1L;

			@Override
			public Set<SourceFormat> getSupportedFormats() {
				return EnumSet.of(SourceFormat.TABLE, SourceFormat.TEXT);
			}
		};
		source.setValue("testValue");
		source.setFormatAs("Text");

		assertEquals(SourceFormat.TEXT, source.getFormatAs());
		assertEquals(SourceFormat.TEXT, source.copy().getFormatAs());
		assertTrue(source.toString().contains("- formatAs=text"));
	}
}
