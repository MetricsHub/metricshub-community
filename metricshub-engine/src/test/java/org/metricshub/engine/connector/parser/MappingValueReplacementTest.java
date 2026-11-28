package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.awk.MappingExpression;

class MappingValueReplacementTest {

	private static final UnaryOperator<String> REPLACE_X = value -> value.replace("${constant::X}", "=x");

	@Test
	void isMappingValue() {
		assertTrue(MappingValueReplacement.isMappingValue("monitors.cpu.discovery.mapping.attributes.id"));
		assertTrue(
			MappingValueReplacement.isMappingValue(
				"monitors.cpu.discovery.mapping.metrics.hw.network.io{direction=\"receive\"}"
			)
		);
		assertTrue(MappingValueReplacement.isMappingValue("monitors.cpu.collect.mapping.conditionalCollection.a"));
		assertTrue(MappingValueReplacement.isMappingValue("monitors.cpu.simple.mapping.legacyTextParameters.a"));
		assertTrue(MappingValueReplacement.isMappingValue("resources.r1.monitors.cpu.simple.mapping.attributes.id"));
		assertTrue(
			MappingValueReplacement.isMappingValue("resourceGroups.g.resources.r1.monitors.cpu.simple.mapping.metrics.m")
		);

		assertFalse(MappingValueReplacement.isMappingValue("monitors.cpu.discovery.sources.s.computes[0].exclude"));
		assertFalse(MappingValueReplacement.isMappingValue("monitors.cpu.discovery.mapping.source"));
		assertFalse(MappingValueReplacement.isMappingValue("constants.X"));
		assertFalse(MappingValueReplacement.isMappingValue(null));
	}

	@Test
	void replace() {
		// An expression is never replaced
		assertEquals("= $1 * ${constant::X}", MappingValueReplacement.replace("= $1 * ${constant::X}", REPLACE_X));
		// A text is replaced
		assertEquals("a=x", MappingValueReplacement.replace("a${constant::X}", REPLACE_X));
		// A text that becomes "=..." stays a text
		assertEquals("= \"=x\"", MappingValueReplacement.replace("${constant::X}", REPLACE_X));
	}

	@Test
	void awkStringLiteralRoundTrip() {
		assertEquals("\"a\\\\b\\\"c\\n\\r\\t\"", MappingValueReplacement.awkStringLiteral("a\\b\"c\n\r\t"));
		assertEquals("\"a\\0001\"", MappingValueReplacement.awkStringLiteral("a\0" + "1"));
		for (final String text : new String[] { "=x", "=a\"b\\c", "=line1\nline2", "=a\0" + "1b" }) {
			final MappingExpression expression = MappingExpression.of(
				false,
				"= " + MappingValueReplacement.awkStringLiteral(text)
			);
			assertEquals(MappingExpression.Kind.CONSTANT, expression.getKind(), text);
			assertEquals(text, expression.getConstantText());
		}
	}
}
