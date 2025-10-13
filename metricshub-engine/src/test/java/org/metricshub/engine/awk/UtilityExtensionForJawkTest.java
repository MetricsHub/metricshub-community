package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.*;

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
		assertEquals("1.00 B", UNDER_TEST.bytes2HumanFormatBase2(1));
		assertEquals("1.00 KiB", UNDER_TEST.bytes2HumanFormatBase2(1024));
		assertEquals("1.00 MiB", UNDER_TEST.bytes2HumanFormatBase2(1024 * 1024));
		assertEquals("3.10 MiB", UNDER_TEST.bytes2HumanFormatBase2(3.1 * 1024 * 1024));
		assertEquals("3.14 MiB", UNDER_TEST.bytes2HumanFormatBase2(3.1415927 * 1024 * 1024));
		assertEquals("2.00 KiB", UNDER_TEST.bytes2HumanFormatBase2("2048"));
		assertEquals("2.00 KiB", UNDER_TEST.bytes2HumanFormatBase2("   2048   "));
		assertEquals("0.00 B", UNDER_TEST.bytes2HumanFormatBase2("abc"));
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase2(""));
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase2(null));
	}

	@Test
	void testBytes2HumanFormatBase10() {
		assertEquals("1.00 B", UNDER_TEST.bytes2HumanFormatBase10(1));
		assertEquals("1.00 KB", UNDER_TEST.bytes2HumanFormatBase10(1000));
		assertEquals("1.00 MB", UNDER_TEST.bytes2HumanFormatBase10(1000 * 1000));
		assertEquals("3.10 MB", UNDER_TEST.bytes2HumanFormatBase10(3.1 * 1000 * 1000));
		assertEquals("3.14 MB", UNDER_TEST.bytes2HumanFormatBase10(3.1415927 * 1000 * 1000));
		assertEquals("2.00 KB", UNDER_TEST.bytes2HumanFormatBase10("2000"));
		assertEquals("2.00 KB", UNDER_TEST.bytes2HumanFormatBase10("   2000   "));
		assertEquals("0.00 B", UNDER_TEST.bytes2HumanFormatBase10("abc"));
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase10(""));
		assertEquals("", UNDER_TEST.bytes2HumanFormatBase10(null));
	}

	@Test
	void testMebiBytes2HumanFormat() {
		assertEquals("1.00 MiB", UNDER_TEST.mebiBytes2HumanFormat(1));
		assertEquals("1.00 GiB", UNDER_TEST.mebiBytes2HumanFormat(1024));
		assertEquals("1.00 TiB", UNDER_TEST.mebiBytes2HumanFormat(1024 * 1024));
		assertEquals("3.10 TiB", UNDER_TEST.mebiBytes2HumanFormat(3.1 * 1024 * 1024));
		assertEquals("3.14 TiB", UNDER_TEST.mebiBytes2HumanFormat(3.1415927 * 1024 * 1024));
		assertEquals("2.00 GiB", UNDER_TEST.mebiBytes2HumanFormat("2048"));
		assertEquals("2.00 GiB", UNDER_TEST.mebiBytes2HumanFormat("   2048   "));
		assertEquals("0.00 MiB", UNDER_TEST.mebiBytes2HumanFormat("abc"));
		assertEquals("", UNDER_TEST.mebiBytes2HumanFormat(""));
		assertEquals("", UNDER_TEST.mebiBytes2HumanFormat(null));
	}

	@Test
	void testMegaHertz2HumanFormat() {
		assertEquals("1.00 MHz", UNDER_TEST.megaHertz2HumanFormat(1));
		assertEquals("1.00 GHz", UNDER_TEST.megaHertz2HumanFormat(1000));
		assertEquals("3.10 GHz", UNDER_TEST.megaHertz2HumanFormat(3100));
		assertEquals("3.14 GHz", UNDER_TEST.megaHertz2HumanFormat(3141.5927));
		assertEquals("2.00 GHz", UNDER_TEST.megaHertz2HumanFormat("2000"));
		assertEquals("2.00 GHz", UNDER_TEST.megaHertz2HumanFormat("   2000   "));
		assertEquals("0.00 MHz", UNDER_TEST.megaHertz2HumanFormat("abc"));
		assertEquals("", UNDER_TEST.megaHertz2HumanFormat(""));
		assertEquals("", UNDER_TEST.megaHertz2HumanFormat(null));
	}

	@Test
	void testJoin() {
		assertEquals("a/b/c/d", UNDER_TEST.join("/", "a", "b", "c", "d"));
		assertEquals("", UNDER_TEST.join("/"));
		assertEquals("a", UNDER_TEST.join("/", "a"));
		assertEquals("abcd", UNDER_TEST.join(null, "a", "b", "c", "d"));
		assertEquals("a-_-b", UNDER_TEST.join("-_-", "a", "b"));
		assertEquals("", UNDER_TEST.join(null));
	}
}
