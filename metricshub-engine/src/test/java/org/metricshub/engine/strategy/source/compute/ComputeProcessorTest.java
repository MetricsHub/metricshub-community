package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.EMPTY;
import static org.metricshub.engine.constants.Constants.LOCALHOST;
import static org.metricshub.engine.constants.Constants.SINGLE_SPACE;
import static org.metricshub.engine.constants.Constants.TABLE_SEP;
import static org.metricshub.engine.constants.Constants.VALUE_VAL1;
import static org.metricshub.engine.constants.Constants.VALUE_VAL2;
import static org.metricshub.engine.constants.Constants.VALUE_VAL3;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.ConversionType;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.common.ITranslationTable;
import org.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Add;
import org.metricshub.engine.connector.model.monitor.task.source.compute.And;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ArrayTranslate;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Convert;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Decode;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Divide;
import org.metricshub.engine.connector.model.monitor.task.source.compute.DuplicateColumn;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Encode;
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
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComputeProcessorTest {

	private SourceTable sourceTable;

	@InjectMocks
	private ComputeProcessor computeProcessor;

	@Spy
	private ClientsExecutor clientsExecutorMock;

	@Mock
	private ConnectorStore connectorStoreMock;

	private static final String DOLLAR_1 = "$1";
	private static final String UNDERSCORE_DOLLAR_1 = "_$1";
	private static final String DOLLAR_3 = "$3";
	private static final String DOLLAR_4 = "$4";
	private static final String DOLLAR_13 = "$13";
	private static final String ZERO = "0";
	private static final String ZERO_POINT_ZERO = "0.0";
	private static final String ONE = "1";
	private static final String ONE_TWO_THREE = "1,2,3";
	private static final String TWO = "2";
	private static final String FOUR_POINT_ZERO = "4.0";
	private static final String FIVE = "5";
	private static final String TEN = "10";
	private static final String FOURTEEN = "14";
	private static final String THIRTY = "30";
	private static final String THIRTY_SIX = "36";
	private static final String FORTY_ONE = "41";
	private static final String TWO_HUNDRED = "200";
	private static final String TWO_HUNDRED_AND_FIFTY_FOUR = "254";
	private static final String TWO_HUNDRED_AND_FIFTY_FIVE = "255";
	private static final String FIVE_HUNDRED = "500";
	private static final String ONE_THOUSAND_FIVE_HUNDRED = "1500";
	private static final String ANOTHER_NEW_COLUMN = "AnotherNew.Column";
	private static final String ID1 = "ID1";
	private static final String ID2 = "ID2";
	private static final String ID3 = "ID3";
	private static final String PREFIX_ID1 = "prefix_ID1";
	private static final String PREFIX_ID2 = "prefix_ID2";
	private static final String PREFIX_ID3 = "prefix_ID3";
	private static final String ID1_SUFFIX = "ID1_suffix";
	private static final String ID2_SUFFIX = "ID2_suffix";
	private static final String ID3_SUFFIX = "ID3_suffix";
	private static final String FOO = "FOO";
	private static final String BAR = "BAR";
	private static final String BAZ = "BAZ";
	private static final String MANUFACTURER1 = "MANUFACTURER1";
	private static final String MANUFACTURER2 = "MANUFACTURER2";
	private static final String MANUFACTURER3 = "MANUFACTURER3";
	private static final String PREFIX_MANUFACTURER1 = "prefix_MANUFACTURER1";
	private static final String PREFIX_MANUFACTURER2 = "prefix_MANUFACTURER2";
	private static final String PREFIX_MANUFACTURER3 = "prefix_MANUFACTURER3";
	private static final String SUFFIX_APPEND_REF_MANUFACTURER1 = "MANUFACTURER1prefix_ID1_middle_NAME1_suffix";
	private static final String SUFFIX_APPEND_REF_MANUFACTURER2 = "MANUFACTURER2prefix_ID2_middle_NAME2_suffix";
	private static final String SUFFIX_APPEND_REF_MANUFACTURER3 = "MANUFACTURER3prefix_ID3_middle_NAME3_suffix";
	private static final String PREFIX_PREPEND_REF_MANUFACTURER1 = "prefix_ID1_middle_NAME1_suffixMANUFACTURER1";
	private static final String PREFIX_PREPEND_REF_MANUFACTURER2 = "prefix_ID2_middle_NAME2_suffixMANUFACTURER2";
	private static final String PREFIX_PREPEND_REF_MANUFACTURER3 = "prefix_ID3_middle_NAME3_suffixMANUFACTURER3";
	private static final String PREFIX_PREPEND_REF_MANUFACTURER1_PROTECTED =
		"prefix_$$1_middle_NAME1_suffixMANUFACTURER1";
	private static final String PREFIX_PREPEND_REF_MANUFACTURER2_PROTECTED =
		"prefix_$$1_middle_NAME2_suffixMANUFACTURER2";
	private static final String PREFIX_PREPEND_REF_MANUFACTURER3_PROTECTED =
		"prefix_$$1_middle_NAME3_suffixMANUFACTURER3";
	private static final String SUFFIX_APPEND_REF_MANUFACTURER1_PROTECTED = "MANUFACTURER1prefix_$$1_middle_NAME1_suffix";
	private static final String SUFFIX_APPEND_REF_MANUFACTURER2_PROTECTED = "MANUFACTURER2prefix_$$1_middle_NAME2_suffix";
	private static final String SUFFIX_APPEND_REF_MANUFACTURER3_PROTECTED = "MANUFACTURER3prefix_$$1_middle_NAME3_suffix";
	private static final String MANUFACTURER1_SUFFIX = "MANUFACTURER1_suffix";
	private static final String MANUFACTURER2_SUFFIX = "MANUFACTURER2_suffix";
	private static final String MANUFACTURER3_SUFFIX = "MANUFACTURER3_suffix";
	private static final String NAME1 = "NAME1";
	private static final String NAME2 = "NAME2";
	private static final String NAME3 = "NAME3";
	private static final String NEW_COMMA_COLUMN = "new,Column";
	private static final String NEW_COMMA_NUMBER_OF_DISKS1 = "new,NUMBER_OF_DISKS1";
	private static final String NEW_COMMA_NUMBER_OF_DISKS2 = "new,NUMBER_OF_DISKS2";
	private static final String NEW_COMMA_NUMBER_OF_DISKS3 = "new,NUMBER_OF_DISKS3";
	private static final String NUMBER_OF_DISKS1 = "NUMBER_OF_DISKS1";
	private static final String NUMBER_OF_DISKS2 = "NUMBER_OF_DISKS2";
	private static final String NUMBER_OF_DISKS3 = "NUMBER_OF_DISKS3";
	private static final String PREFIX = "prefix_";
	private static final String MIDDLE_VALUE = "_middle_";
	private static final String SUFFIX = "_suffix";
	private static final String TYPE1 = "TYPE1";
	private static final String TYPE2 = "TYPE2";
	private static final String TYPE3 = "TYPE3";

	private static final List<String> LINE_1 = Arrays.asList(ID1, NAME1, MANUFACTURER1, NUMBER_OF_DISKS1);
	private static final List<String> LINE_2 = Arrays.asList(ID2, NAME2, MANUFACTURER2, NUMBER_OF_DISKS2);
	private static final List<String> LINE_3 = Arrays.asList(ID3, NAME3, MANUFACTURER3, NUMBER_OF_DISKS3);

	private static final List<String> LINE_1_RESULT_LEFT = new ArrayList<>(
		Arrays.asList(ID1, NAME1, PREFIX_MANUFACTURER1, NUMBER_OF_DISKS1)
	);
	private static final List<String> LINE_2_RESULT_LEFT = new ArrayList<>(
		Arrays.asList(ID2, NAME2, PREFIX_MANUFACTURER2, NUMBER_OF_DISKS2)
	);
	private static final List<String> LINE_3_RESULT_LEFT = new ArrayList<>(
		Arrays.asList(ID3, NAME3, PREFIX_MANUFACTURER3, NUMBER_OF_DISKS3)
	);

	private static final List<String> LINE_1_RESULT_RIGHT = new ArrayList<>(
		Arrays.asList(ID1, NAME1, MANUFACTURER1_SUFFIX, NUMBER_OF_DISKS1)
	);
	private static final List<String> LINE_2_RESULT_RIGHT = new ArrayList<>(
		Arrays.asList(ID2, NAME2, MANUFACTURER2_SUFFIX, NUMBER_OF_DISKS2)
	);
	private static final List<String> LINE_3_RESULT_RIGHT = new ArrayList<>(
		Arrays.asList(ID3, NAME3, MANUFACTURER3_SUFFIX, NUMBER_OF_DISKS3)
	);

	private static final List<String> LINE_1_RESULT_APPEND_REF = new ArrayList<>(
		Arrays.asList(ID1, NAME1, SUFFIX_APPEND_REF_MANUFACTURER1, NUMBER_OF_DISKS1)
	);
	private static final List<String> LINE_2_RESULT_APPEND_REF = new ArrayList<>(
		Arrays.asList(ID2, NAME2, SUFFIX_APPEND_REF_MANUFACTURER2, NUMBER_OF_DISKS2)
	);
	private static final List<String> LINE_3_RESULT_APPEND_REF = new ArrayList<>(
		Arrays.asList(ID3, NAME3, SUFFIX_APPEND_REF_MANUFACTURER3, NUMBER_OF_DISKS3)
	);

	private static final List<String> LINE_1_RESULT_PREPEND_REF = new ArrayList<>(
		Arrays.asList(ID1, NAME1, PREFIX_PREPEND_REF_MANUFACTURER1, NUMBER_OF_DISKS1)
	);
	private static final List<String> LINE_2_RESULT_PREPEND_REF = new ArrayList<>(
		Arrays.asList(ID2, NAME2, PREFIX_PREPEND_REF_MANUFACTURER2, NUMBER_OF_DISKS2)
	);
	private static final List<String> LINE_3_RESULT_PREPEND_REF = new ArrayList<>(
		Arrays.asList(ID3, NAME3, PREFIX_PREPEND_REF_MANUFACTURER3, NUMBER_OF_DISKS3)
	);

	private static final List<String> LINE_1_RESULT_PREPEND_REF_PROTECTED = new ArrayList<>(
		Arrays.asList(ID1, NAME1, PREFIX_PREPEND_REF_MANUFACTURER1_PROTECTED, NUMBER_OF_DISKS1)
	);
	private static final List<String> LINE_2_RESULT_PREPEND_REF_PROTECTED = new ArrayList<>(
		Arrays.asList(ID2, NAME2, PREFIX_PREPEND_REF_MANUFACTURER2_PROTECTED, NUMBER_OF_DISKS2)
	);
	private static final List<String> LINE_3_RESULT_PREPEND_REF_PROTECTED = new ArrayList<>(
		Arrays.asList(ID3, NAME3, PREFIX_PREPEND_REF_MANUFACTURER3_PROTECTED, NUMBER_OF_DISKS3)
	);

	private static final List<String> LINE_1_RESULT_APPEND_REF_PROTECTED = new ArrayList<>(
		Arrays.asList(ID1, NAME1, SUFFIX_APPEND_REF_MANUFACTURER1_PROTECTED, NUMBER_OF_DISKS1)
	);
	private static final List<String> LINE_2_RESULT_APPEND_REF_PROTECTED = new ArrayList<>(
		Arrays.asList(ID2, NAME2, SUFFIX_APPEND_REF_MANUFACTURER2_PROTECTED, NUMBER_OF_DISKS2)
	);
	private static final List<String> LINE_3_RESULT_APPEND_REF_PROTECTED = new ArrayList<>(
		Arrays.asList(ID3, NAME3, SUFFIX_APPEND_REF_MANUFACTURER3_PROTECTED, NUMBER_OF_DISKS3)
	);

	private static final List<String> LINE_1_ONE_COLUMN = new ArrayList<>(Collections.singletonList(ID1));
	private static final List<String> LINE_2_ONE_COLUMN = new ArrayList<>(Collections.singletonList(ID2));
	private static final List<String> LINE_3_ONE_COLUMN = new ArrayList<>(Collections.singletonList(ID3));

	private static final List<String> LINE_WBEM_PATH_1 = Arrays.asList(
		ID1,
		NAME1,
		MANUFACTURER1,
		"Symm_StorageSystem.CreationClassName=\"Symm_StorageSystem\",Name=\"SYMMETRIX-+-NAME1\""
	);
	private static final List<String> LINE_WBEM_PATH_2 = Arrays.asList(
		ID2,
		NAME2,
		MANUFACTURER2,
		"Symm_StorageSystem.CreationClassName=\"Symm_StorageSystem\",Symm_StorageSystem.Name=\"SYMMETRIX-+-NAME2\""
	);
	private static final List<String> LINE_WBEM_PATH_3 = Arrays.asList(
		ID3,
		NAME3,
		MANUFACTURER3,
		"Symm_StorageSystem.CreationClassName=\"Symm_StorageSystem\",NotAName=\"NotAName\",Name=\"SYMMETRIX-+-NAME3\""
	);

	private static final List<String> LINE_WBEM_PATH_1_RESULT = Arrays.asList(
		ID1,
		NAME1,
		MANUFACTURER1,
		"SYMMETRIX-+-NAME1"
	);
	private static final List<String> LINE_WBEM_PATH_2_RESULT = Arrays.asList(
		ID2,
		NAME2,
		MANUFACTURER2,
		"SYMMETRIX-+-NAME2"
	);
	private static final List<String> LINE_WBEM_PATH_3_RESULT = Arrays.asList(
		ID3,
		NAME3,
		MANUFACTURER3,
		"SYMMETRIX-+-NAME3"
	);

	private TelemetryManager telemetryManager;

	@BeforeEach
	void setUp() {
		computeProcessor = new ComputeProcessor();
		sourceTable = new SourceTable();
		computeProcessor.setSourceTable(sourceTable);
		computeProcessor.setHostname(LOCALHOST);
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.WINDOWS).build()
				)
				.build();
		clientsExecutorMock.setTelemetryManager(telemetryManager);
		computeProcessor.setClientsExecutor(clientsExecutorMock);
	}

	private void initializeSourceTable() {
		sourceTable.getTable().clear();
		sourceTable.getTable().add(new ArrayList<>(LINE_1));
		sourceTable.getTable().add(new ArrayList<>(LINE_2));
		sourceTable.getTable().add(new ArrayList<>(LINE_3));
	}

	@Test
	void testProcessAdd() {
		List<List<String>> table = Arrays.asList(
			Arrays.asList(ID1, FIVE_HUNDRED, TWO, VALUE_VAL1),
			Arrays.asList(ID2, ONE_THOUSAND_FIVE_HUNDRED, FIVE, VALUE_VAL2),
			Arrays.asList(ID1, TWO_HUNDRED, TWO, VALUE_VAL3)
		);

		sourceTable.setTable(table);

		computeProcessor.process((Add) null);
		assertEquals(table, sourceTable.getTable());

		Add addition = Add.builder().column(-1).value(EMPTY).build();
		computeProcessor.process(addition);
		assertEquals(table, sourceTable.getTable());

		addition = Add.builder().column(1).value(DOLLAR_13).build();
		computeProcessor.process(addition);
		assertEquals(table, sourceTable.getTable());

		addition = Add.builder().column(1).value(ZERO).build();
		computeProcessor.process(addition);
		assertEquals(table, sourceTable.getTable());

		final List<List<String>> result = Arrays.asList(
			Arrays.asList(ID1, "502.0", TWO, VALUE_VAL1),
			Arrays.asList(ID2, "1505.0", FIVE, VALUE_VAL2),
			Arrays.asList(ID1, "202.0", TWO, VALUE_VAL3)
		);

		Add addColumn = Add.builder().column(2).value(DOLLAR_3).build();
		computeProcessor.process(addColumn);
		assertEquals(result, sourceTable.getTable());

		addColumn = Add.builder().column(2).value(DOLLAR_1).build();
		computeProcessor.process(addColumn);
		assertEquals(result, sourceTable.getTable());

		addColumn = Add.builder().column(2).value(ID1).build();
		computeProcessor.process(addColumn);
		assertEquals(result, sourceTable.getTable());

		final Add addValue = Add.builder().column(2).value(TEN).build();
		computeProcessor.process(addValue);
		assertEquals(
			Arrays.asList(
				Arrays.asList(ID1, "512.0", TWO, VALUE_VAL1),
				Arrays.asList(ID2, "1515.0", FIVE, VALUE_VAL2),
				Arrays.asList(ID1, "212.0", TWO, VALUE_VAL3)
			),
			sourceTable.getTable()
		);

		Add emptyAdd = Add.builder().column(4).value(FIVE).build();
		table =
			Arrays.asList(
				Arrays.asList(ID1, FIVE_HUNDRED, TWO, EMPTY),
				Arrays.asList(ID2, ONE_THOUSAND_FIVE_HUNDRED, FIVE, EMPTY),
				Arrays.asList(ID1, TWO_HUNDRED, TWO, EMPTY)
			);
		sourceTable.setTable(table);
		computeProcessor.process(emptyAdd);
		assertEquals(table, sourceTable.getTable());

		emptyAdd = Add.builder().column(2).value(DOLLAR_4).build();
		computeProcessor.process(emptyAdd);
		assertEquals(table, sourceTable.getTable());
	}

	@Test
	void testProcessDivide() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList(ID1, FIVE_HUNDRED, TWO, VALUE_VAL1),
			Arrays.asList(ID2, ONE_THOUSAND_FIVE_HUNDRED, FIVE, VALUE_VAL2),
			Arrays.asList(ID1, TWO_HUNDRED, TWO, VALUE_VAL3)
		);

		sourceTable.setTable(table);

		computeProcessor.process((Divide) null);
		assertEquals(table, sourceTable.getTable());

		Divide divide = Divide.builder().column(-1).value(EMPTY).build();
		computeProcessor.process(divide);
		assertEquals(table, sourceTable.getTable());

		divide = Divide.builder().column(2).value(ONE).build();
		computeProcessor.process(divide);
		assertEquals(table, sourceTable.getTable());

		divide = Divide.builder().column(2).value(DOLLAR_13).build();
		computeProcessor.process(divide);
		assertEquals(table, sourceTable.getTable());

		divide = Divide.builder().column(2).value(ZERO).build();
		computeProcessor.process(divide);
		assertEquals(table, sourceTable.getTable());

		final List<List<String>> result1 = Arrays.asList(
			Arrays.asList(ID1, "250.0", TWO, VALUE_VAL1),
			Arrays.asList(ID2, "300.0", FIVE, VALUE_VAL2),
			Arrays.asList(ID1, "100.0", TWO, VALUE_VAL3)
		);

		Divide valueColumn = Divide.builder().column(2).value(DOLLAR_3).build();
		computeProcessor.process(valueColumn);
		assertEquals(result1, sourceTable.getTable());

		valueColumn = Divide.builder().column(2).value(DOLLAR_1).build();
		computeProcessor.process(valueColumn);
		assertEquals(result1, sourceTable.getTable());

		valueColumn = Divide.builder().column(2).value(ID1).build();
		computeProcessor.process(valueColumn);
		assertEquals(result1, sourceTable.getTable());

		final List<List<String>> result2 = Arrays.asList(
			Arrays.asList(ID1, "25.0", TWO, VALUE_VAL1),
			Arrays.asList(ID2, "30.0", FIVE, VALUE_VAL2),
			Arrays.asList(ID1, "10.0", TWO, VALUE_VAL3)
		);

		Divide valueValue = Divide.builder().column(2).value(TEN).build();
		computeProcessor.process(valueValue);
		assertEquals(result2, sourceTable.getTable());

		valueValue = Divide.builder().column(2).value(ZERO).build();
		computeProcessor.process(valueValue);
		assertEquals(result2, sourceTable.getTable());
	}

	@Test
	void testProcessMultiply() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList(ID1, FIVE_HUNDRED, TWO, VALUE_VAL1),
			Arrays.asList(ID2, ONE_THOUSAND_FIVE_HUNDRED, FIVE, VALUE_VAL2),
			Arrays.asList(ID1, TWO_HUNDRED, TWO, VALUE_VAL3)
		);

		sourceTable.setTable(table);

		computeProcessor.process((Multiply) null);
		assertEquals(table, sourceTable.getTable());

		Multiply multiply = Multiply.builder().column(-1).value(EMPTY).build();
		computeProcessor.process(multiply);
		assertEquals(table, sourceTable.getTable());

		multiply = Multiply.builder().column(2).value(ONE).build();
		computeProcessor.process(multiply);
		assertEquals(table, sourceTable.getTable());

		multiply = Multiply.builder().column(2).value(DOLLAR_13).build();
		computeProcessor.process(multiply);
		assertEquals(table, sourceTable.getTable());

		final List<List<String>> result = Arrays.asList(
			Arrays.asList(ID1, "1000.0", TWO, VALUE_VAL1),
			Arrays.asList(ID2, "7500.0", FIVE, VALUE_VAL2),
			Arrays.asList(ID1, "400.0", TWO, VALUE_VAL3)
		);

		Multiply multiByColumn = Multiply.builder().column(2).value(DOLLAR_3).build();
		computeProcessor.process(multiByColumn);
		assertEquals(result, sourceTable.getTable());

		multiByColumn = Multiply.builder().column(2).value(DOLLAR_1).build();
		computeProcessor.process(multiByColumn);
		assertEquals(result, sourceTable.getTable());

		multiByColumn = Multiply.builder().column(2).value(ID1).build();
		computeProcessor.process(multiByColumn);
		assertEquals(result, sourceTable.getTable());

		Multiply valueValue = Multiply.builder().column(2).value(TEN).build();
		computeProcessor.process(valueValue);
		assertEquals(
			Arrays.asList(
				Arrays.asList(ID1, "10000.0", TWO, VALUE_VAL1),
				Arrays.asList(ID2, "75000.0", FIVE, VALUE_VAL2),
				Arrays.asList(ID1, "4000.0", TWO, VALUE_VAL3)
			),
			sourceTable.getTable()
		);

		valueValue = Multiply.builder().column(2).value(ZERO).build();
		computeProcessor.process(valueValue);
		assertEquals(
			Arrays.asList(
				Arrays.asList(ID1, ZERO_POINT_ZERO, TWO, VALUE_VAL1),
				Arrays.asList(ID2, ZERO_POINT_ZERO, FIVE, VALUE_VAL2),
				Arrays.asList(ID1, ZERO_POINT_ZERO, TWO, VALUE_VAL3)
			),
			sourceTable.getTable()
		);
	}

	@Test
	void testProcessSubtract() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList(ID1, FIVE_HUNDRED, TWO, VALUE_VAL1),
			Arrays.asList(ID2, ONE_THOUSAND_FIVE_HUNDRED, FIVE, VALUE_VAL2),
			Arrays.asList(ID1, TWO_HUNDRED, TWO, VALUE_VAL3)
		);

		sourceTable.setTable(table);

		computeProcessor.process((Subtract) null);
		assertEquals(table, sourceTable.getTable());

		Subtract substract = Subtract.builder().column(-1).value(EMPTY).build();
		computeProcessor.process(substract);
		assertEquals(table, sourceTable.getTable());

		substract = Subtract.builder().column(1).value(DOLLAR_13).build();
		computeProcessor.process(substract);
		assertEquals(table, sourceTable.getTable());

		substract = Subtract.builder().column(1).value(ZERO).build();
		computeProcessor.process(substract);
		assertEquals(table, sourceTable.getTable());

		final List<List<String>> result = Arrays.asList(
			Arrays.asList(ID1, "498.0", TWO, VALUE_VAL1),
			Arrays.asList(ID2, "1495.0", FIVE, VALUE_VAL2),
			Arrays.asList(ID1, "198.0", TWO, VALUE_VAL3)
		);

		Subtract substractColumn = Subtract.builder().column(2).value(DOLLAR_3).build();
		computeProcessor.process(substractColumn);
		assertEquals(result, sourceTable.getTable());

		substractColumn = Subtract.builder().column(2).value(DOLLAR_1).build();
		computeProcessor.process(substractColumn);
		assertEquals(result, sourceTable.getTable());

		substractColumn = Subtract.builder().column(2).value(ID1).build();
		computeProcessor.process(substractColumn);
		assertEquals(result, sourceTable.getTable());

		final Subtract substractValue = Subtract.builder().column(2).value(TEN).build();
		computeProcessor.process(substractValue);
		assertEquals(
			Arrays.asList(
				Arrays.asList(ID1, "488.0", TWO, VALUE_VAL1),
				Arrays.asList(ID2, "1485.0", FIVE, VALUE_VAL2),
				Arrays.asList(ID1, "188.0", TWO, VALUE_VAL3)
			),
			sourceTable.getTable()
		);
	}

	@Test
	void testPerformMathComputeOnLine() {
		final List<List<String>> table = Collections.singletonList(Arrays.asList(SINGLE_SPACE, FOO, FOUR_POINT_ZERO));
		sourceTable.setTable(table);

		// column index > row size
		final Divide divide = Divide.builder().value(TWO).column(4).build();
		computeProcessor.process(divide);
		assertEquals(table, sourceTable.getTable());

		// column index <= row size, op1 is blank
		divide.setColumn(1);
		computeProcessor.process(divide);
		assertEquals(table, sourceTable.getTable());

		// column index <= row size, op1 is not blank, op2Index == -1
		table.get(0).set(0, "8.0");
		List<List<String>> result = Collections.singletonList(Arrays.asList(FOUR_POINT_ZERO, FOO, FOUR_POINT_ZERO));
		computeProcessor.process(divide);
		assertEquals(result, sourceTable.getTable());

		// column index <= row size, op1 is not blank, op2Index != -1, op2Index >= row size
		divide.setValue(DOLLAR_4);
		computeProcessor.process(divide);
		assertEquals(result, sourceTable.getTable());

		// column index <= row size, op1 is not blank, op2Index != -1, op2Index < row size
		divide.setValue(DOLLAR_3);
		result = Collections.singletonList(Arrays.asList("1.0", FOO, FOUR_POINT_ZERO));
		computeProcessor.process(divide);
		assertEquals(result, sourceTable.getTable());
	}

	@Test
	void testProcessAnd() {
		List<List<String>> table = Arrays.asList(
			Arrays.asList(ID1, NAME1, MANUFACTURER1, ONE), // 0000 0001
			Arrays.asList(ID2, NAME2, MANUFACTURER2, FOURTEEN), // 0000 1110
			Arrays.asList(ID3, NAME3, MANUFACTURER3, TWO_HUNDRED_AND_FIFTY_FIVE)
		); // 1111 1110

		List<List<String>> tableResult = Arrays.asList(
			Arrays.asList(ID1, NAME1, MANUFACTURER1, ONE),
			Arrays.asList(ID2, NAME2, MANUFACTURER2, FOURTEEN),
			Arrays.asList(ID3, NAME3, MANUFACTURER3, TWO_HUNDRED_AND_FIFTY_FIVE)
		);

		sourceTable.setTable(table);

		// test null source to process
		computeProcessor.process((And) null);
		assertEquals(tableResult, sourceTable.getTable());

		// test TranslationTable is null
		final And and = And.builder().column(0).value(ONE).build(); // and : 0000 0001
		computeProcessor.process(and);
		assertEquals(tableResult, sourceTable.getTable());

		// test column value is not an integer
		and.setColumn(3);
		computeProcessor.process(and);
		assertEquals(tableResult, sourceTable.getTable());

		// tests OK
		and.setColumn(4);
		tableResult =
			Arrays.asList(
				Arrays.asList(ID1, NAME1, MANUFACTURER1, ONE),
				Arrays.asList(ID2, NAME2, MANUFACTURER2, ZERO),
				Arrays.asList(ID3, NAME3, MANUFACTURER3, ONE)
			);
		computeProcessor.process(and);
		assertEquals(tableResult, sourceTable.getTable());

		table =
			Arrays.asList(
				Arrays.asList(ID1, NAME1, MANUFACTURER1, ONE), // 0000 0001
				Arrays.asList(ID2, NAME2, MANUFACTURER2, FOURTEEN), // 0000 1110
				Arrays.asList(ID3, NAME3, MANUFACTURER3, TWO_HUNDRED_AND_FIFTY_FIVE)
			); // 1111 1110
		and.setValue(THIRTY); // and:0001 1110

		sourceTable.setTable(table);

		tableResult =
			Arrays.asList(
				Arrays.asList(ID1, NAME1, MANUFACTURER1, ZERO), // 0000 0001
				Arrays.asList(ID2, NAME2, MANUFACTURER2, FOURTEEN), // 0000 1110
				Arrays.asList(ID3, NAME3, MANUFACTURER3, THIRTY)
			); // 0001 1110
		computeProcessor.process(and);
		assertEquals(tableResult, sourceTable.getTable());

		// test with column
		table =
			Arrays.asList(
				Arrays.asList(ID1, NAME1, TWO_HUNDRED_AND_FIFTY_FOUR, ONE), // 1111 1110 & 0000 0001
				Arrays.asList(ID2, NAME2, THIRTY_SIX, FOURTEEN), // 0010 0100 & 0000 1110
				Arrays.asList(ID3, NAME3, FORTY_ONE, TWO_HUNDRED_AND_FIFTY_FIVE)
			); // 0010 1001 & 1111 1111
		and.setValue(DOLLAR_3);

		sourceTable.setTable(table);

		tableResult =
			Arrays.asList(
				Arrays.asList(ID1, NAME1, TWO_HUNDRED_AND_FIFTY_FOUR, ZERO), // 0000 0000
				Arrays.asList(ID2, NAME2, THIRTY_SIX, "4"), // 0000 0100
				Arrays.asList(ID3, NAME3, FORTY_ONE, FORTY_ONE)
			); // 0010 1001
		computeProcessor.process(and);
		assertEquals(tableResult, sourceTable.getTable());
	}

	@Test
	void testProcessPrepend() {
		initializeSourceTable();

		// Test with empty Prepend
		final Prepend prepend = new Prepend();

		computeProcessor.process(prepend);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(LINE_1, table.get(0));
		assertEquals(LINE_2, table.get(1));
		assertEquals(LINE_3, table.get(2));

		// Test with Prepend without Value
		prepend.setColumn(3);

		computeProcessor.process(prepend);

		assertEquals(LINE_1, table.get(0));
		assertEquals(LINE_2, table.get(1));
		assertEquals(LINE_3, table.get(2));

		// Test with correct Prepend
		prepend.setValue(PREFIX);

		computeProcessor.process(prepend);

		assertEquals(LINE_1_RESULT_LEFT, table.get(0));
		assertEquals(LINE_2_RESULT_LEFT, table.get(1));
		assertEquals(LINE_3_RESULT_LEFT, table.get(2));

		// empty lines in table => add new column
		sourceTable.getTable().clear();
		sourceTable.getTable().add(new ArrayList<>());
		sourceTable.getTable().add(new ArrayList<>());
		sourceTable.getTable().add(new ArrayList<>());
		prepend.setColumn(1);
		prepend.setValue(FOO);
		computeProcessor.process(prepend);
		assertEquals(new ArrayList<>(Arrays.asList(FOO)), table.get(0));
		assertEquals(new ArrayList<>(Arrays.asList(FOO)), table.get(1));
		assertEquals(new ArrayList<>(Arrays.asList(FOO)), table.get(2));
	}

	@Test
	void testProcessPrependOneColumn() {
		sourceTable.getTable().add(new ArrayList<>(LINE_1_ONE_COLUMN));
		sourceTable.getTable().add(new ArrayList<>(LINE_2_ONE_COLUMN));
		sourceTable.getTable().add(new ArrayList<>(LINE_3_ONE_COLUMN));

		final Prepend prepend = Prepend.builder().column(1).value(PREFIX).build();

		computeProcessor.process(prepend);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(new ArrayList<>(Collections.singletonList(PREFIX_ID1)), table.get(0));
		assertEquals(new ArrayList<>(Collections.singletonList(PREFIX_ID2)), table.get(1));
		assertEquals(new ArrayList<>(Collections.singletonList(PREFIX_ID3)), table.get(2));
	}

	@Test
	void testProcessPrependColumn() {
		initializeSourceTable();

		final Prepend prepend = Prepend.builder().column(3).value(DOLLAR_1).build();

		computeProcessor.process(prepend);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(new ArrayList<>(Arrays.asList(ID1, NAME1, "ID1MANUFACTURER1", NUMBER_OF_DISKS1)), table.get(0));
		assertEquals(new ArrayList<>(Arrays.asList(ID2, NAME2, "ID2MANUFACTURER2", NUMBER_OF_DISKS2)), table.get(1));
		assertEquals(new ArrayList<>(Arrays.asList(ID3, NAME3, "ID3MANUFACTURER3", NUMBER_OF_DISKS3)), table.get(2));
	}

	@Test
	void testProcessPrependNotColumn1() {
		initializeSourceTable();

		final Prepend prepend = Prepend.builder().column(3).value("$1_").build();

		computeProcessor.process(prepend);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(new ArrayList<>(Arrays.asList(ID1, NAME1, "ID1_MANUFACTURER1", NUMBER_OF_DISKS1)), table.get(0));
		assertEquals(new ArrayList<>(Arrays.asList(ID2, NAME2, "ID2_MANUFACTURER2", NUMBER_OF_DISKS2)), table.get(1));
		assertEquals(new ArrayList<>(Arrays.asList(ID3, NAME3, "ID3_MANUFACTURER3", NUMBER_OF_DISKS3)), table.get(2));
	}

	@Test
	void testProcessPrependNotColumn2() {
		initializeSourceTable();

		final Prepend prepend = Prepend.builder().column(3).value(UNDERSCORE_DOLLAR_1).build();

		computeProcessor.process(prepend);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(new ArrayList<>(Arrays.asList(ID1, NAME1, "_ID1MANUFACTURER1", NUMBER_OF_DISKS1)), table.get(0));
		assertEquals(new ArrayList<>(Arrays.asList(ID2, NAME2, "_ID2MANUFACTURER2", NUMBER_OF_DISKS2)), table.get(1));
		assertEquals(new ArrayList<>(Arrays.asList(ID3, NAME3, "_ID3MANUFACTURER3", NUMBER_OF_DISKS3)), table.get(2));
	}

	@Test
	void testProcessPrependNewColumn() {
		initializeSourceTable();

		final Prepend prepend = Prepend.builder().column(3).value("new,Column;prefix_").build();

		computeProcessor.process(prepend);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(
			new ArrayList<>(Arrays.asList(ID1, NAME1, NEW_COMMA_COLUMN, PREFIX_MANUFACTURER1, NUMBER_OF_DISKS1)),
			table.get(0)
		);
		assertEquals(
			new ArrayList<>(Arrays.asList(ID2, NAME2, NEW_COMMA_COLUMN, PREFIX_MANUFACTURER2, NUMBER_OF_DISKS2)),
			table.get(1)
		);
		assertEquals(
			new ArrayList<>(Arrays.asList(ID3, NAME3, NEW_COMMA_COLUMN, PREFIX_MANUFACTURER3, NUMBER_OF_DISKS3)),
			table.get(2)
		);
	}

	@Test
	void testProcessPrependTwoNewColumns() {
		initializeSourceTable();

		final Prepend prepend = Prepend.builder().column(1).value("new,$4;AnotherNew.Column;prefix_").build();

		computeProcessor.process(prepend);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(
			new ArrayList<>(
				Arrays.asList(
					NEW_COMMA_NUMBER_OF_DISKS1,
					ANOTHER_NEW_COLUMN,
					PREFIX_ID1,
					NAME1,
					MANUFACTURER1,
					NUMBER_OF_DISKS1
				)
			),
			table.get(0)
		);
		assertEquals(
			new ArrayList<>(
				Arrays.asList(
					NEW_COMMA_NUMBER_OF_DISKS2,
					ANOTHER_NEW_COLUMN,
					PREFIX_ID2,
					NAME2,
					MANUFACTURER2,
					NUMBER_OF_DISKS2
				)
			),
			table.get(1)
		);
		assertEquals(
			new ArrayList<>(
				Arrays.asList(
					NEW_COMMA_NUMBER_OF_DISKS3,
					ANOTHER_NEW_COLUMN,
					PREFIX_ID3,
					NAME3,
					MANUFACTURER3,
					NUMBER_OF_DISKS3
				)
			),
			table.get(2)
		);
	}

	@Test
	void testProcessPrependColumnIndexOutOfBand() {
		final List<List<String>> table = sourceTable.getTable();
		table.clear();
		table.add(new ArrayList<>(LINE_1));
		table.add(new ArrayList<>(LINE_3_ONE_COLUMN));
		final Prepend prepend = Prepend.builder().column(2).value(PREFIX).type(Prepend.class.getSimpleName()).build();

		computeProcessor.process(prepend);

		assertEquals(PREFIX + NAME1, table.get(0).get(1));
		assertEquals(1, table.get(1).size());
		assertEquals(LINE_3_ONE_COLUMN, table.get(1));
	}

	@Test
	void testProcessPrependColumnValueOutOfBand() {
		{
			final List<List<String>> table = sourceTable.getTable();
			table.clear();
			table.add(new ArrayList<>(LINE_1));
			table.add(new ArrayList<>(LINE_3_ONE_COLUMN));
			final Prepend prepend = Prepend.builder().column(1).value("$4").type(Prepend.class.getSimpleName()).build();

			computeProcessor.process(prepend);

			assertEquals(NUMBER_OF_DISKS1 + ID1, table.get(0).get(0));
			assertEquals(1, table.get(1).size());
			assertEquals(LINE_3_ONE_COLUMN, table.get(1));
		}

		{
			final List<List<String>> table = sourceTable.getTable();
			table.clear();
			table.add(new ArrayList<>(LINE_1));
			table.add(new ArrayList<>(LINE_3_ONE_COLUMN));
			final Prepend prepend = Prepend.builder().column(2).value("$1").type(Prepend.class.getSimpleName()).build();

			computeProcessor.process(prepend);

			assertEquals(ID1 + NAME1, table.get(0).get(1));
			assertEquals(1, table.get(1).size());
			assertEquals(LINE_3_ONE_COLUMN, table.get(1));
		}
	}

	@Test
	void testProcessPrependColumnReferenceInString() {
		initializeSourceTable();

		final Prepend prepend = new Prepend();

		final List<List<String>> table = sourceTable.getTable();

		prepend.setColumn(3);
		prepend.setValue(String.format("%s%s%s%s%s", PREFIX, "$1", MIDDLE_VALUE, "$2", SUFFIX));

		computeProcessor.process(prepend);

		assertEquals(LINE_1_RESULT_PREPEND_REF, table.get(0));
		assertEquals(LINE_2_RESULT_PREPEND_REF, table.get(1));
		assertEquals(LINE_3_RESULT_PREPEND_REF, table.get(2));

		// Check with a protected '$'
		initializeSourceTable();
		prepend.setColumn(3);
		prepend.setValue(String.format("%s%s%s%s%s", PREFIX, "$$1", MIDDLE_VALUE, "$2", SUFFIX));

		computeProcessor.process(prepend);

		assertEquals(LINE_1_RESULT_PREPEND_REF_PROTECTED, table.get(0));
		assertEquals(LINE_2_RESULT_PREPEND_REF_PROTECTED, table.get(1));
		assertEquals(LINE_3_RESULT_PREPEND_REF_PROTECTED, table.get(2));
	}

	@Test
	void testProcessAppend() {
		initializeSourceTable();

		// Test with empty Append
		final Append append = new Append();

		computeProcessor.process(append);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(LINE_1, table.get(0));
		assertEquals(LINE_2, table.get(1));
		assertEquals(LINE_3, table.get(2));

		// Test with Append without Value
		append.setColumn(3);

		computeProcessor.process(append);

		assertEquals(LINE_1, table.get(0));
		assertEquals(LINE_2, table.get(1));
		assertEquals(LINE_3, table.get(2));

		// Test with correct Append
		append.setValue(SUFFIX);

		computeProcessor.process(append);

		assertEquals(LINE_1_RESULT_RIGHT, table.get(0));
		assertEquals(LINE_2_RESULT_RIGHT, table.get(1));
		assertEquals(LINE_3_RESULT_RIGHT, table.get(2));
		// index = size + 1 => add new column
		append.setColumn(5);
		append.setValue(FOO);
		computeProcessor.process(append);
		final List<List<String>> expected = Arrays.asList(LINE_1_RESULT_RIGHT, LINE_2_RESULT_RIGHT, LINE_3_RESULT_RIGHT);
		expected.get(0).add(FOO);
		expected.get(1).add(FOO);
		expected.get(2).add(FOO);
		assertEquals(expected, table);

		// index > size + 1  => out of bounds nothing changed
		append.setColumn(15);
		append.setValue(FOO);
		computeProcessor.process(append);
		assertEquals(expected, table);
	}

	@Test
	void testProcessAppendOneColumn() {
		sourceTable.getTable().add(new ArrayList<>(LINE_1_ONE_COLUMN));
		sourceTable.getTable().add(new ArrayList<>(LINE_2_ONE_COLUMN));
		sourceTable.getTable().add(new ArrayList<>(LINE_3_ONE_COLUMN));

		final Append append = Append.builder().column(1).value(SUFFIX).build();

		computeProcessor.process(append);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(new ArrayList<>(Collections.singletonList(ID1_SUFFIX)), table.get(0));
		assertEquals(new ArrayList<>(Collections.singletonList(ID2_SUFFIX)), table.get(1));
		assertEquals(new ArrayList<>(Collections.singletonList(ID3_SUFFIX)), table.get(2));
	}

	@Test
	void testProcessAppendColumn() {
		initializeSourceTable();

		final Append append = Append.builder().column(3).value(DOLLAR_1).build();

		computeProcessor.process(append);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(new ArrayList<>(Arrays.asList(ID1, NAME1, "MANUFACTURER1ID1", NUMBER_OF_DISKS1)), table.get(0));
		assertEquals(new ArrayList<>(Arrays.asList(ID2, NAME2, "MANUFACTURER2ID2", NUMBER_OF_DISKS2)), table.get(1));
		assertEquals(new ArrayList<>(Arrays.asList(ID3, NAME3, "MANUFACTURER3ID3", NUMBER_OF_DISKS3)), table.get(2));
	}

	@Test
	void testProcessAppendNotColumn1() {
		initializeSourceTable();

		final Append append = Append.builder().column(3).value(UNDERSCORE_DOLLAR_1).build();

		computeProcessor.process(append);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(new ArrayList<>(Arrays.asList(ID1, NAME1, "MANUFACTURER1_ID1", NUMBER_OF_DISKS1)), table.get(0));
		assertEquals(new ArrayList<>(Arrays.asList(ID2, NAME2, "MANUFACTURER2_ID2", NUMBER_OF_DISKS2)), table.get(1));
		assertEquals(new ArrayList<>(Arrays.asList(ID3, NAME3, "MANUFACTURER3_ID3", NUMBER_OF_DISKS3)), table.get(2));
	}

	@Test
	void testProcessAppendNewColumn() {
		initializeSourceTable();

		final Append append = Append.builder().column(3).value("_suffix;new,Column").build();

		computeProcessor.process(append);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(
			new ArrayList<>(Arrays.asList(ID1, NAME1, MANUFACTURER1_SUFFIX, NEW_COMMA_COLUMN, NUMBER_OF_DISKS1)),
			table.get(0)
		);
		assertEquals(
			new ArrayList<>(Arrays.asList(ID2, NAME2, MANUFACTURER2_SUFFIX, NEW_COMMA_COLUMN, NUMBER_OF_DISKS2)),
			table.get(1)
		);
		assertEquals(
			new ArrayList<>(Arrays.asList(ID3, NAME3, MANUFACTURER3_SUFFIX, NEW_COMMA_COLUMN, NUMBER_OF_DISKS3)),
			table.get(2)
		);
	}

	@Test
	void testProcessAppendTwoNewColumns() {
		initializeSourceTable();

		final Append append = Append.builder().column(1).value("_suffix;new,$4;AnotherNew.Column").build();

		computeProcessor.process(append);

		final List<List<String>> table = sourceTable.getTable();

		assertEquals(
			new ArrayList<>(
				Arrays.asList(
					ID1_SUFFIX,
					NEW_COMMA_NUMBER_OF_DISKS1,
					ANOTHER_NEW_COLUMN,
					NAME1,
					MANUFACTURER1,
					NUMBER_OF_DISKS1
				)
			),
			table.get(0)
		);
		assertEquals(
			new ArrayList<>(
				Arrays.asList(
					ID2_SUFFIX,
					NEW_COMMA_NUMBER_OF_DISKS2,
					ANOTHER_NEW_COLUMN,
					NAME2,
					MANUFACTURER2,
					NUMBER_OF_DISKS2
				)
			),
			table.get(1)
		);
		assertEquals(
			new ArrayList<>(
				Arrays.asList(
					ID3_SUFFIX,
					NEW_COMMA_NUMBER_OF_DISKS3,
					ANOTHER_NEW_COLUMN,
					NAME3,
					MANUFACTURER3,
					NUMBER_OF_DISKS3
				)
			),
			table.get(2)
		);
	}

	@Test
	void testProcessAppendNoOperation() {
		initializeSourceTable();

		// Append is null
		computeProcessor.setSourceTable(SourceTable.empty());
		computeProcessor.process((Append) null);
		assertNotNull(computeProcessor.getSourceTable().getTable());
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// Append is not null, Append.getColumn() is not null, Append.getValue() is not null,
		// Append.getColumn() <= 0
		final Append append = Append.builder().value(SUFFIX).column(0).build();
		computeProcessor.process(append);
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// Append is not null, Append.getColumn() is not null, Append.getValue() is not null,
		// Append.getColumn() > 0,
		// computeProcessor.getSourceTable() is null
		append.setColumn(1);
		computeProcessor.setSourceTable(null);
		computeProcessor.process(append);
		assertNull(computeProcessor.getSourceTable());

		// Append is not null, Append.getColumn() is not null, Append.getValue() is not null,
		// Append.getColumn() > 0,
		// computeProcessor.getSourceTable() is not null, computeProcessor.getSourceTable().getTable() is null
		computeProcessor.setSourceTable(SourceTable.builder().table(null).build());
		computeProcessor.process(append);
		assertNull(computeProcessor.getSourceTable().getTable());

		// Append is not null, Append.getColumn() is not null, Append.getValue() is not null,
		// Append.getColumn() > 0,
		// computeProcessor.getSourceTable() is not null, computeProcessor.getSourceTable().getTable() is not null,
		// computeProcessor.getSourceTable().getTable().isEmpty()
		computeProcessor.setSourceTable(SourceTable.empty());
		computeProcessor.process(append);
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// Append is not null, Append.getColumn() is not null, Append.getValue() is not null,
		// Append.getColumn() > 0,
		// computeProcessor.getSourceTable() is not null, computeProcessor.getSourceTable().getTable() is not null,
		// computeProcessor.getSourceTable().getTable() is not empty,
		// Append.getColumn() > sourceTable.getTable().get(0).size()
		computeProcessor.setSourceTable(
			SourceTable.builder().table(Collections.singletonList(Collections.singletonList(FOO))).build()
		);
		append.setColumn(5);
		computeProcessor.process(append);
		assertEquals(1, computeProcessor.getSourceTable().getTable().size());

		// Append is not null, Append.getColumn() is not null, Append.getValue() is not null,
		// Append.getColumn() > 0,
		// computeProcessor.getSourceTable() is not null, computeProcessor.getSourceTable().getTable() is not null,
		// computeProcessor.getSourceTable().getTable() is not empty,
		// Append.getColumn() <= sourceTable.getTable().get(0).size(),
		// matcher.matches, concatColumnIndex < sourceTable.getTable().get(0).size()
		append.setColumn(1);
		append.setValue("$2");
		computeProcessor.process(append);
		assertEquals(1, computeProcessor.getSourceTable().getTable().size());
	}

	@Test
	void testProcessAppendColumnIndexOutOfBand() {
		final List<List<String>> table = sourceTable.getTable();
		table.clear();
		table.add(new ArrayList<>(LINE_1));
		table.add(new ArrayList<>(LINE_3_ONE_COLUMN));
		final Append append = Append.builder().column(2).value(SUFFIX).type(Append.class.getSimpleName()).build();

		computeProcessor.process(append);

		assertEquals(NAME1 + SUFFIX, table.get(0).get(1));
		assertEquals(1, table.get(1).size());
		assertEquals(LINE_3_ONE_COLUMN, table.get(1));
	}

	@Test
	void testProcessAppendColumnValueOutOfBand() {
		{
			final List<List<String>> table = sourceTable.getTable();
			table.clear();
			table.add(new ArrayList<>(LINE_1));
			table.add(new ArrayList<>(LINE_3_ONE_COLUMN));
			final Append append = Append.builder().column(1).value("$4").type(Append.class.getSimpleName()).build();

			computeProcessor.process(append);

			assertEquals(ID1 + NUMBER_OF_DISKS1, table.get(0).get(0));
			assertEquals(1, table.get(1).size());
			assertEquals(LINE_3_ONE_COLUMN, table.get(1));
		}
		{
			final List<List<String>> table = sourceTable.getTable();
			table.clear();
			table.add(new ArrayList<>(LINE_1));
			table.add(new ArrayList<>(LINE_3_ONE_COLUMN));
			final Append append = Append.builder().column(2).value("$1").type(Append.class.getSimpleName()).build();

			computeProcessor.process(append);

			assertEquals(NAME1 + ID1, table.get(0).get(1));
			assertEquals(1, table.get(1).size());
			assertEquals(LINE_3_ONE_COLUMN, table.get(1));
		}
	}

	@Test
	void testProcessAppendColumnReferenceInString() {
		initializeSourceTable();

		final Append append = new Append();

		final List<List<String>> table = sourceTable.getTable();

		append.setColumn(3);
		append.setValue(String.format("%s%s%s%s%s", PREFIX, "$1", MIDDLE_VALUE, "$2", SUFFIX));

		computeProcessor.process(append);

		assertEquals(LINE_1_RESULT_APPEND_REF, table.get(0));
		assertEquals(LINE_2_RESULT_APPEND_REF, table.get(1));
		assertEquals(LINE_3_RESULT_APPEND_REF, table.get(2));

		// Check with a protected '$'
		initializeSourceTable();
		append.setColumn(3);
		append.setValue(String.format("%s%s%s%s%s", PREFIX, "$$1", MIDDLE_VALUE, "$2", SUFFIX));

		computeProcessor.process(append);

		assertEquals(LINE_1_RESULT_APPEND_REF_PROTECTED, table.get(0));
		assertEquals(LINE_2_RESULT_APPEND_REF_PROTECTED, table.get(1));
		assertEquals(LINE_3_RESULT_APPEND_REF_PROTECTED, table.get(2));
	}

	@Test
	void testJson2Csv() {
		// Retrieve the Json file and extract its content as String
		final String rawData = ResourceHelper
			.getResourceAsString("/test-files/compute/json2Csv/json2CsvSample.json", ComputeProcessor.class)
			.replaceAll("\\s", "");

		// Set the extracted rawData in the source table
		sourceTable.setRawData(rawData);

		// Check the case of a null {@link Json2Csv} instance
		Json2Csv jsonToCsv = null;
		computeProcessor.process(jsonToCsv);
		assertEquals(rawData, sourceTable.getRawData());

		// Check the case of an empty {@link Json2Csv} instance
		jsonToCsv = Json2Csv.builder().build();
		computeProcessor.process(jsonToCsv);
		assertEquals(rawData, sourceTable.getRawData());

		// Check the case of a non-null {@link Json2Csv} instance
		jsonToCsv =
			Json2Csv.builder().entryKey("/monitors").separator(";").properties("id;name;monitorType;hostId").build();

		computeProcessor.process(jsonToCsv);

		final String expectedRawDataResult =
			"/monitors[0];enclosure-1;enclosure-1;ENCLOSURE;hostId;\n" +
			"/monitors[1];enclosure-2;enclosure-2;ENCLOSURE;hostId;\n";
		assertEquals(expectedRawDataResult, sourceTable.getRawData());
	}

	@Test
	void visitSubstringNOK() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "Dell+1"),
			Arrays.asList("ID2", "Dell+33"),
			Arrays.asList("ID3", "Dell+xyz")
		);

		sourceTable.setTable(table);
		computeProcessor.process((Substring) null);
		assertEquals(table, sourceTable.getTable());

		computeProcessor.process(Substring.builder().column(-1).start("-1").length("-1").build());
		assertEquals(table, sourceTable.getTable());

		computeProcessor.process(Substring.builder().column(2).start("TOTO").length("4").build());
		assertEquals(table, sourceTable.getTable());

		computeProcessor.process(Substring.builder().column(2).start("1").length("TOTO").build());
		assertEquals(table, sourceTable.getTable());
	}

	@Test
	void testCheckSubstring() {
		assertTrue(ComputeProcessor.checkSubstring(Substring.builder().column(2).start("1").length("4").build()));
		assertFalse(ComputeProcessor.checkSubstring(Substring.builder().column(-2).start("1").length("4").build()));
		assertFalse(ComputeProcessor.checkSubstring(Substring.builder().column(-1).start("1").length("4").build()));
		assertFalse(ComputeProcessor.checkSubstring(Substring.builder().column(-1).start("-1").length("-1").build()));
		assertFalse(ComputeProcessor.checkSubstring(null));
	}

	@Test
	void testProcessSubstring() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "Dell+1"),
			Arrays.asList("ID2", "Dell+33"),
			Arrays.asList("ID3", "Dell+xyz")
		);

		sourceTable.setTable(table);

		final Substring substring = Substring.builder().column(2).start("1").length("4").build();

		computeProcessor.process(substring);

		final List<List<String>> expected = Arrays.asList(
			Arrays.asList("ID1", "Dell"),
			Arrays.asList("ID2", "Dell"),
			Arrays.asList("ID3", "Dell")
		);
		assertEquals(expected, sourceTable.getTable());
	}

	@Test
	void testProcessSubstringViaColumn() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "Dell+1", "4"),
			Arrays.asList("ID2", "Dell+33", "4"),
			Arrays.asList("ID3", "Dell+xyz", "4")
		);

		sourceTable.setTable(table);

		final Substring substring = Substring.builder().column(2).start("1").length("$3").build();

		computeProcessor.process(substring);

		final List<List<String>> expected = Arrays.asList(
			Arrays.asList("ID1", "Dell", "4"),
			Arrays.asList("ID2", "Dell", "4"),
			Arrays.asList("ID3", "Dell", "4")
		);
		assertEquals(expected, sourceTable.getTable());
	}

	@Test
	void testPerformSubstringWrongBeginIndex() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "Dell+1"),
			Arrays.asList("ID2", "Dell+33"),
			Arrays.asList("ID3", "Dell+xyz")
		);

		sourceTable.setTable(table);

		computeProcessor.performSubstring(1, "Column(4)", 3, "4", -1);

		assertEquals(table, sourceTable.getTable());
	}

	@Test
	void testPerformSubstringWrongColumnIndex() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "Dell+1"),
			Arrays.asList("ID2", "Dell+33"),
			Arrays.asList("ID3", "Dell+xyz")
		);

		sourceTable.setTable(table);

		computeProcessor.performSubstring(3, "1", -1, "4", -1);

		assertEquals(table, sourceTable.getTable());
	}

	@Test
	void testPerformSubstring() {
		final List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "Dell+1"),
			Arrays.asList("ID2", "Dell+33"),
			Arrays.asList("ID3", "Dell+xyz")
		);

		sourceTable.setTable(table);

		computeProcessor.performSubstring(1, "1", -1, "4", -1);

		final List<List<String>> expected = Arrays.asList(
			Arrays.asList("ID1", "Dell"),
			Arrays.asList("ID2", "Dell"),
			Arrays.asList("ID3", "Dell")
		);
		assertEquals(expected, sourceTable.getTable());
	}

	@Test
	void testCheckSubstringArguments() {
		assertTrue(ComputeProcessor.checkSubstringArguments(1, 3, 3));

		//noinspection ConstantConditions
		assertFalse(ComputeProcessor.checkSubstringArguments(null, 3, 3));

		//noinspection ConstantConditions
		assertFalse(ComputeProcessor.checkSubstringArguments(1, null, 3));

		assertFalse(ComputeProcessor.checkSubstringArguments(0, 3, 3));
		assertFalse(ComputeProcessor.checkSubstringArguments(2, 0, 3));
		assertFalse(ComputeProcessor.checkSubstringArguments(1, 4, 3));
	}

	@Test
	void testTransformToIntegerValue() {
		assertNull(ComputeProcessor.transformToIntegerValue(null));
		assertNull(ComputeProcessor.transformToIntegerValue("a"));
		assertEquals(1, ComputeProcessor.transformToIntegerValue("1"));
	}

	@Test
	void testGetValueFunction() {
		assertNotNull(ComputeProcessor.getValueFunction(-1));
		assertNotNull(ComputeProcessor.getValueFunction(0));
	}

	@Test
	void testCheckValueAndColumnIndexConsistency() {
		assertTrue(ComputeProcessor.checkValueAndColumnIndexConsistency("1", -1));
		assertFalse(ComputeProcessor.checkValueAndColumnIndexConsistency("Column(0)", -1));
		assertTrue(ComputeProcessor.checkValueAndColumnIndexConsistency("Column(1)", 0));
		assertTrue(ComputeProcessor.checkValueAndColumnIndexConsistency("1", 1));
	}

	@Test
	void testGetColumnIndex() {
		assertEquals(1, ComputeProcessor.getColumnIndex(" $2 "));
		assertEquals(-1, ComputeProcessor.getColumnIndex("2"));
	}

	@Test
	void testProcessArrayTranslate() throws IOException {
		List<List<String>> table = Arrays.asList(
			Arrays.asList(ID1, null, TYPE1),
			Arrays.asList(ID2, null, TYPE2),
			Arrays.asList(ID3, null, TYPE3)
		);

		List<List<String>> result = Arrays.asList(
			Arrays.asList(ID1, "TRANSLATED_STATUS11|TRANSLATED_STATUS12|TRANSLATED_STATUS13", TYPE1),
			Arrays.asList(ID2, "NO_VALUE|TRANSLATED_STATUS22", TYPE2),
			Arrays.asList(ID3, "TRANSLATED_STATUS31", TYPE3)
		);

		final Map<String, String> translations = Map.of(
			"",
			"NO_VALUE",
			"status11",
			"TRANSLATED_STATUS11",
			"status12",
			"TRANSLATED_STATUS12",
			"status13",
			"TRANSLATED_STATUS13",
			"status22",
			"TRANSLATED_STATUS22", // No translation for STATUS22
			"status31",
			"TRANSLATED_STATUS31"
		);
		final String translationTableName = "translationTableName";
		final TranslationTable connectorTranslationTable = TranslationTable.builder().translations(translations).build();
		final String connectorId = "connectorId";
		final Connector connector = Connector
			.builder()
			.translations(Collections.singletonMap(translationTableName, connectorTranslationTable))
			.build();

		Map<String, Connector> store = Map.of(connectorId, connector);

		final TelemetryManager telemetryManager = TelemetryManager.builder().connectorStore(connectorStoreMock).build();

		doReturn(store).when(connectorStoreMock).getStore();
		computeProcessor.setConnectorId(connectorId);
		computeProcessor.setTelemetryManager(telemetryManager);

		sourceTable.setTable(table);

		// ArrayTranslate is null
		computeProcessor.process((ArrayTranslate) null);
		assertEquals(table, sourceTable.getTable());

		// ArrayTranslate is not null, translationTable is null
		ArrayTranslate arrayTranslate = new ArrayTranslate();
		computeProcessor.process(arrayTranslate);
		assertEquals(table, sourceTable.getTable());

		// ArrayTranslate is not null, translationTable is not null, translations is not null,
		// column < 1
		ReferenceTranslationTable translationTable = new ReferenceTranslationTable("");
		arrayTranslate.setTranslationTable(translationTable);
		arrayTranslate.setColumn(0);
		computeProcessor.process(arrayTranslate);
		assertEquals(table, sourceTable.getTable());

		// ArrayTranslate is not null, translationTable is not null, translations is not null,
		// column >= 1, arraySeparator is null, resultSeparator is null, columnIndex >= row size
		arrayTranslate.setColumn(4);
		computeProcessor.process(arrayTranslate);
		assertEquals(table, sourceTable.getTable());

		// ArrayTranslate is not null, translationTable is not null, translations is not null,
		// column >= 1, arraySeparator is not null, resultSeparator is not null, columnIndex >= row size
		arrayTranslate.setArraySeparator(",");
		arrayTranslate.setResultSeparator("|");
		computeProcessor.process(arrayTranslate);
		assertEquals(table, sourceTable.getTable());

		// ArrayTranslate is not null, translationTable is not null, translations is not null,
		// column >= 1, arraySeparator is not null, resultSeparator is not null, columnIndex < row size,
		// arrayValue is null
		arrayTranslate.setColumn(2);
		computeProcessor.process(arrayTranslate);
		assertEquals(table, sourceTable.getTable());

		// Test ReferenceTranslationTable OK
		table =
			Arrays.asList(
				Arrays.asList(ID1, "STATUS11,STATUS12,STATUS13", TYPE1),
				Arrays.asList(ID2, ",STATUS22,STATUS23,", TYPE2),
				Arrays.asList(ID3, "STATUS31", TYPE3)
			);

		sourceTable.setTable(table);

		translationTable = new ReferenceTranslationTable("${translation::translationTableName}");
		arrayTranslate.setTranslationTable(translationTable);

		computeProcessor.process(arrayTranslate);
		assertEquals(result, sourceTable.getTable());

		// Test Inline TranslationTable OK
		sourceTable.setTable(
			Arrays.asList(
				Arrays.asList(ID1, "STATUS11,STATUS12,STATUS13", TYPE1),
				Arrays.asList(ID2, "STATUS22,STATUS23,", TYPE2),
				Arrays.asList(ID3, "STATUS31", TYPE3)
			)
		);

		result =
			Arrays.asList(
				Arrays.asList(ID1, "TRANSLATED_STATUS11|TRANSLATED_STATUS12|TRANSLATED_STATUS13", TYPE1),
				Arrays.asList(ID2, "TRANSLATED_STATUS22", TYPE2),
				Arrays.asList(ID3, "TRANSLATED_STATUS31", TYPE3)
			);

		final TranslationTable referenceTranslationTable = TranslationTable
			.builder()
			.translations(
				Map.of(
					"status11",
					"TRANSLATED_STATUS11",
					"status12",
					"TRANSLATED_STATUS12",
					"status13",
					"TRANSLATED_STATUS13",
					"status22",
					"TRANSLATED_STATUS22",
					"status31",
					"TRANSLATED_STATUS31"
				)
			)
			.build();
		arrayTranslate.setTranslationTable(referenceTranslationTable);

		computeProcessor.process(arrayTranslate);
		assertEquals(result, sourceTable.getTable());
	}

	@Test
	void testExcludeMatchingLines() {
		List<List<String>> table = newSourceTable();

		// regexp is null, valueSet is null
		final ExcludeMatchingLines excludeMatchingLines = ExcludeMatchingLines
			.builder()
			.column(1)
			.regExp(null)
			.valueList(null)
			.build();
		computeProcessor.process(excludeMatchingLines);
		assertEquals(table, computeProcessor.getSourceTable().getTable());

		// regexp is empty, valueSet is null
		table = newSourceTable();
		excludeMatchingLines.setRegExp("");
		computeProcessor.process(excludeMatchingLines);
		assertEquals(table, computeProcessor.getSourceTable().getTable());

		// regexp is empty, valueSet is empty
		table = newSourceTable();
		excludeMatchingLines.setValueList(EMPTY);
		computeProcessor.process(excludeMatchingLines);
		assertEquals(table, computeProcessor.getSourceTable().getTable());

		// regex is not null, not empty
		table = newSourceTable();
		excludeMatchingLines.setRegExp("^B.*");
		computeProcessor.process(excludeMatchingLines);
		assertNotEquals(table, computeProcessor.getSourceTable().getTable());
		List<List<String>> resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(1, resultTable.size());
		assertEquals(table.get(0), resultTable.get(0));

		// regex is null,
		// valueSet is not null, not empty
		table = newSourceTable();
		excludeMatchingLines.setRegExp(null);
		excludeMatchingLines.setValueList("3,300");
		excludeMatchingLines.setColumn(4);
		computeProcessor.process(excludeMatchingLines);
		assertNotEquals(table, computeProcessor.getSourceTable().getTable());
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(1, resultTable.size());
		assertEquals(table.get(1), resultTable.get(0));

		// regex is not null, not empty
		// valueSet is not null, not empty
		table = newSourceTable();
		excludeMatchingLines.setColumn(1);
		excludeMatchingLines.setRegExp(".*R.*"); // Applying only the regex would exclude line2
		excludeMatchingLines.setValueList("foo,BAR,BAB"); // Applying only the valueSet would exclude line1 and line2
		computeProcessor.process(excludeMatchingLines);
		assertNotEquals(table, computeProcessor.getSourceTable().getTable());
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(1, resultTable.size()); // Applying both the regex and the valueSet leaves only line3
		assertEquals(table.get(2), resultTable.get(0));
	}

	@Test
	void testExtract() {
		List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "STATUS1", "TYPE1", null, "NAME1"),
			Arrays.asList("ID2", "STATUS2", "TYPE2", null, "NAME2"),
			Arrays.asList("ID3", "STATUS3", "TYPE3", null, "NAME3")
		);

		sourceTable.setTable(table);

		// Extract is null
		computeProcessor.process((Extract) null);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column is not valid, subColumn is not valid
		Extract extract = Extract.builder().column(-1).subColumn(-1).build();
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column < 1
		extract.setColumn(0);
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn is null
		extract.setColumn(1);
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn < 1
		extract.setSubColumn(-1);
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn >= 1, subSeparators is null
		extract.setSubColumn(1);
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn >= 1, subSeparators is empty
		extract.setSubSeparators("");
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn >= 1, subSeparators is not null and not empty,
		// column > row size
		extract.setSubSeparators("|");
		extract.setColumn(6);
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn >= 1, subSeparators is not null and not empty,
		// column is valid, text is null
		extract.setColumn(4);
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn >= 1, subSeparators is not null and not empty,
		// column is valid, text is not null, subColumn < 1
		table.get(0).set(3, "|OK|1");
		table.get(1).set(3, "|OK|2");
		table.get(2).set(3, "|OK|3");
		extract.setSubColumn(0);
		computeProcessor.process(extract);
		assertEquals(table, sourceTable.getTable());

		// Extract is not null, column >= 1, subColumn >= 1, subSeparators is not null and not empty,
		// column is valid, text is not null, subColumn > text.length()
		extract.setSubColumn(4);
		computeProcessor.process(extract);
		List<List<String>> expected = Arrays.asList(
			Arrays.asList("ID1", "STATUS1", "TYPE1", "", "NAME1"),
			Arrays.asList("ID2", "STATUS2", "TYPE2", "", "NAME2"),
			Arrays.asList("ID3", "STATUS3", "TYPE3", "", "NAME3")
		);
		assertEquals(expected, sourceTable.getTable());

		// Test OK, subSeparators is a single character
		List<List<String>> result = Arrays.asList(
			Arrays.asList("ID1", "STATUS1", "TYPE1", "1", "NAME1"),
			Arrays.asList("ID2", "STATUS2", "TYPE2", "2", "NAME2"),
			Arrays.asList("ID3", "STATUS3", "TYPE3", "3", "NAME3")
		);
		extract.setSubColumn(3);
		computeProcessor.process(extract);
		assertEquals(expected, sourceTable.getTable());

		// Test OK, subSeparators is "()"
		table.get(0).set(3, "STATUS1 (1)");
		table.get(1).set(3, "STATUS2 (2)");
		table.get(2).set(3, "STATUS3 (3)");
		sourceTable.setTable(table);
		extract.setSubColumn(2);
		extract.setSubSeparators("()");
		computeProcessor.process(extract);
		assertEquals(result, sourceTable.getTable());

		// Test OK, subSeparators is "%%"
		table.get(0).set(3, "1% of maximum");
		table.get(1).set(3, "2% of maximum");
		table.get(2).set(3, "3% of maximum");
		sourceTable.setTable(table);
		extract.setSubColumn(1);
		extract.setSubSeparators("%%");
		computeProcessor.process(extract);
		assertEquals(result, sourceTable.getTable());
	}

	@Test
	void testReplace() {
		sourceTable.getTable().add(new ArrayList<>(Arrays.asList("ID1", "val1", "1value1")));
		sourceTable.getTable().add(new ArrayList<>(Arrays.asList("ID2", "val2", "1value11")));
		sourceTable.getTable().add(new ArrayList<>(Arrays.asList("ID3", "val3", "va1lue12")));

		// Check the case of a null {@link Replace} object
		computeProcessor.process((Replace) null);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "val1", "1value1"),
				Arrays.asList("ID2", "val2", "1value11"),
				Arrays.asList("ID3", "val3", "va1lue12")
			),
			sourceTable.getTable()
		);

		// // Check the case of an invalid column index
		final Replace replace = Replace.builder().column(-1).build();
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "val1", "1value1"),
				Arrays.asList("ID2", "val2", "1value11"),
				Arrays.asList("ID3", "val3", "va1lue12")
			),
			sourceTable.getTable()
		);

		replace.setColumn(2);
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "val1", "1value1"),
				Arrays.asList("ID2", "val2", "1value11"),
				Arrays.asList("ID3", "val3", "va1lue12")
			),
			sourceTable.getTable()
		);

		replace.setExistingValue("al");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "val1", "1value1"),
				Arrays.asList("ID2", "val2", "1value11"),
				Arrays.asList("ID3", "val3", "va1lue12")
			),
			sourceTable.getTable()
		);

		replace.setExistingValue(null);
		replace.setNewValue("");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "val1", "1value1"),
				Arrays.asList("ID2", "val2", "1value11"),
				Arrays.asList("ID3", "val3", "va1lue12")
			),
			sourceTable.getTable()
		);

		replace.setExistingValue("al");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "v1", "1value1"),
				Arrays.asList("ID2", "v2", "1value11"),
				Arrays.asList("ID3", "v3", "va1lue12")
			),
			sourceTable.getTable()
		);

		replace.setColumn(3);
		replace.setExistingValue("1");
		replace.setNewValue("f");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "v1", "fvaluef"),
				Arrays.asList("ID2", "v2", "fvalueff"),
				Arrays.asList("ID3", "v3", "vafluef2")
			),
			sourceTable.getTable()
		);

		replace.setExistingValue("ue");
		replace.setNewValue("$2");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "v1", "fvalv1f"),
				Arrays.asList("ID2", "v2", "fvalv2ff"),
				Arrays.asList("ID3", "v3", "vaflv3f2")
			),
			sourceTable.getTable()
		);

		// Check the case when both existing and new values match COLUMN_PATTERN regex
		replace.setExistingValue("lv");
		replace.setNewValue("val1;val2");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "v1", "fvaval1", "val21f"),
				Arrays.asList("ID2", "v2", "fvaval1", "val22ff"),
				Arrays.asList("ID3", "v3", "vafval1", "val23f2")
			),
			sourceTable.getTable()
		);

		// Check the case when existing value matches COLUMN_PATTERN regex and the new value is hard-coded
		replace.setExistingValue("$3");
		replace.setNewValue("v1v2");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "v1", "v1v2", "val21f"),
				Arrays.asList("ID2", "v2", "v1v2", "val22ff"),
				Arrays.asList("ID3", "v3", "v1v2", "val23f2")
			),
			sourceTable.getTable()
		);

		// Check the case when both existing value and new value match COLUMN_PATTERN regex
		replace.setExistingValue("$2");
		replace.setNewValue("$1");
		computeProcessor.process(replace);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "v1", "ID1v2", "val21f"),
				Arrays.asList("ID2", "v2", "v1ID2", "val22ff"),
				Arrays.asList("ID3", "v3", "v1v2", "val23f2")
			),
			sourceTable.getTable()
		);
	}

	@Test
	void testXml2Csv() {
		final String xml = ResourceHelper.getResourceAsString("/test-files/compute/xml2Csv/xml2Csv.xml", this.getClass());
		sourceTable.setRawData(xml);

		final String properties =
			">classId;" +
			"outConfigs/equipmentFan>dn;" +
			"outConfigs/equipmentFan>serial;" +
			"outConfigs/equipmentFan>model;" +
			"outConfigs/equipmentFan>vendor;" +
			"outConfigs/equipmentFan>operState";

		final String recordTag = "/configResolveClass";

		computeProcessor.process((Xml2Csv) null);
		assertEquals(Collections.emptyList(), sourceTable.getTable());
		assertEquals(xml, sourceTable.getRawData());

		computeProcessor.process(Xml2Csv.builder().build());
		assertEquals(Collections.emptyList(), sourceTable.getTable());
		assertEquals(xml, sourceTable.getRawData());

		computeProcessor.process(Xml2Csv.builder().recordTag(recordTag).build());
		assertEquals(Collections.emptyList(), sourceTable.getTable());
		assertEquals(xml, sourceTable.getRawData());

		computeProcessor.process(Xml2Csv.builder().properties(properties).recordTag(recordTag).build());

		final List<List<String>> expected = List.of(
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-1/fan-1",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-1/fan-2",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-1/fan-3",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-1/fan-4",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-1/fan-5",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-1/fan-6",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-2/fan-1",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-2/fan-2",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-2/fan-3",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-2/fan-4",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-2/fan-5",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/switch-A/fan-module-1-2/fan-6",
				"N/A",
				"N10-FAN1",
				"Cisco Systems, Inc.",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-1/fan-1",
				"NWG15030613",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-1/fan-2",
				"NWG15030613",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-2/fan-1",
				"NWG150305AQ",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-2/fan-2",
				"NWG150305AQ",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-3/fan-1",
				"NWG15030653",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-3/fan-2",
				"NWG15030653",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-4/fan-1",
				"NWG1503055C",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-4/fan-2",
				"NWG1503055C",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-5/fan-1",
				"NWG150305CM",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-5/fan-2",
				"NWG150305CM",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-6/fan-1",
				"NWG150306ZR",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-6/fan-2",
				"NWG150306ZR",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-7/fan-1",
				"NWG150305QP",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-7/fan-2",
				"NWG150305QP",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-8/fan-1",
				"NWG150306VZ",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			),
			List.of(
				"equipmentFan",
				"sys/chassis-1/fan-module-1-8/fan-2",
				"NWG150306VZ",
				"N20-FAN5",
				"Cisco Systems Inc",
				"operable"
			)
		);

		assertEquals(expected, sourceTable.getTable());
		assertNotNull(sourceTable.getRawData());

		final String expectedCsvResult =
			"equipmentFan;sys/switch-A/fan-module-1-1/fan-1;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-1/fan-2;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-1/fan-3;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-1/fan-4;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-1/fan-5;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-1/fan-6;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-2/fan-1;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-2/fan-2;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-2/fan-3;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-2/fan-4;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-2/fan-5;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/switch-A/fan-module-1-2/fan-6;N/A;N10-FAN1;Cisco Systems, Inc.;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-1/fan-1;NWG15030613;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-1/fan-2;NWG15030613;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-2/fan-1;NWG150305AQ;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-2/fan-2;NWG150305AQ;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-3/fan-1;NWG15030653;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-3/fan-2;NWG15030653;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-4/fan-1;NWG1503055C;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-4/fan-2;NWG1503055C;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-5/fan-1;NWG150305CM;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-5/fan-2;NWG150305CM;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-6/fan-1;NWG150306ZR;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-6/fan-2;NWG150306ZR;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-7/fan-1;NWG150305QP;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-7/fan-2;NWG150305QP;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-8/fan-1;NWG150306VZ;N20-FAN5;Cisco Systems Inc;operable;\n" +
			"equipmentFan;sys/chassis-1/fan-module-1-8/fan-2;NWG150306VZ;N20-FAN5;Cisco Systems Inc;operable;";

		computeProcessor.process(Xml2Csv.builder().properties(properties).build());
		assertEquals(expected, sourceTable.getTable());
		assertEquals(expectedCsvResult, sourceTable.getRawData());
	}

	@Test
	void testKeepOnlyMatchingLinesNoOperation() {
		// KeepOnlyMatchingLines is null
		computeProcessor.setSourceTable(SourceTable.empty());
		computeProcessor.process((KeepOnlyMatchingLines) null);
		assertNotNull(computeProcessor.getSourceTable().getTable());
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// KeepOnlyMatchingLines is not null, keepOnlyMatchingLines.getColumn() is null
		KeepOnlyMatchingLines keepOnlyMatchingLines = KeepOnlyMatchingLines.builder().column(-1).build();
		computeProcessor.process(keepOnlyMatchingLines);
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// KeepOnlyMatchingLines is not null, keepOnlyMatchingLines.getColumn() is not null,
		// keepOnlyMatchingLines.getColumn() <= 0
		keepOnlyMatchingLines.setColumn(0);
		computeProcessor.process(keepOnlyMatchingLines);
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// KeepOnlyMatchingLines is not null, keepOnlyMatchingLines.getColumn() is not null,
		// keepOnlyMatchingLines.getColumn() > 0,
		// computeVisitor.getSourceTable() is null
		keepOnlyMatchingLines.setColumn(1);
		computeProcessor.setSourceTable(null);
		computeProcessor.process(keepOnlyMatchingLines);
		assertNull(computeProcessor.getSourceTable());

		// KeepOnlyMatchingLines is not null, keepOnlyMatchingLines.getColumn() is not null,
		// keepOnlyMatchingLines.getColumn() > 0,
		// computeVisitor.getSourceTable() is not null, computeVisitor.getSourceTable().getTable() is null
		computeProcessor.setSourceTable(SourceTable.builder().table(null).build());
		computeProcessor.process(keepOnlyMatchingLines);
		assertNull(computeProcessor.getSourceTable().getTable());

		// KeepOnlyMatchingLines is not null, keepOnlyMatchingLines.getColumn() is not null,
		// keepOnlyMatchingLines.getColumn() > 0,
		// computeVisitor.getSourceTable() is not null, computeVisitor.getSourceTable().getTable() is not null,
		// computeVisitor.getSourceTable().getTable().isEmpty()
		computeProcessor.setSourceTable(SourceTable.empty());
		computeProcessor.process(keepOnlyMatchingLines);
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// KeepOnlyMatchingLines is not null, keepOnlyMatchingLines.getColumn() is not null,
		// keepOnlyMatchingLines.getColumn() > 0,
		// computeVisitor.getSourceTable() is not null, computeVisitor.getSourceTable().getTable() is not null,
		// computeVisitor.getSourceTable().getTable() is not empty,
		// keepOnlyMatchingLines.getColumn() > sourceTable.getTable().get(0).size()
		computeProcessor.setSourceTable(
			SourceTable.builder().table(Collections.singletonList(Collections.singletonList(FOO))).build()
		);
		keepOnlyMatchingLines.setColumn(2);
		computeProcessor.process(keepOnlyMatchingLines);
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());
	}

	/**
	 * Creates and returns a new source table represented as a list of lists of strings.
	 *
	 * This method constructs a source table with three lines, each containing a combination of predefined values:
	 * <ol>
	 * <li>Line 1: ["FOO", "1", "2", "3"]</li>
	 * <li>Line 2: ["BAR", "10", "20", "30"]</li>
	 * <li>Line 3: ["BAZ", "100", "200", "300"]</li>
	 * </ol>
	 * The source table is then set in the compute processor using {@code SourceTable.builder().table(table).build()}.
	 *
	 * @return A new source table represented as a list of lists of strings.
	 *         Each inner list corresponds to a line in the source table.
	 */
	List<List<String>> newSourceTable() {
		final List<String> line1 = Arrays.asList(FOO, "1", "2", "3");
		final List<String> line2 = Arrays.asList(BAR, "10", "20", "30");
		final List<String> line3 = Arrays.asList(BAZ, "100", "200", "300");
		final List<List<String>> table = Arrays.asList(line1, line2, line3);

		computeProcessor.setSourceTable(SourceTable.builder().table(table).build());

		return table;
	}

	@Test
	void testKeepOnlyMatchingLines() {
		List<List<String>> table = newSourceTable();

		// regexp is null, valueList is null
		KeepOnlyMatchingLines keepOnlyMatchingLines = KeepOnlyMatchingLines
			.builder()
			.column(1)
			.regExp(null)
			.valueList(null)
			.build();
		computeProcessor.process(keepOnlyMatchingLines);
		assertEquals(table, computeProcessor.getSourceTable().getTable());

		// regexp is empty, valueSet is null
		table = newSourceTable();
		keepOnlyMatchingLines.setRegExp("");
		computeProcessor.process(keepOnlyMatchingLines);
		assertEquals(table, computeProcessor.getSourceTable().getTable());

		// regexp is empty, valueSet is empty
		table = newSourceTable();
		keepOnlyMatchingLines.setValueList(EMPTY);
		computeProcessor.process(keepOnlyMatchingLines);
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		// regex is not null, not empty
		table = newSourceTable();
		keepOnlyMatchingLines.setRegExp("^B.*");
		keepOnlyMatchingLines.setValueList(null);
		computeProcessor.process(keepOnlyMatchingLines);
		assertNotEquals(table, computeProcessor.getSourceTable().getTable());
		List<List<String>> resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(2, resultTable.size());
		assertEquals(table.get(1), resultTable.get(0));
		assertEquals(table.get(2), resultTable.get(1));

		// regex is null,
		// valueSet is not null, not empty
		table = newSourceTable();
		computeProcessor.getSourceTable().setTable(table);
		keepOnlyMatchingLines.setRegExp(null);
		keepOnlyMatchingLines.setValueList("3,300");
		keepOnlyMatchingLines.setColumn(4);
		computeProcessor.process(keepOnlyMatchingLines);
		assertNotEquals(table, computeProcessor.getSourceTable().getTable());
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(2, resultTable.size());
		assertEquals(table.get(0), resultTable.get(0));
		assertEquals(table.get(2), resultTable.get(1));

		// regex is not null, not empty
		// valueSet is not null, not empty
		table = newSourceTable();
		keepOnlyMatchingLines.setColumn(1);
		keepOnlyMatchingLines.setRegExp("^B.*"); // Applying only the regex would match line2 and line3
		keepOnlyMatchingLines.setValueList("FOO,BAR,BAB"); // Applying only the valueSet would match line1 and line2
		computeProcessor.process(keepOnlyMatchingLines);
		assertNotEquals(table, computeProcessor.getSourceTable().getTable());
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(1, resultTable.size()); // Applying both the regex and the valueList matches only line2
		assertEquals(table.get(1), resultTable.get(0));

		// regex is null, not empty
		// valueSet is not null, not empty and contains
		table = newSourceTable();
		keepOnlyMatchingLines.setColumn(1);
		keepOnlyMatchingLines.setRegExp(null);
		keepOnlyMatchingLines.setValueList("FOz,bar,BAB"); // Applying only the valueSet would match line2
		computeProcessor.process(keepOnlyMatchingLines);
		assertNotEquals(table, computeProcessor.getSourceTable().getTable());
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(1, resultTable.size()); // Applying both the regex and the valueList matches only line2
		assertEquals(table.get(1), resultTable.get(0));
	}

	@Test
	void testGetPredicate() {
		List<String> line1 = Arrays.asList(FOO, "1", "2", "3");
		List<String> line2 = Arrays.asList(BAR, "10", "20", "30");
		List<String> line3 = Arrays.asList(BAZ, "100", "2", "300");
		List<String> line4 = Arrays.asList(BAZ + FOO, FOO, "2000", "3000");
		List<List<String>> table = Arrays.asList(line1, line2, line3, line4);

		computeProcessor.setSourceTable(SourceTable.builder().table(table).build());
		KeepOnlyMatchingLines keepOnlyMatchingLines = KeepOnlyMatchingLines
			.builder()
			.column(1)
			.regExp("^B")
			.valueList(null)
			.build();
		computeProcessor.process(keepOnlyMatchingLines);
		// check regex column 1 starts with B
		List<List<String>> resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(3, resultTable.size());
		assertEquals(line2, resultTable.get(0));
		assertEquals(line3, resultTable.get(1));
		assertEquals(line4, resultTable.get(2));

		keepOnlyMatchingLines.setColumn(2);
		keepOnlyMatchingLines.setRegExp("[1-9]");
		computeProcessor.process(keepOnlyMatchingLines);
		// check regex column 2 is numeric value
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(2, resultTable.size());
		assertEquals(line2, resultTable.get(0));
		assertEquals(line3, resultTable.get(1));

		keepOnlyMatchingLines.setColumn(3);
		keepOnlyMatchingLines.setRegExp("^.$");
		computeProcessor.process(keepOnlyMatchingLines);
		// check regex column 3 contains only one character
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(1, resultTable.size());
		assertEquals(line3, resultTable.get(0));

		keepOnlyMatchingLines.setRegExp("blabla");
		computeProcessor.process(keepOnlyMatchingLines);
		// nothing matches with blabla
		assertTrue(computeProcessor.getSourceTable().getTable().isEmpty());

		table = Arrays.asList(line1, line2, line3, line4);

		computeProcessor.setSourceTable(SourceTable.builder().table(table).build());
		final ExcludeMatchingLines excludeMatchingLines = ExcludeMatchingLines.builder().column(1).regExp("^B").build();
		// exclude lines starting with B
		computeProcessor.process(excludeMatchingLines);
		resultTable = computeProcessor.getSourceTable().getTable();
		assertNotNull(resultTable);
		assertEquals(1, resultTable.size());
		assertEquals(line1, resultTable.get(0));

		table = Arrays.asList(line1, line2, line3, line4);

		// exclude lines with column 2 is numeric value
		computeProcessor.setSourceTable(SourceTable.builder().table(table).build());
		excludeMatchingLines.setRegExp("[1-9]");
		excludeMatchingLines.setColumn(2);
		computeProcessor.process(excludeMatchingLines);
		resultTable = computeProcessor.getSourceTable().getTable();
		assertEquals(1, resultTable.size());
		assertEquals(line4, resultTable.get(0));

		excludeMatchingLines.setRegExp("blabla"); // unchanged result
		computeProcessor.process(excludeMatchingLines);
		resultTable = computeProcessor.getSourceTable().getTable();
		assertEquals(1, resultTable.size());
		assertEquals(line4, resultTable.get(0));

		// check regex ignoreCase
		line1 = Arrays.asList("A", "temperature", "motherboard");
		line2 = Arrays.asList("A", "Fan", "fan3");
		line3 = Arrays.asList("B", "temperature", "bp-temp2   ");
		line4 = Arrays.asList("B", "fan", "fan2");
		table = Arrays.asList(line1, line2, line3, line4);

		computeProcessor.setSourceTable(SourceTable.builder().table(table).build());
		keepOnlyMatchingLines = KeepOnlyMatchingLines.builder().column(2).regExp("fan").build();
		computeProcessor.process(keepOnlyMatchingLines);
		final List<List<String>> expectedTableResult = Arrays.asList(line2, line4);
		assertEquals(expectedTableResult, computeProcessor.getSourceTable().getTable());

		// check valueSet ignoreCase
		table = Arrays.asList(line1, line2, line3, line4);
		computeProcessor.setSourceTable(SourceTable.builder().table(table).build());
		keepOnlyMatchingLines = KeepOnlyMatchingLines.builder().column(2).valueList("fan,Fan").build();
		computeProcessor.process(keepOnlyMatchingLines);
		assertEquals(expectedTableResult, computeProcessor.getSourceTable().getTable());
	}

	@Test
	void testProcessAwk() throws Exception {
		final EmbeddedFile embeddedFile = EmbeddedFile.builder().content(BAZ.getBytes()).id(1).build();
		final String connectorId = "connectorId";
		final Connector connector = Connector.builder().embeddedFiles(Map.of(1, embeddedFile)).build();

		final Map<String, Connector> store = Map.of(connectorId, connector);

		final TelemetryManager telemetryManager = TelemetryManager.builder().connectorStore(connectorStoreMock).build();

		doReturn(store).when(connectorStoreMock).getStore();

		computeProcessor.setConnectorId(connectorId);
		computeProcessor.setTelemetryManager(telemetryManager);

		List<List<String>> table = Arrays.asList(LINE_1, LINE_2, LINE_3);

		sourceTable.setTable(table);
		Awk awkNull = null;
		computeProcessor.process(awkNull);
		assertEquals(table, sourceTable.getTable());

		sourceTable.setTable(null);
		sourceTable.setRawData(null);

		final String embeddedFileName = "${file::1}";

		Awk awkOK = Awk
			.builder()
			.script(embeddedFileName)
			.keep("^" + FOO)
			.exclude("^" + BAR)
			.separators(TABLE_SEP)
			.selectColumns(ONE_TWO_THREE)
			.build();

		doReturn(
			"FOO;ID1;NAME1;MANUFACTURER1;NUMBER_OF_DISKS1\nBAR;ID2;NAME2;MANUFACTURER2;NUMBER_OF_DISKS2\nBAZ;ID3;NAME3;MANUFACTURER3;NUMBER_OF_DISKS3"
		)
			.when(clientsExecutorMock)
			.executeAwkScript(any(), any());

		computeProcessor.process(awkOK);
		String expectedRawData = "FOO;ID1;NAME1;";
		List<List<String>> expectedTable = Arrays.asList(Arrays.asList(FOO, ID1, NAME1));
		assertEquals(expectedTable, sourceTable.getTable());
		assertEquals(expectedRawData, sourceTable.getRawData());

		final List<List<String>> osCommandResultTable = List.of(List.of("OS command result"));
		sourceTable.setTable(osCommandResultTable);
		sourceTable.setRawData(null);
		awkOK =
			Awk
				.builder()
				.script(embeddedFileName)
				.keep("^" + FOO)
				.exclude("^" + BAR)
				.separators(TABLE_SEP)
				.selectColumns(ONE_TWO_THREE)
				.build();
		doReturn(null).when(clientsExecutorMock).executeAwkScript(any(), any());

		computeProcessor.process(awkOK);
		assertEquals(Collections.emptyList(), sourceTable.getTable());

		sourceTable.setTable(osCommandResultTable);
		sourceTable.setRawData(null);
		awkOK =
			Awk
				.builder()
				.script(embeddedFileName)
				.keep("^" + FOO)
				.exclude("^" + BAR)
				.separators(TABLE_SEP)
				.selectColumns(ONE_TWO_THREE)
				.build();
		doReturn(EMPTY).when(clientsExecutorMock).executeAwkScript(any(), any());

		computeProcessor.process(awkOK);
		assertEquals(Collections.emptyList(), sourceTable.getTable());

		sourceTable.setRawData(null);
		sourceTable.setTable(table);
		doReturn(SourceTable.tableToCsv(table, TABLE_SEP, true)).when(clientsExecutorMock).executeAwkScript(any(), any());

		computeProcessor.process(
			Awk.builder().script(embeddedFileName).exclude(ID1).keep(ID2).separators(TABLE_SEP).selectColumns("2,3").build()
		);
		assertEquals("NAME2;MANUFACTURER2;", sourceTable.getRawData());
		assertEquals(Arrays.asList(Arrays.asList(NAME2, MANUFACTURER2)), sourceTable.getTable());

		// Let's try with a space character in the selectColumns list
		doReturn(SourceTable.tableToCsv(table, TABLE_SEP, true)).when(clientsExecutorMock).executeAwkScript(any(), any());

		computeProcessor.process(
			Awk.builder().script(embeddedFileName).exclude(ID1).keep(ID2).separators(TABLE_SEP).selectColumns("2, 3").build()
		);
		assertEquals("NAME2;MANUFACTURER2;", sourceTable.getRawData());
		assertEquals(Arrays.asList(Arrays.asList(NAME2, MANUFACTURER2)), sourceTable.getTable());
	}

	@Test
	void testProcessInlineAwk() throws Exception {
		List<List<String>> table = Arrays.asList(LINE_1, LINE_2, LINE_3);

		sourceTable.setTable(table);
		sourceTable.setRawData(null);

		final Awk awkOK = Awk
			.builder()
			.script(
				"""
					BEGIN { FS = ";"; }
					{
						print $1 ";" $2 ";" $3 ";"
					}
				"""
			)
			.keep("^" + ID1)
			.separators(TABLE_SEP)
			.selectColumns(ONE_TWO_THREE)
			.build();

		doCallRealMethod().when(clientsExecutorMock).executeAwkScript(any(), any());

		computeProcessor.process(awkOK);
		final List<List<String>> expectedTable = Arrays.asList(Arrays.asList(ID1, NAME1, MANUFACTURER1));
		String expectedRawData = SourceTable.tableToCsv(expectedTable, ";", false);
		assertEquals(expectedTable, sourceTable.getTable());
		assertEquals(expectedRawData, sourceTable.getRawData());
	}

	@Test
	void testKeepColumns() {
		List<List<String>> table = Arrays.asList(LINE_1, LINE_2, LINE_3);

		sourceTable.setTable(table);

		// KeepColumns is null
		computeProcessor.process((KeepColumns) null);
		assertEquals(table, sourceTable.getTable());

		// KeepColumns is null, keepColumns.getColumnNumbers() is invalid
		KeepColumns keepColumns = KeepColumns.builder().columnNumbers("-1").build();
		computeProcessor.process(keepColumns);
		assertEquals(table, sourceTable.getTable());

		// KeepColumns is null, keepColumns.getColumnNumbers() is not null and not empty,
		// 1 column number is lower than 1
		keepColumns.setColumnNumbers("1,0,3");
		computeProcessor.process(keepColumns);
		assertEquals(table, sourceTable.getTable());

		// KeepColumns is null, keepColumns.getColumnNumbers() is not null and not empty,
		// 1 column number is greater than the rows' size
		keepColumns.setColumnNumbers("1,5,3");
		computeProcessor.process(keepColumns);
		assertEquals(table, sourceTable.getTable());

		// KeepColumns is null, keepColumns.getColumnNumbers() is not null and not empty,
		// 1 column number is not valid
		keepColumns.setColumnNumbers("1,-1,3");
		computeProcessor.process(keepColumns);
		List<List<String>> expectedTable = Arrays.asList(LINE_1, LINE_2, LINE_3);
		assertEquals(expectedTable, sourceTable.getTable()); // null index will be skipped

		// test OK
		sourceTable.setTable(table);
		List<List<String>> result = Arrays.asList(
			Arrays.asList(LINE_1.get(0), LINE_1.get(1), LINE_1.get(3)),
			Arrays.asList(LINE_2.get(0), LINE_2.get(1), LINE_2.get(3)),
			Arrays.asList(LINE_3.get(0), LINE_3.get(1), LINE_3.get(3))
		);

		keepColumns.setColumnNumbers("1,2,4");
		computeProcessor.process(keepColumns);
		assertEquals(result, sourceTable.getTable());

		// test OK but index are not sorted
		sourceTable.setTable(table);
		keepColumns.setColumnNumbers("1,4,2");
		computeProcessor.process(keepColumns);
		assertEquals(result, sourceTable.getTable());
	}

	@Test
	void testDuplicateColumn() {
		sourceTable.getTable().add(new ArrayList<>(LINE_1));
		// test null arg
		computeProcessor.process((DuplicateColumn) null);
		assertEquals(
			Collections.singletonList(Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1")),
			sourceTable.getTable()
		);

		// test out of bounds
		DuplicateColumn duplicateColumn = new DuplicateColumn("1", 0);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Collections.singletonList(Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1")),
			sourceTable.getTable()
		);

		duplicateColumn = new DuplicateColumn("10", 10);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Collections.singletonList(Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1")),
			sourceTable.getTable()
		);

		// test actual index
		duplicateColumn = new DuplicateColumn("1", 1);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Collections.singletonList(Arrays.asList("ID1", "ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1")),
			sourceTable.getTable()
		);

		duplicateColumn = new DuplicateColumn("2", 2);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Collections.singletonList(Arrays.asList("ID1", "ID1", "ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1")),
			sourceTable.getTable()
		);

		duplicateColumn = new DuplicateColumn("3", 6);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Collections.singletonList(
				Arrays.asList("ID1", "ID1", "ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1", "NUMBER_OF_DISKS1")
			),
			sourceTable.getTable()
		);

		// test multiple lines
		initializeSourceTable();

		duplicateColumn = new DuplicateColumn("13", 3);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		duplicateColumn = new DuplicateColumn("13", 7);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		duplicateColumn = new DuplicateColumn("13", -1);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		duplicateColumn = new DuplicateColumn("13", 0);
		computeProcessor.process(duplicateColumn);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);
	}

	@Test
	void testProcessConvert() {
		{
			final List<List<String>> table = Arrays.asList(
				Arrays.asList("ID1", "ff: dd:11"),
				Arrays.asList("ID2", "aa:: dd: 22"),
				Arrays.asList("ID3", " bb:cc:22 ")
			);

			sourceTable.setTable(table);

			final Convert convert = Convert.builder().column(2).conversion(ConversionType.HEX_2_DEC).build();

			computeProcessor.process(convert);

			final List<List<String>> expected = Arrays.asList(
				Arrays.asList("ID1", "16768273"),
				Arrays.asList("ID2", "11197730"),
				Arrays.asList("ID3", "12307490")
			);

			assertEquals(expected, table);
		}

		{
			final List<List<String>> table = Arrays.asList(
				Arrays.asList("ID1", "ok|ok"),
				Arrays.asList("ID2", "ok|\n|degraded|"),
				Arrays.asList("ID3", "ok|degraded\n|ok|failed")
			);

			sourceTable.setTable(table);

			final Convert convert = Convert.builder().column(2).conversion(ConversionType.ARRAY_2_SIMPLE_STATUS).build();

			computeProcessor.process(convert);

			final List<List<String>> expected = Arrays.asList(
				Arrays.asList("ID1", "ok"),
				Arrays.asList("ID2", "degraded"),
				Arrays.asList("ID3", "failed")
			);

			assertEquals(expected, table);
		}
	}

	@Test
	void testTranslate() {
		// Test translate with inline TranslationTable
		final Map<String, String> translationMap = Map.of(
			"name1",
			"NAME1_resolved",
			"name2",
			"NAME2_resolved",
			"name3",
			"NAME3_resolved",
			"id1",
			"ID1_resolved",
			"id2",
			"ID2_resolved",
			"id3",
			"ID3_resolved",
			"number_of_disks1",
			"NUMBER_OF_DISKS1_resolved",
			"number_of_disks2",
			"NUMBER_OF_DISKS2_resolved",
			"number_of_disks3",
			"NUMBER_OF_DISKS3_resolved"
		);

		// test null source to visit
		initializeSourceTable();
		computeProcessor.process((Translate) null);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		// test TranslationTable is null
		initializeSourceTable();
		Translate translate = Translate
			.builder()
			.column(0)
			.translationTable(TranslationTable.builder().translations(Collections.emptyMap()).build())
			.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		initializeSourceTable();
		translate = Translate.builder().column(0).translationTable(TranslationTable.builder().build()).build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		// test index out of bounds
		initializeSourceTable();
		translate =
			Translate
				.builder()
				.column(0)
				.translationTable(TranslationTable.builder().translations(translationMap).build())
				.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		initializeSourceTable();
		translate =
			Translate
				.builder()
				.column(10)
				.translationTable(TranslationTable.builder().translations(translationMap).build())
				.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		// test 1st index
		initializeSourceTable();
		translate =
			Translate
				.builder()
				.column(1)
				.translationTable(TranslationTable.builder().translations(translationMap).build())
				.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1_resolved", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2_resolved", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3_resolved", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		// test intermediate index
		initializeSourceTable();
		translate =
			Translate
				.builder()
				.column(2)
				.translationTable(TranslationTable.builder().translations(translationMap).build())
				.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1_resolved", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2", "NAME2_resolved", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3", "NAME3_resolved", "MANUFACTURER3", "NUMBER_OF_DISKS3")
			),
			sourceTable.getTable()
		);

		// test last index
		initializeSourceTable();
		translate =
			Translate
				.builder()
				.column(4)
				.translationTable(TranslationTable.builder().translations(translationMap).build())
				.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1_resolved"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2_resolved"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3_resolved")
			),
			sourceTable.getTable()
		);

		// test unknown value
		initializeSourceTable();
		sourceTable.getTable().add(new ArrayList<>(Arrays.asList("ID", "NAME", "MANUFACTURER", "NUMBER_OF_DISKS")));
		translate =
			Translate
				.builder()
				.column(1)
				.translationTable(TranslationTable.builder().translations(translationMap).build())
				.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1_resolved", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1"),
				Arrays.asList("ID2_resolved", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2"),
				Arrays.asList("ID3_resolved", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3"),
				Arrays.asList("ID", "NAME", "MANUFACTURER", "NUMBER_OF_DISKS")
			),
			sourceTable.getTable()
		);

		// test with semicolon
		final Map<String, String> translationMapSemiColon = Map.of(
			"name1",
			"NAME1_resolved",
			"name2",
			"NAME2_resolved",
			"name3",
			"NAME3_resolved",
			"id1",
			"ID1_resolved",
			"id2",
			"ID2_resolved",
			"id3",
			"ID3_resolved",
			"number_of_disks1",
			"NUMBER_OF_DISKS1_resolved;new_column_1",
			"number_of_disks2",
			"NUMBER_OF_DISKS2_resolved;new_column_2",
			"number_of_disks3",
			"NUMBER_OF_DISKS3_resolved;new_column_3"
		);

		initializeSourceTable();
		translate =
			Translate
				.builder()
				.column(4)
				.translationTable(TranslationTable.builder().translations(translationMapSemiColon).build())
				.build();
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1_resolved", "new_column_1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2_resolved", "new_column_2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3_resolved", "new_column_3")
			),
			sourceTable.getTable()
		);

		// Test translate with  ReferenceTranslationTable

		final ITranslationTable translationTable = new ReferenceTranslationTable("${translation::translationTableName}");
		translate.setTranslationTable(translationTable);
		final Map<String, String> translations = Map.of(
			"",
			"NO_VALUE",
			"status11",
			"TRANSLATED_STATUS11",
			"status12",
			"TRANSLATED_STATUS12",
			"status13",
			"TRANSLATED_STATUS13",
			"status22",
			"TRANSLATED_STATUS22", // No translation for STATUS22
			"status31",
			"TRANSLATED_STATUS31"
		);
		final String translationTableName = "translationTableName";
		final TranslationTable connectorTranslationTable = TranslationTable.builder().translations(translations).build();
		final String connectorId = "connectorId";
		final Connector connector = Connector
			.builder()
			.translations(Collections.singletonMap(translationTableName, connectorTranslationTable))
			.build();

		final Map<String, Connector> store = Map.of(connectorId, connector);

		final TelemetryManager telemetryManager = TelemetryManager.builder().connectorStore(connectorStoreMock).build();

		doReturn(store).when(connectorStoreMock).getStore();
		computeProcessor.setConnectorId(connectorId);
		computeProcessor.setTelemetryManager(telemetryManager);
		computeProcessor.process(translate);
		assertEquals(
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "NUMBER_OF_DISKS1_resolved", "new_column_1"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "NUMBER_OF_DISKS2_resolved", "new_column_2"),
				Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "NUMBER_OF_DISKS3_resolved", "new_column_3")
			),
			sourceTable.getTable()
		);
	}

	@Test
	void testProcessExtractPropertyFromWbemPath() {
		final List<List<String>> table = Arrays.asList(LINE_WBEM_PATH_1, LINE_WBEM_PATH_2, LINE_WBEM_PATH_3);

		sourceTable.setTable(table);
		ExtractPropertyFromWbemPath extractPropertyFromWbemPath = null;
		computeProcessor.process(extractPropertyFromWbemPath);
		assertEquals(table, sourceTable.getTable());

		extractPropertyFromWbemPath = ExtractPropertyFromWbemPath.builder().property("name").column(4).build();
		final List<List<String>> tableResult = Arrays.asList(
			LINE_WBEM_PATH_1_RESULT,
			LINE_WBEM_PATH_2_RESULT,
			LINE_WBEM_PATH_3_RESULT
		);
		computeProcessor.process(extractPropertyFromWbemPath);
		assertEquals(tableResult, sourceTable.getTable());
	}

	@Test
	void testPerBitTranslation() {
		final Map<String, String> translationMap = Map.of(
			"0,1",
			"No Network",
			"1,0",
			"Authentication Failure",
			"1,1",
			"Not Ready",
			"2,1",
			"Fan Failure",
			"3,1",
			"AC Switch On",
			"4,1",
			"AC Power On",
			"5,1",
			"Ready",
			"6,1",
			"Failed",
			"7,1",
			"Predicted Failure"
		);

		final String bitList = "0,1,2,3,4,5,6,7";

		List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "1"),
			Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "2"),
			Arrays.asList("ID3", "NAME3", "MANUFACTURER3", "255")
		);

		sourceTable.setTable(table);

		// test null source to visit
		computeProcessor.process((PerBitTranslation) null);
		assertEquals(table, sourceTable.getTable());

		final PerBitTranslation translate = PerBitTranslation
			.builder()
			.column(0)
			.bitList(EMPTY)
			.translationTable(TranslationTable.builder().build())
			.build();

		// test translations is null
		translate.setTranslationTable(TranslationTable.builder().translations(null).build());
		computeProcessor.process(translate);
		assertEquals(table, sourceTable.getTable());

		// test column index out of bounds
		final TranslationTable translationTable = (TranslationTable) (translate.getTranslationTable());
		translationTable.setTranslations(translationMap);
		computeProcessor.process(translate);
		assertEquals(table, sourceTable.getTable());

		translate.setColumn(10);
		computeProcessor.process(translate);
		assertEquals(table, sourceTable.getTable());

		// test column value is not an integer
		translate.setBitList(EMPTY);
		translate.setColumn(3);
		computeProcessor.process(translate);
		assertEquals(table, sourceTable.getTable());

		// test OK
		translate.setColumn(4);
		translate.setBitList(bitList);
		table =
			Arrays.asList(
				Arrays.asList("ID1", "NAME1", "MANUFACTURER1", "No Network - Authentication Failure"),
				Arrays.asList("ID2", "NAME2", "MANUFACTURER2", "Not Ready"),
				Arrays.asList(
					"ID3",
					"NAME3",
					"MANUFACTURER3",
					"No Network - Not Ready - Fan Failure - AC Switch On - AC Power On - Ready - Failed - Predicted Failure"
				)
			);
		computeProcessor.process(translate);
		assertEquals(table, sourceTable.getTable());
	}

	@Test
	void testValidateSizeAndIndices() {
		assertTrue(
			computeProcessor.validateSizeAndIndices(5, 2, 3, 4),
			"Expected the method to return true for valid indices."
		);
		assertFalse(
			computeProcessor.validateSizeAndIndices(5, 2, -1, 4),
			"Expected the method to return false for an invalid index."
		);
		assertFalse(
			computeProcessor.validateSizeAndIndices(5, 2, 3, 5),
			"Expected the method to return false for an index equal to the collection size."
		);
	}

	@Test
	void testConvertHex2Dec() {
		// Prepare the sourceTable table with hex values
		final List<List<String>> table = Arrays.asList(
			Arrays.asList("ID1", "FFFFFFFFFFFFFFE7"), // Large negative-like hex for unsigned conversion
			Arrays.asList("ID2", "7FFFFFFFFFFFFFFE"), // Large positive hex
			Arrays.asList("ID3", "123ABCD") // A smaller hex value
		);

		// Set up the source table with our input data
		sourceTable.setTable(table);

		// Build a conversion operation: convert the 2nd column (index 2) from HEX to DEC
		final Convert convert = Convert.builder().column(2).conversion(ConversionType.HEX_2_DEC).build();

		// Execute the conversion on the source table
		computeProcessor.process(convert);

		// Define the expected results: the decimal equivalents of the hex values
		final List<List<String>> expected = Arrays.asList(
			Arrays.asList("ID1", "18446744073709551591"),
			Arrays.asList("ID2", "9223372036854775806"),
			Arrays.asList("ID3", "19114957")
		);

		// Verify the converted table matches our expected values
		assertEquals(expected, sourceTable.getTable());
	}

	@Test
	void testEncodeAndDecode() {
		// Prepare the sourceTable table with values to encode.
		final List<List<String>> baseTable = Arrays.asList(
			Arrays.asList("ID1", "testEncode"),
			Arrays.asList("ID2", "username:password"),
			Arrays.asList("ID3", "123//;AB&CD")
		);

		// Set up the source table with our input data.
		sourceTable.setTable(
			Arrays.asList(
				Arrays.asList("ID1", "testEncode"),
				Arrays.asList("ID2", "username:password"),
				Arrays.asList("ID3", "123//;AB&CD")
			)
		);

		// Wrong type of encoding.
		final Encode encodeWrongEncoding = Encode.builder().column(2).encoding("WrongEncoding").build();

		// Execute the Encode compute.
		computeProcessor.process(encodeWrongEncoding);

		// Verify there is no change.
		assertEquals(baseTable, sourceTable.getTable());

		// Wrong type of decoding.
		final Decode decodeWrongEncoding = Decode.builder().column(2).encoding("WrongEncoding").build();

		// Execute the Decode compute.
		computeProcessor.process(decodeWrongEncoding);

		// Verify there is no change.
		assertEquals(baseTable, sourceTable.getTable());

		// Base64 encoding.
		final Encode encodeBase64 = Encode.builder().column(2).encoding("Base64").build();

		// Execute the Encode compute.
		computeProcessor.process(encodeBase64);

		// Define the expected results.
		List<List<String>> expected = Arrays.asList(
			Arrays.asList("ID1", "dGVzdEVuY29kZQ=="),
			Arrays.asList("ID2", "dXNlcm5hbWU6cGFzc3dvcmQ="),
			Arrays.asList("ID3", "MTIzLy87QUImQ0Q=")
		);

		// Verify the converted table matches our expected values.
		assertEquals(expected, sourceTable.getTable());

		// Base64 decoding.
		final Decode decodeBase64 = Decode.builder().column(2).encoding("Base64").build();

		// Execute the Decode compute.
		computeProcessor.process(decodeBase64);

		// Verify the converted table matches the base table.
		assertEquals(baseTable, sourceTable.getTable());

		// URL encoding.
		final Encode encodeUrl = Encode.builder().column(2).encoding("URLEncode").build();

		// Execute the Encode compute.
		computeProcessor.process(encodeUrl);

		// Define the expected results.
		expected =
			Arrays.asList(
				Arrays.asList("ID1", "testEncode"),
				Arrays.asList("ID2", "username%3Apassword"),
				Arrays.asList("ID3", "123%2F%2F%3BAB%26CD")
			);

		// Verify the converted table matches our expected values.
		assertEquals(expected, sourceTable.getTable());

		// URL decoding.
		final Decode decodeURL = Decode.builder().column(2).encoding("URLDecode").build();

		// Execute the Decode compute.
		computeProcessor.process(decodeURL);

		// Verify the converted table matches the base table.
		assertEquals(baseTable, sourceTable.getTable());
	}
}
