package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.engine.constants.Constants.SINGLE_SPACE;
import static org.metricshub.engine.constants.Constants.TABLE_SEP;

import java.util.List;
import org.junit.jupiter.api.Test;

class FilterResultHelperTest {

	private static final String LINE1 = "FOO;ID1;NAME1;MANUFACTURER1;NUMBER_OF_DISKS1";
	private static final String LINE2 = "BAR;ID2;NAME2;MANUFACTURER2;NUMBER_OF_DISKS2";
	private static final String LINE3 = "BAZ;ID3;NAME3;MANUFACTURER3;NUMBER_OF_DISKS3";
	private static final String NOT_A_LINE = "xxxxxxxxxx";
	private static final List<String> LINE_RAW_DATA = List.of(LINE1, LINE2, LINE3);

	@Test
	void testFilterLines() {
		assertThrows(IllegalArgumentException.class, () -> FilterResultHelper.filterLines(null, 1, 1, "^BAR", "^FOO"));

		assertEquals(LINE_RAW_DATA, FilterResultHelper.filterLines(LINE_RAW_DATA, null, null, null, null));
		assertEquals(LINE_RAW_DATA, FilterResultHelper.filterLines(LINE_RAW_DATA, 0, null, null, null));
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3), 1, null, null, null)
		);
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, NOT_A_LINE, LINE1, LINE2, LINE3), 2, null, null, null)
		);
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(List.of(LINE1, LINE2, LINE3, NOT_A_LINE, NOT_A_LINE), null, 2, null, null)
		);
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(List.of(LINE1, LINE2, LINE3, NOT_A_LINE), null, 1, null, null)
		);
		assertEquals(LINE_RAW_DATA, FilterResultHelper.filterLines(List.of(LINE1, LINE2, LINE3), null, 0, null, null));
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 1, 1, null, null)
		);
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE, NOT_A_LINE), 1, 2, null, null)
		);
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 2, 1, null, null)
		);
		assertEquals(
			LINE_RAW_DATA,
			FilterResultHelper.filterLines(
				List.of(NOT_A_LINE, NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE, NOT_A_LINE),
				2,
				2,
				null,
				null
			)
		);

		assertEquals(
			List.of(LINE1, LINE3),
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 1, 1, "^BAR", null)
		);

		assertEquals(
			List.of(LINE1),
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 1, 1, null, "^FOO")
		);

		assertEquals(
			List.of(LINE1),
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 1, 1, "^BAR", "^FOO")
		);

		assertEquals(
			List.of(),
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 1, 1, "^BAR", "^BAR")
		);

		assertEquals(
			List.of(LINE3),
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 2, 1, "^BAR", null)
		);

		assertEquals(
			List.of(),
			FilterResultHelper.filterLines(List.of(NOT_A_LINE, LINE1, LINE2, LINE3, NOT_A_LINE), 2, 1, null, "^FOO")
		);
	}

	@Test
	void testSelectedColumns() {
		assertThrows(IllegalArgumentException.class, () -> FilterResultHelper.selectedColumns(null, TABLE_SEP, "-4"));

		// no separator
		assertEquals(LINE_RAW_DATA, FilterResultHelper.selectedColumns(LINE_RAW_DATA, null, "-3"));
		assertEquals(LINE_RAW_DATA, FilterResultHelper.selectedColumns(LINE_RAW_DATA, "", "-3"));

		// no selected columns
		assertEquals(LINE_RAW_DATA, FilterResultHelper.selectedColumns(LINE_RAW_DATA, TABLE_SEP, null)); // no selected columns
		assertEquals(LINE_RAW_DATA, FilterResultHelper.selectedColumns(LINE_RAW_DATA, TABLE_SEP, "")); // no selected columns

		// out of bounds
		assertEquals(List.of("", "", ""), FilterResultHelper.selectedColumns(LINE_RAW_DATA, TABLE_SEP, "50"));

		// other separator
		final List<String> rawDataLinesOtherSeparator = List.of(
			"FOO_ID1_NAME1_MANUFACTURER1_NUMBER_OF_DISKS1",
			"BAR_ID2_NAME2_MANUFACTURER2_NUMBER_OF_DISKS2",
			"BAZ_ID3_NAME3_MANUFACTURER3_NUMBER_OF_DISKS3"
		);

		final List<String> expected = List.of(
			"FOO;ID1;NAME1;MANUFACTURER1",
			"BAR;ID2;NAME2;MANUFACTURER2",
			"BAZ;ID3;NAME3;MANUFACTURER3"
		);
		assertEquals(expected, FilterResultHelper.selectedColumns(LINE_RAW_DATA, TABLE_SEP, "-4")); // use case trad
		assertEquals(expected, FilterResultHelper.selectedColumns(rawDataLinesOtherSeparator, "_", "-4")); // use case trad

		assertEquals(
			List.of("ID1;NAME1", "ID2;NAME2", "ID3;NAME3"),
			FilterResultHelper.selectedColumns(LINE_RAW_DATA, TABLE_SEP, "2,3")
		); // use case trad

		assertEquals(
			List.of("name1;val1", "name2;val2", "name3;val3"),
			FilterResultHelper.selectedColumns(
				List.of("id1   name1  val1", "id2   name2  val2", "id3   name3  val3"),
				SINGLE_SPACE,
				"2,3"
			)
		); // spaces separator

		assertEquals(
			List.of("na,me1;val,1", "name2;val2", "name3;val3"),
			FilterResultHelper.selectedColumns(
				List.of("id1   na;me1  val;1", "id2   name2  val2", "id3   name3  val3"),
				SINGLE_SPACE,
				"2,3"
			)
		); // make sure that we replace intial ";" by "," because we use it as separator

		assertEquals(
			List.of("MANUFACTURER1;NUMBER_OF_DISKS1", "MANUFACTURER2;NUMBER_OF_DISKS2", "MANUFACTURER3;NUMBER_OF_DISKS3"),
			FilterResultHelper.selectedColumns(LINE_RAW_DATA, TABLE_SEP, "4-")
		); // use case trad

		assertEquals(
			List.of(
				"FOO;ID1;MANUFACTURER1;NUMBER_OF_DISKS1",
				"BAR;ID2;MANUFACTURER2;NUMBER_OF_DISKS2",
				"BAZ;ID3;MANUFACTURER3;NUMBER_OF_DISKS3"
			),
			FilterResultHelper.selectedColumns(LINE_RAW_DATA, TABLE_SEP, "-2,4-")
		); // use case trad
	}
}
