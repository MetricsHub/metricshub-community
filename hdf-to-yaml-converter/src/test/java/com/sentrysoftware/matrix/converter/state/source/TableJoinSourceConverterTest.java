package com.sentrysoftware.matrix.converter.state.source;

import java.io.IOException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.sentrysoftware.matrix.converter.AbstractConnectorPropertyConverterTest;

class TableJoinSourceConverterTest extends AbstractConnectorPropertyConverterTest{

	@Override
	protected String getResourcePath() {
		return "src/test/resources/test-files/monitors/source/tablejoin";
	}

	@Test
	@Disabled("until TableJoin Source converter is up")
	void test() throws IOException {
		testConversion("discovery");
		testConversion("collect");

		testAll();
	}
}