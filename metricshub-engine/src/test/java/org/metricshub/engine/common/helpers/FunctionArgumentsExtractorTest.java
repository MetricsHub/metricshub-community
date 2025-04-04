package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionArgumentsExtractorTest {

	@Test
	void testExtractFunctionArguments() {
		assertEquals(
			List.of("disk_controller", "id", "controller_number", "$2"),
			FunctionArgumentsExtractor.extractArguments("lookup(\"disk_controller\", \"id\", \"controller_number\", $2)")
		);

		assertEquals(
			List.of("(disk_controller)", "id", "controller_number", "$2"),
			FunctionArgumentsExtractor.extractArguments("lookup(\"(disk_controller)\",   \"id\",	\"controller_number\", $2)")
		);

		assertEquals(
			List.of("(disk_controller", "id", "controller_number", "$2"),
			FunctionArgumentsExtractor.extractArguments("lookup(\"(disk_controller\", \"id\", \"controller_number\", $2)")
		);

		assertEquals(
			List.of("disk_controller)", "id", "controller_number", "$2"),
			FunctionArgumentsExtractor.extractArguments("lookup(\"disk_controller)\", \"id\", \"controller_number\", $2)")
		);

		assertEquals(
			List.of("disk,controller", "id", "controller_number", "$2"),
			FunctionArgumentsExtractor.extractArguments(
				"lookup(\"disk,controller\",     \"id\",   \"controller_number\", $2)"
			)
		);
	}
}
