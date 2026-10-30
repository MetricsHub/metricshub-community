package org.metricshub.engine.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolParameterTest {

	@Test
	void testTypeDefaultsToStringAndIsNormalized() {
		assertEquals("string", new ToolParameter(null, null, false, null).type());
		assertEquals("string", new ToolParameter(" ", null, false, null).type());
		assertEquals("integer", new ToolParameter(" Integer ", null, false, null).type());
	}

	@Test
	void testUnknownTypeIsRejected() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			new ToolParameter("intger", "A typo", false, null)
		);
		assertTrue(exception.getMessage().contains("intger"), exception.getMessage());
	}

	@Test
	void testDeclarationIsChecked() {
		// A pattern that does not compile
		assertThrows(IllegalArgumentException.class, () -> new ToolParameter(null, null, false, null, "[a-z", null));
		// A default value the parameter itself would refuse
		assertThrows(IllegalArgumentException.class, () -> new ToolParameter("integer", null, false, "ten"));
		assertThrows(IllegalArgumentException.class, () ->
			new ToolParameter(null, null, false, "STOP", null, List.of("TERM", "KILL"))
		);
		// An empty enum accepts anything
		assertNull(new ToolParameter(null, null, false, null, null, List.of()).enumValues());
		// Enum entries of another type than the parameter's, or refused by its pattern
		assertThrows(IllegalArgumentException.class, () ->
			new ToolParameter("integer", null, false, null, null, List.of("1", "two"))
		);
		assertThrows(IllegalArgumentException.class, () ->
			new ToolParameter(null, null, false, null, "[a-z]+", List.of("prod", "123"))
		);
	}

	@Test
	void testIsConstrained() {
		assertFalse(new ToolParameter(null, null, false, null).isConstrained());
		assertTrue(new ToolParameter("integer", null, false, null).isConstrained());
		assertTrue(new ToolParameter(null, null, false, null, null, List.of("a")).isConstrained());
		assertTrue(new ToolParameter(null, null, false, null, ".*", null).isConstrained());
	}

	@Test
	void testWithoutMetacharacters() {
		// Whatever the pattern lets through, no metacharacter, line break included
		final ToolParameter loose = new ToolParameter(null, null, false, null, "[\\s\\S]*", null).withoutMetacharacters();
		for (final String value : List.of(
			"$(id)",
			"a b",
			"a\n",
			"x'y",
			"a;b",
			"`id`",
			"a\\b",
			"%PATH%",
			"a^&b",
			"-rf",
			"--all"
		)) {
			assertThrows(IllegalArgumentException.class, () -> loose.validate("x", value), value);
		}
		assertDoesNotThrow(() -> loose.validate("x", "sshd.service"));
		// The pattern still applies
		final ToolParameter unit = new ToolParameter(null, null, false, null, "[a-z]+", null).withoutMetacharacters();
		assertThrows(IllegalArgumentException.class, () -> unit.validate("unit", "sshd.service"));
		// A default holding a metacharacter makes the parameter unusable unescaped
		final ToolParameter spaced = new ToolParameter(null, null, false, "a b", ".*", null);
		assertThrows(IllegalArgumentException.class, spaced::withoutMetacharacters);
		// Enums and other types are left as they are
		final ToolParameter integer = new ToolParameter("integer", null, false, null);
		assertSame(integer, integer.withoutMetacharacters());
	}

	@Test
	void testValidateRejectsControlCharacters() {
		final ToolParameter text = new ToolParameter(null, null, false, null);
		assertDoesNotThrow(() -> text.validate("text", "a\tb\r\nc"));
		assertThrows(IllegalArgumentException.class, () -> text.validate("text", "a\u0000b"));
		assertThrows(IllegalArgumentException.class, () -> text.validate("text", "a\u001bb"));
		// An unpaired surrogate is no character: XML refuses it; a pair is one
		assertThrows(IllegalArgumentException.class, () -> text.validate("text", "a\uD800b"));
		assertDoesNotThrow(() -> text.validate("text", "a😀b"));
		assertThrows(IllegalArgumentException.class, () -> text.validate("text", "a￿b"));
	}

	@Test
	void testValidateBoundsThePatternMatchingTime() {
		// Catastrophic backtracking: fails after the timeout instead of holding the request
		// (about 20 s for this value without the timeout)
		final ToolParameter nested = new ToolParameter(null, null, false, null, "(.*a){16}", null);
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			nested.validate("x", "a".repeat(30) + "!")
		);
		assertEquals("The argument x takes too long to match its pattern", exception.getMessage());
		assertDoesNotThrow(() -> nested.validate("x", "a".repeat(16)));
	}

	@Test
	void testValidateBoundsThePatternRecursion() {
		// Each repetition of the group is a recursion: a long value overflows the stack before the timeout
		final ToolParameter recursive = new ToolParameter(null, null, false, null, "(x|y)*", null);
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			recursive.validate("x", "x".repeat(1_000_000))
		);
		assertEquals("The argument x is too long to match its pattern", exception.getMessage());
		assertDoesNotThrow(() -> recursive.validate("x", "xyxy"));
	}

	@Test
	void testNumericEnumComparesValues() {
		final ToolParameter level = new ToolParameter("integer", null, false, null, null, List.of("01", "2"));
		assertDoesNotThrow(() -> level.validate("level", "1"));
		assertThrows(IllegalArgumentException.class, () -> level.validate("level", "3"));
		final ToolParameter ratio = new ToolParameter("number", null, false, null, null, List.of("0.5"));
		assertDoesNotThrow(() -> ratio.validate("ratio", "0.50"));
	}

	@Test
	void testValidateTypes() {
		final ToolParameter integer = new ToolParameter("integer", null, false, null);
		assertDoesNotThrow(() -> integer.validate("n", "-42"));
		assertThrows(IllegalArgumentException.class, () -> integer.validate("n", "4.2"));
		assertThrows(IllegalArgumentException.class, () -> integer.validate("n", "10; reboot"));

		final ToolParameter number = new ToolParameter("number", null, false, null);
		assertDoesNotThrow(() -> number.validate("x", "4.2e-3"));
		assertThrows(IllegalArgumentException.class, () -> number.validate("x", "NaN"));

		final ToolParameter bool = new ToolParameter("boolean", null, false, null);
		assertDoesNotThrow(() -> bool.validate("b", "TRUE"));
		assertThrows(IllegalArgumentException.class, () -> bool.validate("b", "yes"));
	}

	@Test
	void testValidatePatternMatchesTheWholeValue() {
		final ToolParameter service = new ToolParameter(null, null, true, null, "[\\w@.-]+", null);
		assertDoesNotThrow(() -> service.validate("service", "sshd.service"));
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			service.validate("service", "sshd; reboot")
		);
		assertEquals("The argument service must match the pattern [\\w@.-]+", exception.getMessage());
	}

	@Test
	void testValidateEnum() {
		final ToolParameter signal = new ToolParameter(null, null, false, null, null, List.of("TERM", "KILL"));
		assertDoesNotThrow(() -> signal.validate("signal", "KILL"));
		assertThrows(IllegalArgumentException.class, () -> signal.validate("signal", "kill"));
	}

	@Test
	void testValidateRejectsMacros() {
		final ToolParameter target = new ToolParameter(null, null, false, null);
		assertDoesNotThrow(() -> target.validate("target", "$HOME and 100%"));
		// Expanded after the arguments are inserted: the password would end up in the output
		assertThrows(IllegalArgumentException.class, () -> target.validate("target", "%{PASSWORD}"));
		assertThrows(IllegalArgumentException.class, () -> target.validate("target", "${source::beforeAll.session}"));
	}

	@Test
	void testResolveArgumentsValidatesAndDropsUndeclaredArguments() {
		final Map<String, ToolParameter> parameters = new LinkedHashMap<>();
		parameters.put("lines", new ToolParameter("integer", null, false, "100"));
		parameters.put("service", new ToolParameter(null, null, true, null, "[\\w@.-]+", null));
		final ToolDefinition definition = new ToolDefinition("serviceLogs", null, parameters, false);

		final Map<String, String> arguments = new LinkedHashMap<>();
		arguments.put("service", "sshd");
		arguments.put("other", "$(reboot)");
		assertEquals(Map.of("lines", "100", "service", "sshd"), definition.resolveArguments(arguments));

		arguments.put("lines", "1 && reboot");
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			definition.resolveArguments(arguments)
		);
		assertEquals("The argument lines must be of type integer", exception.getMessage());
	}
}
