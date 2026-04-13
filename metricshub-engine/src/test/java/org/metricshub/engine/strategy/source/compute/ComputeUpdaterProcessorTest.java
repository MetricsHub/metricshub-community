package org.metricshub.engine.strategy.source.compute;

import static org.metricshub.engine.constants.Constants.EMPTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.connector.model.common.ConversionType;
import org.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Add;
import org.metricshub.engine.connector.model.monitor.task.source.compute.And;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ArrayTranslate;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Convert;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Divide;
import org.metricshub.engine.connector.model.monitor.task.source.compute.DuplicateColumn;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ExcludeMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Extract;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ExtractPropertyFromWbemPath;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepColumns;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepOnlyMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Multiply;
import org.metricshub.engine.connector.model.monitor.task.source.compute.PerBitTranslation;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Prepend;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Substring;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Subtract;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Translate;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComputeUpdaterProcessorTest {

	@Mock
	private ComputeProcessor computeProcessor;

	@Mock
	private Map<String, String> attributes;

	@InjectMocks
	private ComputeUpdaterProcessor computeUpdaterProcessor;

	@Test
	void testProcessAdd() {
		doNothing().when(computeProcessor).process(any(Add.class));
		computeUpdaterProcessor.process(Add.builder().column(1).value(EMPTY).build());
		verify(computeProcessor, times(1)).process(any(Add.class));
	}

	@Test
	void testProcessDivide() {
		doNothing().when(computeProcessor).process(any(Divide.class));
		computeUpdaterProcessor.process(Divide.builder().column(1).value(EMPTY).build());
		verify(computeProcessor, times(1)).process(any(Divide.class));
	}

	@Test
	void testProcessMultiply() {
		doNothing().when(computeProcessor).process(any(Multiply.class));
		computeUpdaterProcessor.process(Multiply.builder().column(1).value(EMPTY).build());
		verify(computeProcessor, times(1)).process(any(Multiply.class));
	}

	@Test
	void testProcessSubtract() {
		doNothing().when(computeProcessor).process(any(Subtract.class));
		computeUpdaterProcessor.process(Subtract.builder().column(1).value(EMPTY).build());
		verify(computeProcessor, times(1)).process(any(Subtract.class));
	}

	@Test
	void testProcessAnd() {
		doNothing().when(computeProcessor).process(any(And.class));
		computeUpdaterProcessor.process(And.builder().column(1).value(EMPTY).build());
		verify(computeProcessor, times(1)).process(any(And.class));
	}

	@Test
	void testProcessPrepend() {
		doNothing().when(computeProcessor).process(any(Prepend.class));
		computeUpdaterProcessor.process(Prepend.builder().column(1).value(EMPTY).build());
		verify(computeProcessor, times(1)).process(any(Prepend.class));
	}

	@Test
	void testProcessAppend() {
		doNothing().when(computeProcessor).process(any(Append.class));
		computeUpdaterProcessor.process(Append.builder().column(1).value(EMPTY).build());
		verify(computeProcessor, times(1)).process(any(Append.class));
	}

	@Test
	void testProcessArrayTranslate() {
		doNothing().when(computeProcessor).process(any(ArrayTranslate.class));
		computeUpdaterProcessor.process(
			ArrayTranslate.builder().column(1).translationTable(new TranslationTable()).build()
		);

		computeUpdaterProcessor.process(
			ArrayTranslate.builder().column(1).translationTable(new ReferenceTranslationTable()).build()
		);

		verify(computeProcessor, times(2)).process(any(ArrayTranslate.class));
	}

	@Test
	void testProcessJson2CSV() {
		final Json2Csv json2Csv = Json2Csv.builder().build();
		doNothing().when(computeProcessor).process(any(Json2Csv.class));
		computeUpdaterProcessor.process(json2Csv);
		verify(computeProcessor, times(1)).process(any(Json2Csv.class));
	}

	@Test
	void testProcessExcludeMatchingLines() {
		final ExcludeMatchingLines excludeMatchingLines = ExcludeMatchingLines.builder().column(-1).build();
		doNothing().when(computeProcessor).process(any(ExcludeMatchingLines.class));
		computeUpdaterProcessor.process(excludeMatchingLines);
		verify(computeProcessor, times(1)).process(any(ExcludeMatchingLines.class));
	}

	@Test
	void testProcessExtract() {
		final Extract extract = Extract.builder().column(-1).subColumn(-1).build();
		doNothing().when(computeProcessor).process(any(Extract.class));
		computeUpdaterProcessor.process(extract);
		verify(computeProcessor, times(1)).process(any(Extract.class));
	}

	@Test
	void testProcessReplace() {
		final Replace replace = Replace.builder().column(-1).build();
		doNothing().when(computeProcessor).process(any(Replace.class));
		computeUpdaterProcessor.process(replace);
		verify(computeProcessor, times(1)).process(any(Replace.class));
	}

	@Test
	void testProcessSubstring() {
		final Substring substring = Substring.builder().column(-1).start("-1").length("-1").build();
		doNothing().when(computeProcessor).process(any(Substring.class));
		computeUpdaterProcessor.process(substring);
		verify(computeProcessor, times(1)).process(any(Substring.class));
	}

	@Test
	void testProcessXML2CSV() {
		final Xml2Csv xml2Csv = Xml2Csv.builder().build();
		doNothing().when(computeProcessor).process(any(Xml2Csv.class));
		computeUpdaterProcessor.process(xml2Csv);
		verify(computeProcessor, times(1)).process(any(Xml2Csv.class));
	}

	@Test
	void testProcessKeepColumns() {
		final KeepColumns keepColumns = KeepColumns.builder().columnNumbers("-1").build();
		doNothing().when(computeProcessor).process(any(KeepColumns.class));
		computeUpdaterProcessor.process(keepColumns);
		verify(computeProcessor, times(1)).process(any(KeepColumns.class));
	}

	@Test
	void testProcessKeepOnlyMatchingLines() {
		final KeepOnlyMatchingLines keepOnlyMatchingLines = KeepOnlyMatchingLines.builder().column(-1).build();
		doNothing().when(computeProcessor).process(any(KeepOnlyMatchingLines.class));
		computeUpdaterProcessor.process(keepOnlyMatchingLines);
		verify(computeProcessor, times(1)).process(any(KeepOnlyMatchingLines.class));
	}

	@Test
	void testProcessAwk() {
		doNothing().when(computeProcessor).process(any(Awk.class));
		computeUpdaterProcessor.process(Awk.builder().script("script").build());
		verify(computeProcessor, times(1)).process(any(Awk.class));
	}

	@Test
	void testProcessDuplicateColumn() {
		final DuplicateColumn duplicateColumn = DuplicateColumn.builder().column(-1).build();
		doNothing().when(computeProcessor).process(any(DuplicateColumn.class));
		computeUpdaterProcessor.process(duplicateColumn);
		verify(computeProcessor, times(1)).process(any(DuplicateColumn.class));
	}

	@Test
	void testProcessConvert() {
		doNothing().when(computeProcessor).process(any(Convert.class));
		computeUpdaterProcessor.process(
			Convert.builder().column(1).conversion(ConversionType.ARRAY_2_SIMPLE_STATUS).build()
		);
		verify(computeProcessor, times(1)).process(any(Convert.class));
	}

	@Test
	void testProcessTranslate() {
		final Translate translate = Translate
			.builder()
			.column(-1)
			.translationTable(TranslationTable.builder().build())
			.build();
		doNothing().when(computeProcessor).process(any(Translate.class));
		computeUpdaterProcessor.process(translate);
		verify(computeProcessor, times(1)).process(any(Translate.class));
	}

	@Test
	void testProcessExtractPropertyFromWbemPath() {
		final ExtractPropertyFromWbemPath extractPropertyFromWbemPath = ExtractPropertyFromWbemPath
			.builder()
			.property("property")
			.column(-1)
			.build();
		doNothing().when(computeProcessor).process(any(ExtractPropertyFromWbemPath.class));
		computeUpdaterProcessor.process(extractPropertyFromWbemPath);
		verify(computeProcessor, times(1)).process(any(ExtractPropertyFromWbemPath.class));
	}

	@Test
	void testProcessPerBitTranslation() {
		final PerBitTranslation perBitTranslation = PerBitTranslation
			.builder()
			.column(-1)
			.bitList(EMPTY)
			.translationTable(TranslationTable.builder().build())
			.build();
		doNothing().when(computeProcessor).process(any(PerBitTranslation.class));
		computeUpdaterProcessor.process(perBitTranslation);
		verify(computeProcessor, times(1)).process(any(PerBitTranslation.class));
	}
}
