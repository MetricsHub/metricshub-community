package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metricshub.jawk.util.AwkSettings;

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

	@Test
	void testJoin() {
		assertEquals("a/b/c/d", UNDER_TEST.join("/", "a", "b", "c", "d"), "Should join strings with '/' as separator");
		assertEquals("", UNDER_TEST.join("/"), "Should return empty string when no elements provided");
		assertEquals("a", UNDER_TEST.join("/", "a"), "Should return single element unchanged");
		assertEquals(
			"abcd",
			UNDER_TEST.join(null, "a", "b", "c", "d"),
			"Should concatenate without separator when null separator provided"
		);
		assertEquals("a-_-b", UNDER_TEST.join("-_-", "a", "b"), "Should use custom separator '-_-'");
		assertEquals("", UNDER_TEST.join(null), "Should return empty string when both separator and inputs are null");
	}

	@Test
	void testBase64Encode() {
		assertEquals("SGVsbG8gV29ybGQh", UNDER_TEST.base64Encode("Hello World!"), "Standard Base64 encoding");
		assertEquals("", UNDER_TEST.base64Encode(""), "Empty string should return empty string");
		assertEquals("", UNDER_TEST.base64Encode(null), "Null input should return empty string");
	}
}
