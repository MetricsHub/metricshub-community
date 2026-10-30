package org.metricshub.engine.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
	}

	@Test
	void testIsFreeText() {
		assertTrue(new ToolParameter(null, null, false, null).isFreeText());
		assertFalse(new ToolParameter("integer", null, false, null).isFreeText());
		assertFalse(new ToolParameter(null, null, false, null, "\\w+", null).isFreeText());
		assertFalse(new ToolParameter(null, null, false, null, null, List.of("a")).isFreeText());
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
