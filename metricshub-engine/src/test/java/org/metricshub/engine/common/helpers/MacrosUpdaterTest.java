package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link MacrosUpdater} utility class.
 *
 * <p>This test class verifies that macros within a text string are correctly
 * replaced with their corresponding values, including proper escaping for various contexts
 * such as JSON, XML, SQL, etc.
 * </p>
 */
class MacrosUpdaterTest {

	private static final String PASSWORD_BASE64_MACRO = "%{PASSWORD_BASE64}";
	private static final String BASIC_AUTH_BASE64_MACRO = "%{BASIC_AUTH_BASE64}";
	private static final String SHA256_AUTH_MACRO = "%{SHA256_AUTH}";

	// Escaped macros
	private static final String USERNAME_ESC_JSON = "%{esc(json)::USERNAME}";
	private static final String PASSWORD_ESC_JSON = "%{esc(json)::PASSWORD}";
	private static final String USERNAME_ESC_XML = "%{esc(xml)::USERNAME}";
	private static final String PASSWORD_ESC_SQL = "%{esc(sql)::PASSWORD}";
	private static final String USERNAME_ESC_URL = "${esc(url)::USERNAME}";
	private static final String PASSWORD_ESC_URL = "%{esc(url)::PASSWORD}";
	private static final String USERNAME_ESC_REGEX = "%{esc(regex)::USERNAME}";
	private static final String PASSWORD_ESC_REGEX = "%{esc(regex)::PASSWORD}";
	private static final String USERNAME_ESC_WINDOWS_CMD = "%{esc(windows)::USERNAME}";
	private static final String PASSWORD_ESC_WINDOWS_CMD = "%{esc(windows)::PASSWORD}";
	private static final String USERNAME_ESC_POWERSHELL = "%{esc(powershell)::USERNAME}";
	private static final String PASSWORD_ESC_POWERSHELL = "%{esc(powershell)::PASSWORD}";
	private static final String USERNAME_ESC_BASH = "%{esc(bash)::USERNAME}";
	private static final String PASSWORD_ESC_BASH = "%{esc(bash)::PASSWORD}";
	private static final String PASSWORD_BASE64_JSON = "%{esc(json)::PASSWORD_BASE64}";
	private static final String PASSWORD_BASE64_XML = "%{esc(xml)::PASSWORD_BASE64}";
	private static final String PASSWORD_BASE64_SQL = "%{esc(sql)::PASSWORD_BASE64}";
	private static final String PASSWORD_BASE64_URL = "%{esc(url)::PASSWORD_BASE64}";
	private static final String PASSWORD_BASE64_REGEX = "%{esc(regex)::PASSWORD_BASE64}";
	private static final String PASSWORD_BASE64_WINDOWS_CMD = "%{esc(windows)::PASSWORD_BASE64}";
	private static final String PASSWORD_BASE64_POWERSHELL = "%{esc(powershell)::PASSWORD_BASE64}";
	private static final String PASSWORD_BASE64_BASH = "%{esc(bash)::PASSWORD_BASE64}";
	private static final String BASIC_AUTH_BASE64_JSON = "%{esc(json)::BASIC_AUTH_BASE64}";
	private static final String BASIC_AUTH_BASE64_XML = "%{esc(xml)::BASIC_AUTH_BASE64}";
	private static final String BASIC_AUTH_BASE64_SQL = "%{esc(sql)::BASIC_AUTH_BASE64}";
	private static final String BASIC_AUTH_BASE64_URL = "%{esc(url)::BASIC_AUTH_BASE64}";
	private static final String BASIC_AUTH_BASE64_REGEX = "%{esc(regex)::BASIC_AUTH_BASE64}";
	private static final String BASIC_AUTH_BASE64_WINDOWS_CMD = "%{esc(windows)::BASIC_AUTH_BASE64}";
	private static final String BASIC_AUTH_BASE64_POWERSHELL = "%{esc(powershell)::BASIC_AUTH_BASE64}";
	private static final String BASIC_AUTH_BASE64_BASH = "%{esc(bash)::BASIC_AUTH_BASE64}";
	private static final String SHA256_AUTH_JSON = "%{esc(json)::SHA256_AUTH}";
	private static final String SHA256_AUTH_XML = "%{esc(xml)::SHA256_AUTH}";
	private static final String SHA256_AUTH_SQL = "%{esc(sql)::SHA256_AUTH}";
	private static final String SHA256_AUTH_URL = "%{esc(url)::SHA256_AUTH}";
	private static final String SHA256_AUTH_REGEX = "%{esc(regex)::SHA256_AUTH}";
	private static final String SHA256_AUTH_WINDOWS_CMD = "%{esc(windows)::SHA256_AUTH}";
	private static final String SHA256_AUTH_POWERSHELL = "%{esc(powershell)::SHA256_AUTH}";
	private static final String SHA256_AUTH_BASH = "%{esc(bash)::SHA256_AUTH}";
	private static final String AUTHENTICATION_TOKEN_JSON = "%{esc(json)::AUTHENTICATIONTOKEN}";
	private static final String AUTHENTICATION_TOKEN_XML = "%{esc(xml)::AUTHENTICATIONTOKEN}";
	private static final String AUTHENTICATION_TOKEN_SQL = "%{esc(sql)::AUTHENTICATIONTOKEN}";
	private static final String AUTHENTICATION_TOKEN_URL = "%{esc(url)::AUTHENTICATIONTOKEN}";
	private static final String AUTHENTICATION_TOKEN_REGEX = "%{esc(regex)::AUTHENTICATIONTOKEN}";
	private static final String AUTHENTICATION_TOKEN_WINDOWS_CMD = "%{esc(windows)::AUTHENTICATIONTOKEN}";
	private static final String AUTHENTICATION_TOKEN_POWERSHELL = "%{esc(powershell)::AUTHENTICATIONTOKEN}";
	private static final String AUTHENTICATION_TOKEN_BASH = "%{esc(bash)::AUTHENTICATIONTOKEN}";

	// Macros

	/**
	 * Username macro
	 */
	public static final String USERNAME_MACRO = "%{USERNAME}";
	/**
	 * Password macro
	 */
	public static final String PASSWORD_MACRO = "%{PASSWORD}";
	/**
	 * Hostname macro
	 */
	public static final String HOSTNAME_MACRO = "%{HOSTNAME}";

	/**
	 * Tests the primary macro replacement functionality, including basic macros and some escaped macros.
	 */
	@Test
	void testUpdate() {
		final String text = String.format(
			"""
			%s
			%s
			%s
			%s
			%s
			%s
			%s
			%s
			""",
			HOSTNAME_MACRO,
			PASSWORD_MACRO,
			USERNAME_MACRO,
			BASIC_AUTH_BASE64_MACRO,
			SHA256_AUTH_MACRO,
			PASSWORD_BASE64_MACRO,
			USERNAME_ESC_JSON,
			PASSWORD_ESC_JSON
		);

		final String result = MacrosUpdater.update(text, "user", "pwd".toCharArray(), "token", "hostname", false);
		final String expected =
			"""
			hostname
			pwd
			user
			dXNlcjpwd2Q=
			3c469e9d6c5875d37a43f353d4f88e61fcf812c66eee3457465a40b0da4153e0
			cHdk
			user
			pwd
			""";
		assertEquals(expected, result);
	}

	/**
	 * Tests the escaping of special characters in JSON strings.
	 */
	@Test
	void testEscapeJsonSpecialCharacters() {
		assertEquals("test\\\"test", MacrosUpdater.escapeJsonSpecialCharacters("test\"test"));
		assertEquals("test\\ttest", MacrosUpdater.escapeJsonSpecialCharacters("test\ttest"));
		assertEquals("test\\ntest", MacrosUpdater.escapeJsonSpecialCharacters("test\ntest"));
		assertEquals("test\\rtest", MacrosUpdater.escapeJsonSpecialCharacters("test\rtest"));
		assertEquals("te\\\\st", MacrosUpdater.escapeJsonSpecialCharacters("te\\st"));
	}

	/**
	 * Tests the escaping of special characters in URLs.
	 */
	@Test
	void testEscapeUrlSpecialCharacters() {
		assertEquals("Hello%20World", MacrosUpdater.escapeUrlSpecialCharacters("Hello World"));
		assertEquals("test%40example.com", MacrosUpdater.escapeUrlSpecialCharacters("test@example.com"));
		assertEquals("%24%26%2F%3F", MacrosUpdater.escapeUrlSpecialCharacters("$&/?"));
		assertEquals("%7Bfoo%7D", MacrosUpdater.escapeUrlSpecialCharacters("{foo}"));
		assertEquals("%7E%23", MacrosUpdater.escapeUrlSpecialCharacters("~#"));
	}

	/**
	 * Tests the escaping of special characters in regular expressions.
	 */
	@Test
	void testEscapeRegexSpecialCharacters() {
		// Test escaping of common regex characters
		assertEquals("\\Q.\\E", MacrosUpdater.escapeRegexSpecialCharacters("."));
		assertEquals("\\Q^\\E", MacrosUpdater.escapeRegexSpecialCharacters("^"));
		assertEquals("\\Q$\\E", MacrosUpdater.escapeRegexSpecialCharacters("$"));
		assertEquals("\\Q*\\E", MacrosUpdater.escapeRegexSpecialCharacters("*"));
		assertEquals("\\Q+\\E", MacrosUpdater.escapeRegexSpecialCharacters("+"));
		assertEquals("\\Q?\\E", MacrosUpdater.escapeRegexSpecialCharacters("?"));
		assertEquals("\\Q{\\E", MacrosUpdater.escapeRegexSpecialCharacters("{"));
		assertEquals("\\Q}\\E", MacrosUpdater.escapeRegexSpecialCharacters("}"));
		assertEquals("\\Q[\\E", MacrosUpdater.escapeRegexSpecialCharacters("["));
		assertEquals("\\Q]\\E", MacrosUpdater.escapeRegexSpecialCharacters("]"));
		assertEquals("\\Q(\\E", MacrosUpdater.escapeRegexSpecialCharacters("("));
		assertEquals("\\Q)\\E", MacrosUpdater.escapeRegexSpecialCharacters(")"));
		assertEquals("\\Q|\\E", MacrosUpdater.escapeRegexSpecialCharacters("|"));
		assertEquals("\\Q\\\\E", MacrosUpdater.escapeRegexSpecialCharacters("\\"));
		assertEquals("\\Q/\\E", MacrosUpdater.escapeRegexSpecialCharacters("/"));

		// Test a combined string with multiple special characters
		assertEquals("\\Q(a*b+c)\\E", MacrosUpdater.escapeRegexSpecialCharacters("(a*b+c)"));

		// Test string with no special regex characters (should remain unchanged)
		assertEquals("\\Qabc123\\E", MacrosUpdater.escapeRegexSpecialCharacters("abc123"));

		// Test an empty string input (should remain unchanged)
		assertEquals("\\Q\\E", MacrosUpdater.escapeRegexSpecialCharacters(""));

		// Test complex string containing both escaped special characters and normal characters
		assertEquals("\\Qabc(def)[ghi].\\E", MacrosUpdater.escapeRegexSpecialCharacters("abc(def)[ghi]."));
	}

	/**
	 * Tests the escaping of special characters in XML strings.
	 */
	@Test
	void testEscapeXmlSpecialCharacters() {
		assertEquals("&lt;tag&gt;", MacrosUpdater.escapeXmlSpecialCharacters("<tag>"));
		assertEquals("Fish &amp; Chips", MacrosUpdater.escapeXmlSpecialCharacters("Fish & Chips"));
		assertEquals("&quot;Hello&quot;", MacrosUpdater.escapeXmlSpecialCharacters("\"Hello\""));
		assertEquals("It&apos;s mine", MacrosUpdater.escapeXmlSpecialCharacters("It's mine"));
	}

	/**
	 * Tests the escaping of special characters in Windows CMD strings.
	 */
	@Test
	void testEscapeWindowsCmdSpecialCharacters() {
		assertEquals("^&^|^<^>", MacrosUpdater.escapeWindowsCmdSpecialCharacters("&|<>"));
		assertEquals("Hello ^^World", MacrosUpdater.escapeWindowsCmdSpecialCharacters("Hello ^World"));
		assertEquals("foo^(bar^)", MacrosUpdater.escapeWindowsCmdSpecialCharacters("foo(bar)"));
		assertEquals("^\"Hello^\"", MacrosUpdater.escapeWindowsCmdSpecialCharacters("\"Hello\""));
	}

	/**
	 * Tests the escaping of special characters in PowerShell strings.
	 */
	@Test
	void testEscapePowershellSpecialCharacters() {
		assertEquals(
			"This is a test with `\"double quotes`\" and `$variables`.",
			MacrosUpdater.escapePowershellSpecialCharacters("This is a test with \"double quotes\" and $variables.")
		);
		assertEquals(
			"Escaped characters: ``n, ``t, ``r, `$, `{, `}, `[, `], `#, and ``0`.",
			MacrosUpdater.escapePowershellSpecialCharacters("Escaped characters: `n, `t, `r, $, {, }, [, ], #, and `0.")
		);
		assertEquals(
			"No special characters here",
			MacrosUpdater.escapePowershellSpecialCharacters("No special characters here")
		);
	}

	/**
	 * Tests the escaping of special characters in Bash strings.
	 */
	@Test
	void testEscapeBashSpecialCharacters() {
		assertEquals("\\$\\!\\*", MacrosUpdater.escapeBashSpecialCharacters("$!*"));
		assertEquals("\\&\\|\\<\\>", MacrosUpdater.escapeBashSpecialCharacters("&|<>"));
		assertEquals("\\\\'Hello\\\\'", MacrosUpdater.escapeBashSpecialCharacters("'Hello'"));
		assertEquals("\\\\\"Hello\\\\\"", MacrosUpdater.escapeBashSpecialCharacters("\"Hello\""));
	}

	/**
	 * Tests the escaping of special characters in SQL strings.
	 */
	@Test
	void testEscapeSqlSpecialCharacters() {
		assertEquals("O''Reilly", MacrosUpdater.escapeSqlSpecialCharacters("O'Reilly"));
		assertEquals("Line\\nBreak", MacrosUpdater.escapeSqlSpecialCharacters("Line\nBreak"));
		assertEquals("Column\\tTab", MacrosUpdater.escapeSqlSpecialCharacters("Column\tTab"));
		assertEquals("\\\\\"Quote\\\\\"", MacrosUpdater.escapeSqlSpecialCharacters("\"Quote\""));
	}

	/**
	 * Tests the replacement of the AUTHENTICATION_TOKEN macros with various escape types.
	 */
	@Test
	void testUpdateWithAuthenticationTokenEscapes() {
		// Complex authentication token example
		final String authToken = "auth@token#123$%^&*()_+|~`";

		// Expected values for different escape types
		final String expectedJson = "auth@token#123$%^&*()_+|~`"; // JSON escape
		final String expectedXml = "auth@token#123$%^&amp;*()_+|~`"; // XML escape
		final String expectedSql = "auth@token#123$%^&*()_+|~`"; // SQL escape (no change needed)
		final String expectedUrl = "auth%40token%23123%24%25%5E%26*%28%29_%2B%7C%7E%60"; // URL-encoded
		final String expectedRegex = "\\Qauth@token#123$%^&*()_+|~`\\E"; // Regex escape
		final String expectedWindowsCmd = "auth@token#123$^%^^^&*^(^)_+^|~`"; // Windows CMD escape
		final String expectedPowershell = "auth@token`#123`$%^&*`(`)_+|~`"; // PowerShell escape
		final String expectedBash = "auth@token#123\\$%^\\&\\*\\(\\)_+\\|\\~`"; // Bash escape

		// Test JSON escape
		{
			final String text = AUTHENTICATION_TOKEN_JSON;
			assertEquals(expectedJson, MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false));
		}

		// Test XML escape
		{
			final String text = AUTHENTICATION_TOKEN_XML;
			assertEquals(expectedXml, MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false));
		}

		// Test SQL escape
		{
			final String text = AUTHENTICATION_TOKEN_SQL;
			assertEquals(expectedSql, MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false));
		}

		// Test URL escape
		{
			final String text = AUTHENTICATION_TOKEN_URL;
			assertEquals(expectedUrl, MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false));
		}

		// Test Regex escape
		{
			final String text = AUTHENTICATION_TOKEN_REGEX;
			assertEquals(
				expectedRegex,
				MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false)
			);
		}

		// Test Windows CMD escape
		{
			final String text = AUTHENTICATION_TOKEN_WINDOWS_CMD;
			assertEquals(
				expectedWindowsCmd,
				MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false)
			);
		}

		// Test PowerShell escape
		{
			final String text = AUTHENTICATION_TOKEN_POWERSHELL;
			assertEquals(
				expectedPowershell,
				MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false)
			);
		}

		// Test Bash escape
		{
			final String text = AUTHENTICATION_TOKEN_BASH;
			assertEquals(expectedBash, MacrosUpdater.update(text, "user", "pwd".toCharArray(), authToken, "hostname", false));
		}
	}

	/**
	 * Tests the replacement of the USERNAME_ESC macros with various escaped usernames.
	 */
	@Test
	void testUpdateWithEscapedUsername() {
		// Test XML escape
		{
			final String text = USERNAME_ESC_XML;
			final String username = "user&name<test>";
			final String expected = "user&amp;name&lt;test&gt;";
			final String result = MacrosUpdater.update(text, username, "pwd".toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test JSON escape
		{
			final String text = USERNAME_ESC_JSON;
			final String username = "user\"name";
			final String expected = "user\\\"name";
			final String result = MacrosUpdater.update(text, username, "pwd".toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test URL escape
		{
			final String text = USERNAME_ESC_URL;
			final String username = "user name@example.com";
			final String expected = "user%20name%40example.com";
			final String result = MacrosUpdater.update(text, username, "pwd".toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test Regex escape
		{
			final String text = USERNAME_ESC_REGEX;
			final String username = "user.name*";
			final String expected = "\\Quser.name*\\E";
			final String result = MacrosUpdater.update(text, username, "pwd".toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test Windows CMD escape
		{
			final String text = USERNAME_ESC_WINDOWS_CMD;
			final String username = "user&name|cmd<>";
			final String expected = "user^&name^|cmd^<^>";
			final String result = MacrosUpdater.update(text, username, "pwd".toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test PowerShell escape
		{
			final String text = USERNAME_ESC_POWERSHELL;
			final String username = "user\"name$var";
			final String expected = "user`\"name`$var";
			final String result = MacrosUpdater.update(text, username, "pwd".toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test Bash escape
		{
			final String text = USERNAME_ESC_BASH;
			final String username = "user'name$var";
			final String expected = "user\\\\'name\\$var";
			final String result = MacrosUpdater.update(text, username, "pwd".toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}
	}

	/**
	 * Tests the replacement of the PASSWORD_ESC macros with various escaped passwords.
	 */
	@Test
	void testUpdateWithEscapedPassword() {
		// Test SQL escape
		{
			final String text = PASSWORD_ESC_SQL;
			final String password = "p@ss'word";
			final String expected = "p@ss''word";
			final String result = MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test URL escape
		{
			final String text = PASSWORD_ESC_URL;
			final String password = "p@ss word/?";
			final String expected = "p%40ss%20word%2F%3F";
			final String result = MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test Regex escape
		{
			final String text = PASSWORD_ESC_REGEX;
			final String password = "p@ss^word$";
			final String expected = "\\Qp@ss^word$\\E";
			final String result = MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test Windows CMD escape
		{
			final String text = PASSWORD_ESC_WINDOWS_CMD;
			final String password = "p^wd&cmd|";
			final String expected = "p^^wd^&cmd^|";
			final String result = MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test PowerShell escape
		{
			final String text = PASSWORD_ESC_POWERSHELL;
			final String password = "p@ss$word$";
			final String expected = "p@ss`$word`$";
			final String result = MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}

		// Test Bash escape
		{
			final String text = PASSWORD_ESC_BASH;
			final String password = "p$ss[]word&";
			final String expected = "p\\$ss\\[\\]word\\&";
			final String result = MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false);
			assertEquals(expected, result);
		}
	}

	/**
	 * Tests the replacement of the PASSWORD_BASE64 macros with various escaped passwords.
	 */
	@Test
	void testUpdateWithPasswordBase64Escapes() {
		// Base64 encoding of "p@55w0rd!#^&*()_+|~`"
		final String encodedPasswordCommon = "cEA1NXcwcmQhI14mKigpXyt8fmA=";
		final String encodedPasswordUrl = "cEA1NXcwcmQhI14mKigpXyt8fmA%3D"; // Base64 encoding for URL context remains the same
		final String encodedPasswordRegex = "\\QcEA1NXcwcmQhI14mKigpXyt8fmA=\\E"; // Base64 encoding for Regex context remains the sam

		// Test JSON escape
		{
			final String text = PASSWORD_BASE64_JSON;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordCommon,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}

		// Test XML escape
		{
			final String text = PASSWORD_BASE64_XML;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordCommon,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}

		// Test SQL escape
		{
			final String text = PASSWORD_BASE64_SQL;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordCommon,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}

		// Test URL escape
		{
			final String text = PASSWORD_BASE64_URL;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordUrl,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}

		// Test Regex escape
		{
			final String text = PASSWORD_BASE64_REGEX;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordRegex,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}

		// Test Windows CMD escape
		{
			final String text = PASSWORD_BASE64_WINDOWS_CMD;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordCommon,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}

		// Test PowerShell escape
		{
			final String text = PASSWORD_BASE64_POWERSHELL;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordCommon,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}

		// Test Bash escape
		{
			final String text = PASSWORD_BASE64_BASH;
			final String password = "p@55w0rd!#^&*()_+|~`";
			assertEquals(
				encodedPasswordCommon,
				MacrosUpdater.update(text, "user", password.toCharArray(), "token", "hostname", false)
			);
		}
	}

	/**
	 * Tests the replacement of the BASIC_AUTH_BASE64 macros with various escaped authentication tokens.
	 */
	@Test
	void testUpdateWithBasicAuthBase64Escapes() {
		// More complex username and password with special characters
		final String username = "user@name#1!"; // Including '@', '#', and '!'
		final String password = "p@ssw0rd$%^&*"; // Including special characters like '$', '%', '^', and '*'
		final String authToken = username + ":" + password;

		// Base64 encoding of "user@name#1!:p@ssw0rd$%^&*"
		final String encodedAuthCommon = "dXNlckBuYW1lIzEhOnBAc3N3MHJkJCVeJio=";
		final String encodedAuthUrl = "dXNlckBuYW1lIzEhOnBAc3N3MHJkJCVeJio%3D"; // URL
		final String encodedAuthRegex = "\\QdXNlckBuYW1lIzEhOnBAc3N3MHJkJCVeJio=\\E"; // Regex

		// Test JSON escape
		{
			final String text = BASIC_AUTH_BASE64_JSON;
			assertEquals(
				encodedAuthCommon,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}

		// Test XML escape
		{
			final String text = BASIC_AUTH_BASE64_XML;
			assertEquals(
				encodedAuthCommon,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}

		// Test SQL escape
		{
			final String text = BASIC_AUTH_BASE64_SQL;
			assertEquals(
				encodedAuthCommon,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}

		// Test URL escape
		{
			final String text = BASIC_AUTH_BASE64_URL;
			assertEquals(
				encodedAuthUrl,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}

		// Test Regex escape
		{
			final String text = BASIC_AUTH_BASE64_REGEX;
			assertEquals(
				encodedAuthRegex,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}

		// Test Windows CMD escape
		{
			final String text = BASIC_AUTH_BASE64_WINDOWS_CMD;
			assertEquals(
				encodedAuthCommon,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}

		// Test PowerShell escape
		{
			final String text = BASIC_AUTH_BASE64_POWERSHELL;
			assertEquals(
				encodedAuthCommon,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}

		// Test Bash escape
		{
			final String text = BASIC_AUTH_BASE64_BASH;
			assertEquals(
				encodedAuthCommon,
				MacrosUpdater.update(text, username, password.toCharArray(), authToken, "hostname", false)
			);
		}
	}

	/**
	 * Tests the replacement of the SHA256_AUTH macros with various escaped authentication tokens.
	 */
	@Test
	void testUpdateWithSHA256AuthEscapes() {
		// SHA-256 hash of "user:pwd" combined with different tokens
		// Ensure you compute these hashes based on the token and value combinations
		final String expectedHashJson = "c24b4519c83a68714a4c85baeb68630383509e72e0f714eaea6a284a6cc2fbf6";
		final String expectedHashXml = "5198903ab25ebf2ac9849c2675bfc0d4df8bdd3f2807d92290535472603f351a";
		final String expectedHashSql = "45fc026109bd45cfa34b581a3a96786661c9bced00ebc88536f3dc1a345c4702";
		final String expectedHashUrl = "92e01c2bb93fd79ca9f9896385c5424649d72ac8e41c471b75bee4030803cf8f";
		final String expectedHashRegex = "\\Q75cfb6ae1cc1b638813786a573f09fbebe509917e9d5fb35e7fb1e022ce57bbc\\E";
		final String expectedHashWindowsCmd = "449903eccd092b2295e06f904e7ca4c451d9874af9602f77773390316ba86a86";
		final String expectedHashPowershell = "2b46bf`08`0e1e518659bad5ae7b9cbc`0f61e3`0ee45aeebf8f1c`0e5132fcf3b213";
		final String expectedHashBash = "8f38b1a5f493c82e329273b32483c15a79738bb221bfb47c83b6639e55bc41dc";

		// Test JSON escape
		{
			final String text = SHA256_AUTH_JSON;
			final String token = "token\"with\\escape";
			assertEquals(expectedHashJson, MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false));
		}

		// Test XML escape
		{
			final String text = SHA256_AUTH_XML;
			final String token = "token&with<escape>";
			assertEquals(expectedHashXml, MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false));
		}

		// Test SQL escape
		{
			final String text = SHA256_AUTH_SQL;
			final String token = "token'with;escape";
			assertEquals(expectedHashSql, MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false));
		}

		// Test URL escape
		{
			final String text = SHA256_AUTH_URL;
			final String token = "token with space%20and%40symbol";
			assertEquals(expectedHashUrl, MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false));
		}

		// Test Regex escape
		{
			final String text = SHA256_AUTH_REGEX;
			final String token = "token.with*regex";
			assertEquals(
				expectedHashRegex,
				MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false)
			);
		}

		// Test Windows CMD escape
		{
			final String text = SHA256_AUTH_WINDOWS_CMD;
			final String token = "token&with|cmd<>";
			assertEquals(
				expectedHashWindowsCmd,
				MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false)
			);
		}

		// Test PowerShell escape
		{
			final String text = SHA256_AUTH_POWERSHELL;
			final String token = "token\"with$var";
			assertEquals(
				expectedHashPowershell,
				MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false)
			);
		}

		// Test Bash escape
		{
			final String text = SHA256_AUTH_BASH;
			final String token = "token'with$var";
			assertEquals(expectedHashBash, MacrosUpdater.update(text, "user", "pwd".toCharArray(), token, "hostname", false));
		}
	}

	@Test
	void testProtectAndReplaceMatchedMacro() {
		assertEquals(
			"The p$wd\\ is",
			MacrosUpdater.protectAndReplaceMatchedMacro("The %{PASSWORD} is", "%{PASSWORD}", "p$wd\\")
		);
	}

	@Test
	void testUpdateWithMaskedPassword() {
		assertEquals(
			"Masked password is ********",
			MacrosUpdater.update("Masked password is ${PASSWORD}", null, "%?!***$".toCharArray(), null, "hostname", true)
		);
	}

	@Test
	void testUpdateWithEmptyPassword() {
		assertEquals(
			"Password is:",
			MacrosUpdater.update("Password is:${PASSWORD}", null, EMPTY.toCharArray(), null, "hostname", false)
		);
	}

	@Test
	void testUpdateWithNullOrEmptyValueToReplace() {
		assertEquals(EMPTY, MacrosUpdater.update(EMPTY, null, "pass".toCharArray(), null, "hostname", false));
		assertEquals(EMPTY, MacrosUpdater.update(null, null, "pass".toCharArray(), null, "hostname", false));
	}
}
