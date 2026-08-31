package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jawk.jrt.AssocArray;
import io.jawk.util.AwkSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UtilityExtensionForJawkTest {

	static final UtilityExtensionForJawk UNDER_TEST = new UtilityExtensionForJawk();

	@BeforeAll
	static void setup() {
		AwkSettings settings = AwkSettings.DEFAULT_SETTINGS;
		UNDER_TEST.init(null, null, settings);
	}

	@Test
	void testBytes2HumanFormatBase2() {
		assertEquals("1.00 B", UNDER_TEST.bytes2HumanFormatBase2(1), "Should correctly format 1 byte as 1.00 B");
		assertEquals("1.00 KiB", UNDER_TEST.bytes2HumanFormatBase2(1024), "Should correctly format 1024 bytes as 1.00 KiB");
		assertEquals("1.00 MiB", UNDER_TEST.bytes2HumanFormatBase2(1024 * 1024), "Should correctly format 1 MiB");
		assertEquals("3.10 MiB", UNDER_TEST.bytes2HumanFormatBase2(3.1 * 1024 * 1024), "Should correctly format 3.1 MiB");
		assertEquals(
			"3.14 MiB",
			UNDER_TEST.bytes2HumanFormatBase2(3.1415927 * 1024 * 1024),
			"Should round 3.1415927 MiB to 3.14 MiB"
		);
		assertEquals("2.00 KiB", UNDER_TEST.bytes2HumanFormatBase2("2048"), "Should correctly parse string input '2048'");
		assertEquals(
			"2.00 KiB",
			UNDER_TEST.bytes2HumanFormatBase2("   2048   "),
			"Should trim string input before parsing"
		);
		assertEquals("0.00 B", UNDER_TEST.bytes2HumanFormatBase2("abc"), "Should handle invalid string input gracefully");
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase2(""), "Should return empty string for empty input");
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase2(null), "Should return empty string for null input");
	}

	@Test
	void testBytes2HumanFormatBase10() {
		assertEquals("1.00 B", UNDER_TEST.bytes2HumanFormatBase10(1), "Should correctly format 1 byte in base 10");
		assertEquals("1.00 KB", UNDER_TEST.bytes2HumanFormatBase10(1000), "Should correctly format 1000 bytes as 1 KB");
		assertEquals("1.00 MB", UNDER_TEST.bytes2HumanFormatBase10(1000 * 1000), "Should correctly format 1 MB");
		assertEquals("3.10 MB", UNDER_TEST.bytes2HumanFormatBase10(3.1 * 1000 * 1000), "Should correctly format 3.1 MB");
		assertEquals(
			"3.14 MB",
			UNDER_TEST.bytes2HumanFormatBase10(3.1415927 * 1000 * 1000),
			"Should round 3.1415927 MB to 3.14 MB"
		);
		assertEquals("2.00 KB", UNDER_TEST.bytes2HumanFormatBase10("2000"), "Should correctly parse '2000' as 2 KB");
		assertEquals(
			"2.00 KB",
			UNDER_TEST.bytes2HumanFormatBase10("   2000   "),
			"Should trim string input for base10 format"
		);
		assertEquals("0.00 B", UNDER_TEST.bytes2HumanFormatBase10("abc"), "Should handle invalid string input gracefully");
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase10(""), "Should return empty string for empty input");
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase10(null), "Should return empty string for null input");
	}

	@Test
	void testMebiBytes2HumanFormat() {
		assertEquals("1.00 MiB", UNDER_TEST.mebiBytes2HumanFormat(1), "Should correctly format 1 MiB");
		assertEquals("1.00 GiB", UNDER_TEST.mebiBytes2HumanFormat(1024), "Should correctly format 1024 MiB as 1 GiB");
		assertEquals("1.00 TiB", UNDER_TEST.mebiBytes2HumanFormat(1024 * 1024), "Should correctly format 1 TiB");
		assertEquals("3.10 TiB", UNDER_TEST.mebiBytes2HumanFormat(3.1 * 1024 * 1024), "Should correctly format 3.1 TiB");
		assertEquals(
			"3.14 TiB",
			UNDER_TEST.mebiBytes2HumanFormat(3.1415927 * 1024 * 1024),
			"Should round 3.1415927 TiB to 3.14 TiB"
		);
		assertEquals("2.00 GiB", UNDER_TEST.mebiBytes2HumanFormat("2048"), "Should correctly parse '2048' MiB as 2 GiB");
		assertEquals("2.00 GiB", UNDER_TEST.mebiBytes2HumanFormat("   2048   "), "Should trim string input before parsing");
		assertEquals("0.00 MiB", UNDER_TEST.mebiBytes2HumanFormat("abc"), "Should handle invalid string input gracefully");
		assertEquals("", UNDER_TEST.mebiBytes2HumanFormat(""), "Should return empty string for empty input");
		assertEquals("", UNDER_TEST.mebiBytes2HumanFormat(null), "Should return empty string for null input");
	}

	@Test
	void testMegaHertz2HumanFormat() {
		assertEquals("1.00 MHz", UNDER_TEST.megaHertz2HumanFormat(1), "Should correctly format 1 MHz");
		assertEquals("1.00 GHz", UNDER_TEST.megaHertz2HumanFormat(1000), "Should correctly format 1000 MHz as 1 GHz");
		assertEquals("3.10 GHz", UNDER_TEST.megaHertz2HumanFormat(3100), "Should correctly format 3100 MHz as 3.10 GHz");
		assertEquals("3.14 GHz", UNDER_TEST.megaHertz2HumanFormat(3141.5927), "Should round 3141.5927 MHz to 3.14 GHz");
		assertEquals(
			"2.00 GHz",
			UNDER_TEST.megaHertz2HumanFormat("2000"),
			"Should correctly parse string input '2000' MHz"
		);
		assertEquals("2.00 GHz", UNDER_TEST.megaHertz2HumanFormat("   2000   "), "Should trim string input before parsing");
		assertEquals("0.00 MHz", UNDER_TEST.megaHertz2HumanFormat("abc"), "Should handle invalid string input gracefully");
		assertEquals("", UNDER_TEST.megaHertz2HumanFormat(""), "Should return empty string for empty input");
		assertEquals("", UNDER_TEST.megaHertz2HumanFormat(null), "Should return empty string for null input");
	}

	/**
	 * Driven through the AWK dispatch path rather than by calling the Java method directly: {@code join} converts its
	 * arguments with {@code toAwkString}, which needs the runtime a real interpreter supplies.
	 */
	@Test
	void testJoin() throws AwkException {
		assertEquals(
			"a/b/c/d",
			AwkExecutor.evalAwk("join(\"/\", \"a\", \"b\", \"c\", \"d\")", ""),
			"Should join strings with '/' as separator"
		);
		assertEquals("", AwkExecutor.evalAwk("join(\"/\")", ""), "Should return empty string when no elements provided");
		assertEquals("a", AwkExecutor.evalAwk("join(\"/\", \"a\")", ""), "Should return single element unchanged");
		assertEquals(
			"abcd",
			AwkExecutor.evalAwk("join(unset, \"a\", \"b\", \"c\", \"d\")", ""),
			"Should concatenate without separator when the separator is unset"
		);
		assertEquals("a-_-b", AwkExecutor.evalAwk("join(\"-_-\", \"a\", \"b\")", ""), "Should use custom separator '-_-'");
		assertEquals(
			"",
			AwkExecutor.evalAwk("join(unset)", ""),
			"Should return empty string when both separator and inputs are unset"
		);
	}

	/**
	 * Driven through the AWK dispatch path rather than by calling the Java method directly: {@code base64Encode}
	 * converts its argument with {@code toAwkString}, which needs the runtime a real interpreter supplies.
	 */
	@Test
	void testBase64Encode() throws AwkException {
		assertEquals(
			"SGVsbG8gV29ybGQh",
			AwkExecutor.evalAwk("base64Encode(\"Hello World!\")", ""),
			"Standard Base64 encoding"
		);
		assertEquals("", AwkExecutor.evalAwk("base64Encode(\"\")", ""), "Empty string should return empty string");
		assertEquals("", AwkExecutor.evalAwk("base64Encode(unset)", ""), "Unset input should return empty string");
	}

	@Test
	void testAsorti() {
		AssocArray src = AssocArray.create(false);
		src.put("z", 1);
		src.put("b", 1);
		src.put("a", 1);

		AssocArray dest = AssocArray.create(false);
		final int n = UNDER_TEST.asorti(src, dest);
		assertEquals(3, n, "Should return the number of keys");
		assertEquals("a", dest.get("1"), "First key should be 'a'");
		assertEquals("b", dest.get("2"), "Second key should be 'b'");
		assertEquals("z", dest.get("3"), "Third key should be 'z'");
	}

	/**
	 * Every function must also be callable <em>from an AWK script</em> with field arguments, not only from Java with
	 * String arguments. Jawk hands scalars over as its own runtime types, so a function declaring a
	 * <code>String</code> parameter fails with "argument type mismatch" on <code>$1</code> even though a direct Java
	 * call with a String succeeds.
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
