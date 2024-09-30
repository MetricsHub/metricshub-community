package org.sentrysoftware.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.sentrysoftware.metricshub.engine.common.helpers.JUtils.encodeSha256;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOSTNAME_MACRO;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.PASSWORD_MACRO;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.USERNAME_MACRO;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Utility class for updating HTTP macros in a text string.
 * Replaces known HTTP macros with literal target sequences, such as username,
 * password, authentication-token, base64-password, base64-auth, and sha256-auth.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MacrosUpdater {

	static final String PASSWORD_BASE64_MACRO = "%{PASSWORD_BASE64}";
	static final String BASIC_AUTH_BASE64_MACRO = "%{BASIC_AUTH_BASE64}";
	static final String SHA256_AUTH_MACRO = "%{SHA256_AUTH}";

	// JSON input format macros
	static final String PASSWORD_ESC_JSON = "%{PASSWORD_ESC_JSON}";
	static final String USERNAME_ESC_JSON = "%{USERNAME_ESC_JSON}";
	static final String HOSTNAME_ESC_JSON = "%{HOSTNAME_ESC_JSON}";
	static final String AUTHENTICATIONTOKEN_ESC_JSON = "%{AUTHENTICATIONTOKEN_ESC_JSON}";
	static final String BASIC_AUTH_BASE64_ESC_JSON = "%{BASIC_AUTH_BASE64_ESC_JSON}";
	static final String SHA256_AUTH_ESC_JSON = "%{SHA256_AUTH_ESC_JSON}";
	static final String PASSWORD_BASE64_ESC_JSON = "%{PASSWORD_BASE64_ESC_JSON}";

	// Regex input format macros
	static final String PASSWORD_ESC_REGEX = "%{PASSWORD_ESC_REGEX}";
	static final String USERNAME_ESC_REGEX = "%{USERNAME_ESC_REGEX}";
	static final String HOSTNAME_ESC_REGEX = "%{HOSTNAME_ESC_REGEX}";
	static final String AUTHENTICATIONTOKEN_ESC_REGEX = "%{AUTHENTICATIONTOKEN_ESC_REGEX}";
	static final String BASIC_AUTH_BASE64_ESC_REGEX = "%{BASIC_AUTH_BASE64_ESC_REGEX}";
	static final String SHA256_AUTH_ESC_REGEX = "%{SHA256_AUTH_ESC_REGEX}";
	static final String PASSWORD_BASE64_ESC_REGEX = "%{PASSWORD_BASE64_ESC_REGEX}";

	// URL input format macros
	static final String PASSWORD_ESC_URL = "%{PASSWORD_ESC_URL}";
	static final String USERNAME_ESC_URL = "%{USERNAME_ESC_URL}";
	static final String HOSTNAME_ESC_URL = "%{HOSTNAME_ESC_URL}";
	static final String AUTHENTICATIONTOKEN_ESC_URL = "%{AUTHENTICATIONTOKEN_ESC_URL}";
	static final String BASIC_AUTH_BASE64_ESC_URL = "%{BASIC_AUTH_BASE64_ESC_URL}";
	static final String SHA256_AUTH_ESC_URL = "%{SHA256_AUTH_ESC_URL}";
	static final String PASSWORD_BASE64_ESC_URL = "%{PASSWORD_BASE64_ESC_URL}";

	// XML input format macros
	static final String PASSWORD_ESC_XML = "%{PASSWORD_ESC_XML}";
	static final String USERNAME_ESC_XML = "%{USERNAME_ESC_XML}";
	static final String HOSTNAME_ESC_XML = "%{HOSTNAME_ESC_XML}";
	static final String AUTHENTICATIONTOKEN_ESC_XML = "%{AUTHENTICATIONTOKEN_ESC_XML}";
	static final String BASIC_AUTH_BASE64_ESC_XML = "%{BASIC_AUTH_BASE64_ESC_XML}";
	static final String SHA256_AUTH_ESC_XML = "%{SHA256_AUTH_ESC_XML}";
	static final String PASSWORD_BASE64_ESC_XML = "%{PASSWORD_BASE64_ESC_XML}";

	// Windows Shell input format macros
	static final String PASSWORD_ESC_WINDOWS = "%{PASSWORD_ESC_WINDOWS}";
	static final String USERNAME_ESC_WINDOWS = "%{USERNAME_ESC_WINDOWS}";
	static final String HOSTNAME_ESC_WINDOWS = "%{HOSTNAME_ESC_WINDOWS}";
	static final String AUTHENTICATIONTOKEN_ESC_WINDOWS = "%{AUTHENTICATIONTOKEN_ESC_WINDOWS}";
	static final String BASIC_AUTH_BASE64_ESC_WINDOWS = "%{BASIC_AUTH_BASE64_ESC_WINDOWS}";
	static final String SHA256_AUTH_ESC_WINDOWS = "%{SHA256_AUTH_ESC_WINDOWS}";
	static final String PASSWORD_BASE64_ESC_WINDOWS = "%{PASSWORD_BASE64_ESC_WINDOWS}";

	// Windows CMD input format macros
	static final String PASSWORD_ESC_CMD = "%{PASSWORD_ESC_CMD}";
	static final String USERNAME_ESC_CMD = "%{USERNAME_ESC_CMD}";
	static final String HOSTNAME_ESC_CMD = "%{HOSTNAME_ESC_CMD}";
	static final String AUTHENTICATIONTOKEN_ESC_CMD = "%{AUTHENTICATIONTOKEN_ESC_CMD}";
	static final String BASIC_AUTH_BASE64_ESC_CMD = "%{BASIC_AUTH_BASE64_ESC_CMD}";
	static final String SHA256_AUTH_ESC_CMD = "%{SHA256_AUTH_ESC_CMD}";
	static final String PASSWORD_BASE64_ESC_CMD = "%{PASSWORD_BASE64_ESC_CMD}";

	// Windows Powershell input format macros
	static final String PASSWORD_ESC_POWERSHELL = "%{PASSWORD_ESC_CMD}";
	static final String USERNAME_ESC_POWERSHELL = "%{USERNAME_ESC_CMD}";
	static final String HOSTNAME_ESC_POWERSHELL = "%{HOSTNAME_ESC_CMD}";
	static final String AUTHENTICATIONTOKEN_ESC_POWERSHELL = "%{AUTHENTICATIONTOKEN_ESC_CMD}";
	static final String BASIC_AUTH_BASE64_ESC_POWERSHELL = "%{BASIC_AUTH_BASE64_ESC_CMD}";
	static final String SHA256_AUTH_ESC_POWERSHELL = "%{SHA256_AUTH_ESC_CMD}";
	static final String PASSWORD_BASE64_ESC_POWERSHELL = "%{PASSWORD_BASE64_ESC_CMD}";

	// Linux Shell input format macros
	static final String PASSWORD_ESC_LINUX = "%{PASSWORD_ESC_LINUX}";
	static final String USERNAME_ESC_LINUX = "%{USERNAME_ESC_LINUX}";
	static final String HOSTNAME_ESC_LINUX = "%{HOSTNAME_ESC_LINUX}";
	static final String AUTHENTICATIONTOKEN_ESC_LINUX = "%{AUTHENTICATIONTOKEN_ESC_LINUX}";
	static final String BASIC_AUTH_BASE64_ESC_LINUX = "%{BASIC_AUTH_BASE64_ESC_LINUX}";
	static final String SHA256_AUTH_ESC_LINUX = "%{SHA256_AUTH_ESC_LINUX}";
	static final String PASSWORD_BASE64_ESC_LINUX = "%{PASSWORD_BASE64_ESC_LINUX}";

	// Bash input format macros (Linux Shell alias)
	static final String PASSWORD_ESC_BASH = "%{PASSWORD_ESC_BASH}";
	static final String USERNAME_ESC_BASH = "%{USERNAME_ESC_BASH}";
	static final String HOSTNAME_ESC_BASH = "%{HOSTNAME_ESC_BASH}";
	static final String AUTHENTICATIONTOKEN_ESC_BASH = "%{AUTHENTICATIONTOKEN_ESC_BASH}";
	static final String BASIC_AUTH_BASE64_ESC_BASH = "%{BASIC_AUTH_BASE64_ESC_BASH}";
	static final String SHA256_AUTH_ESC_BASH = "%{SHA256_AUTH_ESC_BASH}";
	static final String PASSWORD_BASE64_ESC_BASH = "%{PASSWORD_BASE64_ESC_BASH}";

	// SQL input format macros
	static final String PASSWORD_ESC_SQL = "%{PASSWORD_ESC_SQL}";
	static final String USERNAME_ESC_SQL = "%{USERNAME_ESC_SQL}";
	static final String HOSTNAME_ESC_SQL = "%{HOSTNAME_ESC_SQL}";
	static final String AUTHENTICATIONTOKEN_ESC_SQL = "%{AUTHENTICATIONTOKEN_ESC_SQL}";
	static final String BASIC_AUTH_BASE64_ESC_SQL = "%{BASIC_AUTH_BASE64_ESC_SQL}";
	static final String SHA256_AUTH_ESC_SQL = "%{SHA256_AUTH_ESC_SQL}";
	static final String PASSWORD_BASE64_ESC_SQL = "%{PASSWORD_BASE64_ESC_SQL}";

	/**
	 * Replaces each known HTTP macro in the given text with the literal target sequences:<br>
	 * username, password, authentication-token, base64-password, base64-auth and sha256-auth
	 *
	 * @param text                The text we wish to update
	 * @param username            The HTTP username
	 * @param password            The HTTP password
	 * @param authenticationToken The HTTP Authentication Token
	 * @param hostname            The remote hostname
	 * @return String value
	 */
	public static String update(
		String text,
		String username,
		char[] password,
		String authenticationToken,
		@NonNull String hostname
	) {
		if (text == null || text.isEmpty()) {
			return EMPTY;
		}

		// Null values control
		final String passwordAsString = password != null ? String.valueOf(password) : EMPTY;
		username = username != null ? username : EMPTY;
		authenticationToken = authenticationToken != null ? authenticationToken : EMPTY;

		// Replace provided macros which don't need processing
		String updatedContent = text
			.replace(USERNAME_MACRO, username)
			.replace(HOSTNAME_MACRO, hostname)
			.replace(PASSWORD_MACRO, passwordAsString)
			.replace("%{AUTHENTICATIONTOKEN}", authenticationToken)
			// Escape Json special characters
			.replace(USERNAME_ESC_JSON, escapeJsonSpecialCharacters(username))
			.replace(PASSWORD_ESC_JSON, escapeJsonSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_JSON, escapeJsonSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_JSON, escapeJsonSpecialCharacters(authenticationToken))
			.replace(USERNAME_ESC_URL, escapeUrlSpecialCharacters(username))
			.replace(PASSWORD_ESC_URL, escapeUrlSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_URL, escapeUrlSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_URL, escapeUrlSpecialCharacters(authenticationToken))
			// Escape XML special characters
			.replace(USERNAME_ESC_XML, escapeXmlSpecialCharacters(username))
			.replace(PASSWORD_ESC_XML, escapeXmlSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_XML, escapeXmlSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_XML, escapeXmlSpecialCharacters(authenticationToken))
			// Escape Windows CMD special characters
			.replace(USERNAME_ESC_WINDOWS, escapeWindowsCmdSpecialCharacters(username))
			.replace(PASSWORD_ESC_WINDOWS, escapeWindowsCmdSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_WINDOWS, escapeWindowsCmdSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_WINDOWS, escapeWindowsCmdSpecialCharacters(authenticationToken))
			// Escape Windows CMD special characters
			.replace(USERNAME_ESC_CMD, escapeWindowsCmdSpecialCharacters(username))
			.replace(PASSWORD_ESC_CMD, escapeWindowsCmdSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_CMD, escapeWindowsCmdSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_CMD, escapeWindowsCmdSpecialCharacters(authenticationToken))
			// Escape Windows Powershell special characters
			.replace(USERNAME_ESC_POWERSHELL, escapeWindowsCmdSpecialCharacters(username))
			.replace(PASSWORD_ESC_POWERSHELL, escapeWindowsCmdSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_POWERSHELL, escapeWindowsCmdSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_POWERSHELL, escapeWindowsCmdSpecialCharacters(authenticationToken))
			// Escape Linux Bash special characters
			.replace(USERNAME_ESC_LINUX, escapeBashSpecialCharacters(username))
			.replace(PASSWORD_ESC_LINUX, escapeBashSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_LINUX, escapeBashSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_LINUX, escapeBashSpecialCharacters(authenticationToken))
			// Escape Linux Bash special characters
			.replace(USERNAME_ESC_BASH, escapeBashSpecialCharacters(username))
			.replace(PASSWORD_ESC_BASH, escapeBashSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_BASH, escapeBashSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_BASH, escapeBashSpecialCharacters(authenticationToken))
			// Escape SQL special characters
			.replace(USERNAME_ESC_SQL, escapeSqlSpecialCharacters(username))
			.replace(PASSWORD_ESC_SQL, escapeSqlSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_SQL, escapeSqlSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_SQL, escapeSqlSpecialCharacters(authenticationToken))
			// Escape Regex special characters
			.replace(USERNAME_ESC_REGEX, escapeRegexSpecialCharacters(username))
			.replace(PASSWORD_ESC_REGEX, escapeRegexSpecialCharacters(passwordAsString))
			.replace(HOSTNAME_ESC_REGEX, escapeRegexSpecialCharacters(hostname))
			.replace(AUTHENTICATIONTOKEN_ESC_REGEX, escapeRegexSpecialCharacters(authenticationToken));

		// Encode the password into a base64 string
		// then replace the macro with the resulting value
		if (updatedContent.indexOf(PASSWORD_BASE64_MACRO) != -1) {
			updatedContent =
				updatedContent
					.replace(PASSWORD_BASE64_MACRO, Base64.getEncoder().encodeToString(passwordAsString.getBytes()))
					// Escape Json special characters
					.replace(
						PASSWORD_BASE64_ESC_JSON,
						Base64.getEncoder().encodeToString(escapeJsonSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_URL,
						Base64.getEncoder().encodeToString(escapeUrlSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_XML,
						Base64.getEncoder().encodeToString(escapeXmlSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_WINDOWS,
						Base64.getEncoder().encodeToString(escapeWindowsCmdSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_CMD,
						Base64.getEncoder().encodeToString(escapeWindowsCmdSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_LINUX,
						Base64.getEncoder().encodeToString(escapeBashSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_BASH,
						Base64.getEncoder().encodeToString(escapeBashSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_SQL,
						Base64.getEncoder().encodeToString(escapeSqlSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_REGEX,
						Base64.getEncoder().encodeToString(escapeRegexSpecialCharacters(passwordAsString).getBytes())
					)
					.replace(
						PASSWORD_BASE64_ESC_POWERSHELL,
						Base64.getEncoder().encodeToString(escapePowershellSpecialCharacters(passwordAsString).getBytes())
					);
		}

		// Join the username and password with a colon `username:password`
		// and encode the resulting string in `base64`
		// then replace the macro with the resulting value
		if (updatedContent.indexOf(BASIC_AUTH_BASE64_MACRO) != -1) {
			updatedContent =
				updatedContent
					.replace(
						BASIC_AUTH_BASE64_MACRO,
						Base64.getEncoder().encodeToString(String.format("%s:%s", username, passwordAsString).getBytes())
					)
					// Escape Json special characters
					.replace(
						BASIC_AUTH_BASE64_ESC_JSON,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format("%s:%s", escapeJsonSpecialCharacters(username), escapeJsonSpecialCharacters(passwordAsString))
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_URL,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format("%s:%s", escapeUrlSpecialCharacters(username), escapeUrlSpecialCharacters(passwordAsString))
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_XML,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format("%s:%s", escapeXmlSpecialCharacters(username), escapeXmlSpecialCharacters(passwordAsString))
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_WINDOWS,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format(
										"%s:%s",
										escapeWindowsCmdSpecialCharacters(username),
										escapeWindowsCmdSpecialCharacters(passwordAsString)
									)
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_CMD,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format(
										"%s:%s",
										escapeWindowsCmdSpecialCharacters(username),
										escapeWindowsCmdSpecialCharacters(passwordAsString)
									)
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_LINUX,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format("%s:%s", escapeBashSpecialCharacters(username), escapeBashSpecialCharacters(passwordAsString))
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_BASH,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format("%s:%s", escapeBashSpecialCharacters(username), escapeBashSpecialCharacters(passwordAsString))
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_SQL,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format("%s:%s", escapeSqlSpecialCharacters(username), escapeSqlSpecialCharacters(passwordAsString))
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_REGEX,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format(
										"%s:%s",
										escapeRegexSpecialCharacters(username),
										escapeRegexSpecialCharacters(passwordAsString)
									)
									.getBytes()
							)
					)
					.replace(
						BASIC_AUTH_BASE64_ESC_POWERSHELL,
						Base64
							.getEncoder()
							.encodeToString(
								String
									.format(
										"%s:%s",
										escapePowershellSpecialCharacters(username),
										escapePowershellSpecialCharacters(passwordAsString)
									)
									.getBytes()
							)
					);
		}

		// Encode the authentication token into SHA256 string
		// then replace the macro with the resulting value
		if (updatedContent.indexOf(SHA256_AUTH_MACRO) != -1) {
			updatedContent =
				updatedContent
					.replace(SHA256_AUTH_MACRO, encodeSha256(authenticationToken))
					// Escape Json special characters
					.replace(SHA256_AUTH_ESC_JSON, encodeSha256(escapeJsonSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_URL, encodeSha256(escapeUrlSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_XML, encodeSha256(escapeXmlSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_WINDOWS, encodeSha256(escapeWindowsCmdSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_CMD, encodeSha256(escapeWindowsCmdSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_LINUX, encodeSha256(escapeBashSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_BASH, encodeSha256(escapeBashSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_SQL, encodeSha256(escapeSqlSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_REGEX, encodeSha256(escapeRegexSpecialCharacters(authenticationToken)))
					.replace(SHA256_AUTH_ESC_POWERSHELL, encodeSha256(escapePowershellSpecialCharacters(authenticationToken)));
		}

		return updatedContent;
	}

	/**
	 * Escape special characters in a JSON string value (\ " \n \r \t).
	 *
	 * @param value The value to escape.
	 * @return The escaped value
	 */
	static String escapeJsonSpecialCharacters(final String value) {
		// Escape common characters
		return value
			// Escape characters (\ " \n \r \t)
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	/**
	 * Escapes special URL characters by replacing them with their percent-encoded equivalents.
	 *
	 * @param value the input string that may contain special URL characters
	 * @return a string where special URL characters have been percent-encoded
	 */
	static String escapeUrlSpecialCharacters(final String value) {
		// Escape common URL characters
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
	}

	/**
	 * Escapes special characters used in regular expressions by prepending a backslash.
	 *
	 * @param value the input string that may contain special regex characters
	 * @return a string where special regex characters have been escaped
	 */
	static String escapeRegexSpecialCharacters(final String value) {
		// Escape special regex characters
		return Pattern.quote(value);
	}

	/**
	 * Escapes special XML characters by replacing them with their corresponding XML entities.
	 *
	 * @param value the input string that may contain special XML characters
	 * @return a string where special XML characters have been replaced with entities
	 */
	static String escapeXmlSpecialCharacters(final String value) {
		// Escape special XML characters
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;");
	}

	/**
	 * Escapes special Windows CMD characters by prepending a caret symbol.
	 *
	 * @param value the input string that may contain special CMD characters
	 * @return a string where special CMD characters have been escaped
	 */
	static String escapeWindowsCmdSpecialCharacters(final String value) {
		// Escape special CMD characters
		return value
			.replace("^", "^^")
			.replace("&", "^&")
			.replace("|", "^|")
			.replace("<", "^<")
			.replace(">", "^>")
			.replace("%", "^%")
			.replace("(", "^(")
			.replace(")", "^)")
			.replace("\"", "^\"");
	}

	/**
	 * Escape special characters in a PowerShell string value (", $, {, }, (, ), [, ], #, \n, \t, \r, \0).
	 *
	 * @param value The value to escape.
	 * @return The escaped value
	 */
	static String escapePowershellSpecialCharacters(final String value) {
		// Escape special characters for Windows PowerShell
		return value
			.replace(".", "`.")
			.replace("\"", "`\"") // Escape double quote
			.replace("$", "`$") // Escape variable indicator
			.replace("{", "`{") // Escape opening curly brace
			.replace("}", "`}") // Escape closing curly brace
			.replace("(", "`(") // Escape opening parenthesis
			.replace(")", "`)") // Escape closing parenthesis
			.replace("[", "`[") // Escape opening bracket
			.replace("]", "`]") // Escape closing bracket
			.replace("#", "`#") // Escape comment indicator
			.replace("\n", "`\n") // Escape new line
			.replace("\t", "`\t") // Escape tab
			.replace("\r", "`\r") // Escape carriage return
			.replace("\0", "`\0"); // Escape null character
	}

	/**
	 * Escapes special Bash characters by prepending a backslash.
	 *
	 * @param value the input string that may contain special Bash characters
	 * @return a string where special Bash characters have been escaped
	 */
	static String escapeBashSpecialCharacters(final String value) {
		// Escape special Bash characters
		return value
			.replace("'", "\\'")
			.replace("\"", "\\\"")
			.replace("\\", "\\\\")
			.replace("$", "\\$")
			.replace("!", "\\!")
			.replace("*", "\\*")
			.replace("?", "\\?")
			.replace("[", "\\[")
			.replace("]", "\\]")
			.replace("(", "\\(")
			.replace(")", "\\)")
			.replace("{", "\\{")
			.replace("}", "\\}")
			.replace("|", "\\|")
			.replace("&", "\\&")
			.replace("<", "\\<")
			.replace(">", "\\>")
			.replace("~", "\\~");
	}

	/**
	 * Escapes special SQL characters such as single quotes and optionally backslashes or double quotes, depending on the SQL dialect.
	 *
	 * @param value the input string that may contain special SQL characters
	 * @return a string where special SQL characters have been escaped
	 */
	static String escapeSqlSpecialCharacters(final String value) {
		// Escape special SQL characters
		return value
			.replace("'", "''")
			.replace("\"", "\\\"") // Only if applicable for the specific SQL dialect
			.replace("\\", "\\\\") // Only if applicable for the specific SQL dialect
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
