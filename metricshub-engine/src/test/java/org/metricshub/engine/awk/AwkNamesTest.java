package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jawk.backend.AVM;
import io.jawk.intermediate.BuiltinFunction;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AwkNamesTest {

	@Test
	void identifiers() {
		assertTrue(AwkNames.isIdentifier("BLOCK_SIZE"));
		assertTrue(AwkNames.isIdentifier("_x1"));
		assertFalse(AwkNames.isIdentifier("1x"));
		assertFalse(AwkNames.isIdentifier("source(1)"));
		assertFalse(AwkNames.isIdentifier("disk-model"));
		assertFalse(AwkNames.isIdentifier(""));
		assertFalse(AwkNames.isIdentifier(null));
		assertTrue(AwkNames.isNameable("model"));
		assertFalse(AwkNames.isNameable("log"));
	}

	@Test
	void reservedNames() {
		for (final String name : new String[] { "log", "NR", "FS", "BEGIN", "getline", "ENVIRON", "SYMTAB" }) {
			assertTrue(AwkNames.isReserved(name), name);
		}
		assertFalse(AwkNames.isReserved("nr"));
		assertFalse(AwkNames.isReserved("rate"));
	}

	@Test
	void reservedCoversJawk() {
		assertTrue(AwkNames.BUILTIN_FUNCTIONS.containsAll(BuiltinFunction.names()));
		assertTrue(AwkNames.SPECIAL_VARIABLES.containsAll(new AVM().getSpecialVariableNames()));
	}

	@Test
	void holdsReference() {
		assertTrue(AwkNames.holdsReference("${source::x}"));
		assertTrue(AwkNames.holdsReference("${file::3}"));
		assertTrue(AwkNames.holdsReference("${attribute::id}"));
		assertFalse(AwkNames.holdsReference("echo ${HOME}"));
		assertFalse(AwkNames.holdsReference(null));
	}

	@Test
	void textSourceName() {
		assertEquals("model", AwkNames.textSourceName("${source::monitors.disk.discovery.sources.model}"));
		assertEquals("info", AwkNames.textSourceName("${source::tools.t.sources.info}"));
		assertEquals("token", AwkNames.textSourceName("${source::beforeAll.token}"));
		assertEquals("done", AwkNames.textSourceName("${source::afterAll.done}"));
		assertNull(AwkNames.textSourceName("${source::monitors.disk.discovery.sources.source(1)}"));
		assertNull(AwkNames.textSourceName("${source::beforeAll.log}"));
		assertNull(AwkNames.textSourceName("${constant::X}"));
		assertNull(AwkNames.textSourceName(null));
	}

	@Test
	void textValue() {
		assertEquals("a\nb", AwkNames.textValue("a\nb\n\n"));
		assertEquals("a", AwkNames.textValue("a"));
		assertEquals("", AwkNames.textValue(null));
	}

	@Test
	void valuesPrecedenceAndExclusions() {
		final Map<String, String> values = AwkNames.values(
			Map.of("ALL", "constant", "TWO", "constant", "ONLY_CONSTANT", "c", "REF", "${source::x}", "FS", "f", "a-b", "x"),
			Map.of("ALL", "variable", "TWO", "variable", "onlyVariable", "v"),
			Map.of("ALL", "text")
		);
		assertEquals(Map.of("ALL", "text", "TWO", "variable", "ONLY_CONSTANT", "c", "onlyVariable", "v"), values);
		assertEquals(Map.of(), AwkNames.values(null, null, null));
	}
}
