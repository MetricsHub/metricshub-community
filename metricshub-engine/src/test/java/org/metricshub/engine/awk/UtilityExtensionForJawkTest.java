package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The MetricsHub utility functions, called from AWK expressions: an extension function reads the running script's
 * runtime (its settings, its number formatting), which only exists while Jawk runs a script.
 */
class UtilityExtensionForJawkTest {

	/**
	 * Evaluates an AWK expression with the MetricsHub engines.
	 *
	 * @param expression The AWK expression
	 * @return its value as text
	 */
	private static String eval(final String expression) throws AwkException {
		return AwkExecutor.evalAwk(expression, "");
	}

	@Test
	void testBytes2HumanFormatBase2() throws AwkException {
		assertEquals("1.00 B", eval("bytes2HumanFormatBase2(1)"), "Should correctly format 1 byte as 1.00 B");
		assertEquals("1.00 KiB", eval("bytes2HumanFormatBase2(1024)"), "Should correctly format 1024 bytes as 1.00 KiB");
		assertEquals("1.00 MiB", eval("bytes2HumanFormatBase2(1024 * 1024)"), "Should correctly format 1 MiB");
		assertEquals("3.10 MiB", eval("bytes2HumanFormatBase2(3.1 * 1024 * 1024)"), "Should correctly format 3.1 MiB");
		assertEquals(
			"3.14 MiB",
			eval("bytes2HumanFormatBase2(3.1415927 * 1024 * 1024)"),
			"Should round 3.1415927 MiB to 3.14 MiB"
		);
		assertEquals("2.00 KiB", eval("bytes2HumanFormatBase2(\"2048\")"), "Should correctly parse string input '2048'");
		assertEquals("2.00 KiB", eval("bytes2HumanFormatBase2(\"   2048   \")"), "Should trim string input before parsing");
		assertEquals("0.00 B", eval("bytes2HumanFormatBase2(\"abc\")"), "Should handle invalid string input gracefully");
		assertEquals("", eval("bytes2HumanFormatBase2(\"\")"), "Should return empty string for empty input");
		assertEquals("", eval("bytes2HumanFormatBase2(unset)"), "Should return empty string for unset input");
	}

	@Test
	void testBytes2HumanFormatBase10() throws AwkException {
		assertEquals("1.00 B", eval("bytes2HumanFormatBase10(1)"), "Should correctly format 1 byte in base 10");
		assertEquals("1.00 KB", eval("bytes2HumanFormatBase10(1000)"), "Should correctly format 1000 bytes as 1 KB");
		assertEquals("1.00 MB", eval("bytes2HumanFormatBase10(1000 * 1000)"), "Should correctly format 1 MB");
		assertEquals("3.10 MB", eval("bytes2HumanFormatBase10(3.1 * 1000 * 1000)"), "Should correctly format 3.1 MB");
		assertEquals(
			"3.14 MB",
			eval("bytes2HumanFormatBase10(3.1415927 * 1000 * 1000)"),
			"Should round 3.1415927 MB to 3.14 MB"
		);
		assertEquals("2.00 KB", eval("bytes2HumanFormatBase10(\"2000\")"), "Should correctly parse '2000' as 2 KB");
		assertEquals(
			"2.00 KB",
			eval("bytes2HumanFormatBase10(\"   2000   \")"),
			"Should trim string input for base10 format"
		);
		assertEquals("0.00 B", eval("bytes2HumanFormatBase10(\"abc\")"), "Should handle invalid string input gracefully");
		assertEquals("", eval("bytes2HumanFormatBase10(\"\")"), "Should return empty string for empty input");
		assertEquals("", eval("bytes2HumanFormatBase10(unset)"), "Should return empty string for unset input");
	}

	@Test
	void testMebiBytes2HumanFormat() throws AwkException {
		assertEquals("1.00 MiB", eval("mebiBytes2HumanFormat(1)"), "Should correctly format 1 MiB");
		assertEquals("1.00 GiB", eval("mebiBytes2HumanFormat(1024)"), "Should correctly format 1024 MiB as 1 GiB");
		assertEquals("1.00 TiB", eval("mebiBytes2HumanFormat(1024 * 1024)"), "Should correctly format 1 TiB");
		assertEquals("3.10 TiB", eval("mebiBytes2HumanFormat(3.1 * 1024 * 1024)"), "Should correctly format 3.1 TiB");
		assertEquals(
			"3.14 TiB",
			eval("mebiBytes2HumanFormat(3.1415927 * 1024 * 1024)"),
			"Should round 3.1415927 TiB to 3.14 TiB"
		);
		assertEquals("2.00 GiB", eval("mebiBytes2HumanFormat(\"2048\")"), "Should correctly parse '2048' MiB as 2 GiB");
		assertEquals("2.00 GiB", eval("mebiBytes2HumanFormat(\"   2048   \")"), "Should trim string input before parsing");
		assertEquals("0.00 MiB", eval("mebiBytes2HumanFormat(\"abc\")"), "Should handle invalid string input gracefully");
		assertEquals("", eval("mebiBytes2HumanFormat(\"\")"), "Should return empty string for empty input");
		assertEquals("", eval("mebiBytes2HumanFormat(unset)"), "Should return empty string for unset input");
	}

	@Test
	void testMegaHertz2HumanFormat() throws AwkException {
		assertEquals("1.00 MHz", eval("megaHertz2HumanFormat(1)"), "Should correctly format 1 MHz");
		assertEquals("1.00 GHz", eval("megaHertz2HumanFormat(1000)"), "Should correctly format 1000 MHz as 1 GHz");
		assertEquals("3.10 GHz", eval("megaHertz2HumanFormat(3100)"), "Should correctly format 3100 MHz as 3.10 GHz");
		assertEquals("3.14 GHz", eval("megaHertz2HumanFormat(3141.5927)"), "Should round 3141.5927 MHz to 3.14 GHz");
		assertEquals("2.00 GHz", eval("megaHertz2HumanFormat(\"2000\")"), "Should correctly parse string input '2000' MHz");
		assertEquals("2.00 GHz", eval("megaHertz2HumanFormat(\"   2000   \")"), "Should trim string input before parsing");
		assertEquals("0.00 MHz", eval("megaHertz2HumanFormat(\"abc\")"), "Should handle invalid string input gracefully");
		assertEquals("", eval("megaHertz2HumanFormat(\"\")"), "Should return empty string for empty input");
		assertEquals("", eval("megaHertz2HumanFormat(unset)"), "Should return empty string for unset input");
	}

	@Test
	void testJoin() throws AwkException {
		assertEquals(
			"a/b/c/d",
			eval("join(\"/\", \"a\", \"b\", \"c\", \"d\")"),
			"Should join strings with '/' as separator"
		);
		assertEquals("", eval("join(\"/\")"), "Should return empty string when no elements provided");
		assertEquals("a", eval("join(\"/\", \"a\")"), "Should return single element unchanged");
		assertEquals(
			"abcd",
			eval("join(unset, \"a\", \"b\", \"c\", \"d\")"),
			"Should concatenate without separator when the separator is unset"
		);
		assertEquals("a-_-b", eval("join(\"-_-\", \"a\", \"b\")"), "Should use custom separator '-_-'");
		assertEquals("", eval("join(unset)"), "Should return empty string when both separator and inputs are unset");
	}

	@Test
	void testBase64Encode() throws AwkException {
		assertEquals("SGVsbG8gV29ybGQh", eval("base64Encode(\"Hello World!\")"), "Standard Base64 encoding");
		assertEquals("", eval("base64Encode(\"\")"), "Empty string should return empty string");
		assertEquals("", eval("base64Encode(unset)"), "Unset input should return empty string");
	}

	/**
	 * The engines of AwkExecutor (scripts and expressions) load Jawk's gawk functions next to the MetricsHub ones.
	 */
	@Test
	void testGawkFunctionsAreAvailable() throws AwkException {
		assertEquals(
			"2 a b\n",
			AwkExecutor.executeAwk("BEGIN { h[\"b\"]; h[\"a\"]; n = asorti(h); print n, h[1], h[2] }", "")
		);
		assertEquals("bxx", AwkExecutor.evalAwk("gensub(/a/, \"x\", \"g\", $1)", "baa;x"));
		assertEquals("31", AwkExecutor.evalAwk("strtonum($1)", "0x1F;x"));
		assertEquals("1970-01-01", eval("strftime(\"%Y-%m-%d\", 0, 1)"));
	}

	/**
	 * Every function must also be callable <em>from an AWK script</em> with field arguments.
	 */
	@Test
	void testFunctionsAreCallableWithAwkFields() throws AwkException {
		assertEquals("1.00 GiB", AwkExecutor.evalAwk("bytes2HumanFormatBase2($1)", "1073741824;x"));
		assertEquals("1.00 GB", AwkExecutor.evalAwk("bytes2HumanFormatBase10($1)", "1000000000;x"));
		assertEquals("1.00 TiB", AwkExecutor.evalAwk("mebiBytes2HumanFormat($1)", "1048576;x"));
		assertEquals("4.00 GHz", AwkExecutor.evalAwk("megaHertz2HumanFormat($1)", "4000;x"));
		assertEquals("a b c", AwkExecutor.evalAwk("join(\" \", $1, $2, $3)", "a;b;c"));
		assertEquals("aGVsbG8=", AwkExecutor.evalAwk("base64Encode($1)", "hello;x"));
	}
}
