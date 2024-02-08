package org.sentrysoftware.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class StringHelperTest {

	@Test
	void testGetStackMessages() {
		assertNotNull(StringHelper.getStackMessages(null));
		assertTrue(StringHelper.getStackMessages(null).isEmpty());
		assertEquals(
			"Exception: Error 2\n" + "Caused by Exception: Error 1",
			StringHelper.getStackMessages(new Exception("Error 2", new Exception("Error 1")))
		);
		assertEquals(
			"Exception: Error 2\n" + "Caused by Exception: Error 1",
			StringHelper.getStackMessages(new Exception("Error 2", new Exception("Error 1", null)))
		);
	}

	@Test
	void testPrettyHttpHeaders() {
		assertEquals(EMPTY, StringHelper.prettyHttpHeaders(Collections.emptyMap()));
		assertEquals(EMPTY, StringHelper.prettyHttpHeaders(null));
		assertEquals(
			"Connection: keep-alive\nContent-Type: application/json",
			StringHelper.prettyHttpHeaders(Map.of("Connection", "keep-alive", "Content-Type", "application/json"))
		);
	}

	@Test
	void testAddNonNull() {
		final StringJoiner stringJoiner = new StringJoiner(";");
		StringHelper.addNonNull(stringJoiner, "prefix", null);
		StringHelper.addNonNull(stringJoiner, "prefix1=", "val1");
		StringHelper.addNonNull(stringJoiner, "prefix2=", 10);
		assertEquals("prefix1=val1;prefix2=10", stringJoiner.toString());
	}

	@Test
	void testReplace() {
		assertEquals("b b cc", StringHelper.replace("$a", "b", "$a $a cc"));
		assertEquals("$a $a cc", StringHelper.replace("$a", (String) null, "$a $a cc"));
		assertEquals("$a $a cc", StringHelper.replace("$d", "b", "$a $a cc"));
		assertThrows(IllegalArgumentException.class, () -> StringHelper.replace(null, "b", "$a $a cc"));
		assertThrows(IllegalArgumentException.class, () -> StringHelper.replace("$a", "$a", null));
	}

	@Test
	void testReplaceWithSupplier() {
		assertEquals("b b cc", StringHelper.replace("$a", () -> "b", "$a $a cc"));
		assertEquals("$a $a cc", StringHelper.replace("$a", () -> (String) null, "$a $a cc"));
		assertEquals(
			"$a $a cc",
			StringHelper.replace(
				"$a",
				() -> {
					throw new IllegalStateException();
				},
				"$a $a cc"
			)
		);
		assertEquals("$a $a cc", StringHelper.replace("$d", () -> "b", "$a $a cc"));
		assertThrows(IllegalArgumentException.class, () -> StringHelper.replace(null, () -> "b", "$a $a cc"));
		assertThrows(IllegalArgumentException.class, () -> StringHelper.replace("$a", () -> "b", null));
		assertThrows(IllegalArgumentException.class, () -> StringHelper.replace("$a", (Supplier<String>) null, "$a $a cc"));
	}

	@Test
	void testStringify() {
		assertEquals(EMPTY, StringHelper.stringify(null));
		assertEquals("a", StringHelper.stringify("a"));
		assertEquals("a,b", StringHelper.stringify(List.of("a", "b")));
		assertEquals("a,b", StringHelper.stringify(new String[] { "a", "b" }));
	}

	@Test
	void testNonNullNonBlank() {
		assertFalse(StringHelper.nonNullNonBlank(EMPTY));
		assertFalse(StringHelper.nonNullNonBlank(null));
		assertTrue(StringHelper.nonNullNonBlank("text"));
	}
}
