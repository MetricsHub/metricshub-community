package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jawk.intermediate.UninitializedObject;
import io.jawk.jrt.GawkBool;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.awk.MappingExpression.Kind;
import org.metricshub.engine.awk.MappingExpression.Problem;

class MappingExpressionTest {

	private static void assertInvalid(final boolean metrics, final String value, final Problem problem) {
		final MappingExpression expression = MappingExpression.of(metrics, value);
		assertEquals(Kind.INVALID, expression.getKind(), value);
		assertEquals(problem, expression.getProblem(), value);
	}

	@Test
	void isExpressionTestsTheFirstCharacter() {
		assertTrue(MappingExpression.isExpression("= $1"));
		assertTrue(MappingExpression.isExpression("=$1"));
		assertFalse(MappingExpression.isExpression(" = $1"));
		assertFalse(MappingExpression.isExpression("a=b"));
		assertFalse(MappingExpression.isExpression(""));
		assertFalse(MappingExpression.isExpression(null));
	}

	@Test
	void fieldFastPath() {
		final MappingExpression field = MappingExpression.of(false, "= $3");
		assertEquals(Kind.FIELD, field.getKind());
		assertEquals(3, field.getFieldIndex());
		assertEquals(" $3", field.getBody());
		assertEquals(Kind.FIELD, MappingExpression.of(false, "= $(3)").getKind());
		assertEquals(3, MappingExpression.of(false, "= $(3)").getFieldIndex());
		assertEquals(Kind.FIELD, MappingExpression.of(false, "=$3").getKind());
		assertEquals(Kind.EVAL, MappingExpression.of(false, "= $0").getKind());
	}

	@Test
	void constantFastPath() {
		assertConstant("= \"enclosure\"", "enclosure");
		assertConstant("= 42", "42");
		assertConstant("= 2 * 3", "6");
		assertConstant("= 010", "10");
		assertConstant("= 1/3", "0.3333333333333333");
		assertConstant("= \"\"", "");
	}

	private static void assertConstant(final String value, final String text) {
		final MappingExpression expression = MappingExpression.of(false, value);
		assertEquals(Kind.CONSTANT, expression.getKind(), value);
		assertEquals(text, expression.getConstantText(), value);
	}

	@Test
	void evalAndReferencedNames() {
		final MappingExpression product = MappingExpression.of(false, "= $1 * BLOCK_SIZE * scale");
		assertEquals(Kind.EVAL, product.getKind());
		assertEquals(Set.of("BLOCK_SIZE", "scale"), product.getReferencedNames());
		assertEquals(Set.of(), product.getArrayNames());

		assertEquals(Set.of(), MappingExpression.of(false, "= NF").getReferencedNames());

		final MappingExpression split = MappingExpression.of(false, "= split($1, a, \".\") ? a[1] : \"\"");
		assertEquals(Kind.EVAL, split.getKind());
		assertEquals(Set.of("a"), split.getReferencedNames());
		assertEquals(Set.of("a"), split.getArrayNames());
	}

	@Test
	void referenceIsRefused() {
		assertInvalid(false, "= ${source::monitors.m.discovery.sources.x} \" a\"", Problem.REFERENCE);
		assertInvalid(false, "= \"${constant::X}\"", Problem.REFERENCE);
	}

	@Test
	void environmentIsRefused() {
		assertInvalid(false, "= ENVIRON[\"PATH\"]", Problem.ENVIRON);
		assertInvalid(false, "= length(SYMTAB[\"ENV\" \"IRON\"])", Problem.ENVIRON);
	}

	@Test
	void compileProblems() {
		assertInvalid(false, "= $1 +", Problem.SYNTAX);
		assertInvalid(false, "= x = 5", Problem.SYNTAX);
		assertInvalid(false, "= foo($1)", Problem.UNKNOWN_FUNCTION);
		assertInvalid(false, "= megabit2bit($1)", Problem.UNKNOWN_FUNCTION);
		assertInvalid(false, "= megaBit2Bit($1, 2)", Problem.ARGUMENTS);
		assertInvalid(false, "= system(\"ls\")", Problem.SANDBOX);
	}

	@Test
	void metricsFunctionsOnlyUnderMetrics() {
		assertInvalid(false, "= rate($1)", Problem.METRICS_FUNCTION_OUTSIDE_METRICS);
		assertEquals(Kind.EVAL, MappingExpression.of(true, "= rate($1) * 8").getKind());
	}

	@Test
	void wholeValueRule() {
		assertWholeValue("= fakeCounter($5 / 1000)", "fakeCounter");
		assertWholeValue("= fakeCounter($1 > 0 ? $1 : 0)", "fakeCounter");
		assertWholeValue("= fakeCounter(legacyPowerSupplyUtilization($1))", "fakeCounter");
		assertWholeValue("= accumulate($2)", "accumulate");
		assertWholeValue("= computePowerShareRatio($4)", "computePowerShareRatio");

		assertNotWholeValue("= fakeCounter($5) / 1000", "fakeCounter");
		assertNotWholeValue("= (fakeCounter($5))", "fakeCounter");
		assertNotWholeValue("= $1 ? accumulate($2) : 0", "accumulate");
		assertNotWholeValue("= fakeCounter(rate($5))", "fakeCounter");
		assertNotWholeValue("= computePowerShareRatio($4) * 2", "computePowerShareRatio");
		assertNotWholeValue("= fakeCounter($1) + fakeCounter($2)", "fakeCounter");
		assertNotWholeValue("= ($1 ~ /\"/) + fakeCounter($2) + ($3 ~ /\"/)", "fakeCounter");

		final MappingExpression text = MappingExpression.of(true, "= \"fakeCounter(\" $1");
		assertEquals(Kind.EVAL, text.getKind());
		assertNull(text.getFunction());
		final MappingExpression rate = MappingExpression.of(true, "= rate($1) * 8");
		assertEquals(Kind.EVAL, rate.getKind());
		assertNull(rate.getFunction());
	}

	private static void assertWholeValue(final String value, final String function) {
		final MappingExpression expression = MappingExpression.of(true, value);
		assertEquals(Kind.EVAL, expression.getKind(), value);
		assertEquals(function, expression.getFunction(), value);
	}

	private static void assertNotWholeValue(final String value, final String function) {
		final MappingExpression expression = MappingExpression.of(true, value);
		assertEquals(Kind.INVALID, expression.getKind(), value);
		assertEquals(Problem.NOT_WHOLE_VALUE, expression.getProblem(), value);
		assertEquals(function, expression.getFunction(), value);
	}

	@Test
	void cacheKeyedBySectionKindAndText() {
		assertSame(MappingExpression.of(false, "= $1 * 2"), MappingExpression.of(false, "= $1 * 2"));
		assertSame(MappingExpression.of(true, "= $1 * 2"), MappingExpression.of(true, "= $1 * 2"));
		assertEquals(Kind.EVAL, MappingExpression.of(true, "= rate($1)").getKind());
		assertEquals(Kind.INVALID, MappingExpression.of(false, "= rate($1)").getKind());
		assertSame(MappingExpression.of(false, "= rate($1)"), MappingExpression.of(false, "= rate($1)"));
	}

	@Test
	void toTextRule() {
		assertNull(MappingExpression.toText(null));
		assertEquals("abc", MappingExpression.toText("abc"));
		assertEquals("125000000", MappingExpression.toText(125000000.0));
		assertEquals("0.5", MappingExpression.toText(0.5));
		assertEquals("1.0E-5", MappingExpression.toText(1.0E-5));
		assertEquals("0", MappingExpression.toText(-0.0));
		assertEquals("1.0E30", MappingExpression.toText(1e30));
		assertEquals("NaN", MappingExpression.toText(Double.NaN));
		assertEquals("Infinity", MappingExpression.toText(Double.POSITIVE_INFINITY));
		assertEquals("-Infinity", MappingExpression.toText(Double.NEGATIVE_INFINITY));
		assertEquals("3", MappingExpression.toText(3.0f));
		assertEquals("1", MappingExpression.toText(Integer.valueOf(1)));
		assertEquals("42", MappingExpression.toText(Long.valueOf(42)));
		assertEquals("1", MappingExpression.toText(new GawkBool(true)));
		assertEquals("", MappingExpression.toText(new UninitializedObject()));
	}

	@Test
	void withoutStringLiterals() {
		assertEquals(" $2", MappingExpression.withoutStringLiterals("\"a$1b\" $2"));
		assertEquals(" $3", MappingExpression.withoutStringLiterals("\"a\\\"$1\" $3"));
	}
}
