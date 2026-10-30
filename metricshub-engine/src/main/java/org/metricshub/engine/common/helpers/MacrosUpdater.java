package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import static org.metricshub.engine.common.helpers.JUtils.encodeSha256;
import static org.metricshub.engine.common.helpers.MacroType.AUTHENTICATIONTOKEN;
import static org.metricshub.engine.common.helpers.MacroType.BASIC_AUTH_BASE64;
import static org.metricshub.engine.common.helpers.MacroType.HOSTNAME;
import static org.metricshub.engine.common.helpers.MacroType.PASSWORD;
import static org.metricshub.engine.common.helpers.MacroType.PASSWORD_BASE64;
import static org.metricshub.engine.common.helpers.MacroType.SHA256_AUTH;
import static org.metricshub.engine.common.helpers.MacroType.USERNAME;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.StringHelper.protectCaseInsensitiveRegex;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for updating and replacing HTTP macros in a text string.
 * This class handles various HTTP macros like username, password, authentication token, etc.,
 * and supports different encoding formats (e.g., base64, SHA256).
 * Macros are case-insensitive and can be escaped using different types such as JSON, XML, URL, etc.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MacrosUpdater {

	// Set the macros regex
	private static final Pattern MACRO_PATTERN = Pattern.compile(
		"[$%]\\{(esc\\(([a-zA-Z0-9]+)\\))?(::)?([A-Z0-9_-]+)\\}"
	);

	/**
	 * A simple macro escaped for a POSIX shell, quoted for where it lands (see {@link #shellQuoted}).
	 */
	private static final Pattern SHELL_MACRO_PATTERN = Pattern.compile(
		"[$%]\\{esc\\((?i:bash|linux)\\)::(?<name>USERNAME|PASSWORD|HOSTNAME|AUTHENTICATIONTOKEN)\\}"
	);

	/**
	 * A simple macro escaped for PowerShell, always replaced with nothing, the error logged. A PowerShell command
	 * reaches PowerShell through {@code cmd.exe}, which reads its quotes first, and its comments, double-quoted strings
	 * and here-strings make the single quotes the escape relies on uncertain: nothing tells that a credential such as
	 * {@code $(Start-Process calc)} would not run.
	 */
	private static final Pattern POWERSHELL_MACRO_PATTERN = Pattern.compile(
		"[$%]\\{esc\\((?i:powershell)\\)::(?<name>USERNAME|PASSWORD|HOSTNAME|AUTHENTICATIONTOKEN)\\}"
	);

	/**
	 * Replaces each known macro in the given text with the corresponding values.
	 * Supported macros: %{USERNAME}, %{PASSWORD}, %{HOSTNAME}, %{AUTHENTICATIONTOKEN},
	 * %{PASSWORD_BASE64}, %{BASIC_AUTH}, %{SHA256}, along with various escape formats like JSON, XML, URL, etc.
	 *
	 * @param text                The text with macros to update.
	 * @param username            The HTTP username.
	 * @param password            The HTTP password.
	 * @param authenticationToken The HTTP authentication token.
	 * @param hostname            The remote hostname.
	 * @param maskPasswords       Whether to mask passwords with "********".
	 * @return The updated string with replaced macros.
	 */
	public static String update(
		String text,
		String username,
		char[] password,
		String authenticationToken,
		@NonNull final String hostname,
		final boolean maskPasswords
	) {
		if (text == null || text.isEmpty()) {
			return EMPTY;
		}

		// Null values control
		final String passwordAsString = password != null ? String.valueOf(password) : EMPTY;
		username = username != null ? username : EMPTY;
		authenticationToken = authenticationToken != null ? authenticationToken : EMPTY;

		String updatedContent = text;
		if (updatedContent.contains("%{") || updatedContent.contains("${")) {
			final Map<String, String> simpleMacroNameToField = Map.of(
				USERNAME.name(),
				username,
				PASSWORD.name(),
				maskPasswords ? "********" : passwordAsString,
				HOSTNAME.name(),
				hostname,
				AUTHENTICATIONTOKEN.name(),
				authenticationToken
			);

			// One pass over the template: a value inserted is never read again as a macro
			final Matcher matcher = MACRO_PATTERN.matcher(text);
			final StringBuilder replaced = new StringBuilder();
			while (matcher.find()) {
				final String macro = matcher.group();
				matcher.appendReplacement(replaced, "");
				final Matcher shell = SHELL_MACRO_PATTERN.matcher(macro);
				if (shell.matches()) {
					// Read with the macros before replaced: one replaced with nothing leaves its place to the next
					final String value = simpleMacroNameToField.getOrDefault(shell.group("name"), EMPTY);
					replaced.append(shellQuoted(macro, value, replaced + text.substring(matcher.start()), replaced.length()));
				} else if (POWERSHELL_MACRO_PATTERN.matcher(macro).matches()) {
					log.error("{}: no escape protects a credential in a PowerShell command, replaced with nothing.", macro);
				} else {
					replaced.append(processMacro(macro, macro, matcher.group(4), matcher.group(2), simpleMacroNameToField));
				}
			}
			matcher.appendTail(replaced);
			updatedContent = replaced.toString();
		}

		return updatedContent;
	}

	/**
	 * Quotes a simple macro escaped for a POSIX shell for the quoting around it (see {@link ShellQuote}): escaped for
	 * single quotes inside single quotes, for double quotes inside double quotes, and a whole single-quoted word
	 * anywhere else, so that a credential such as {@code $(cmd)} is never run, whether the connector quoted the macro
	 * or not. Where the value would not be a mere argument, nothing, the error logged.
	 *
	 * @param macro    the macro, for the log
	 * @param value    its value
	 * @param line     the command line, the macros before already replaced
	 * @param position the position of the macro in the line
	 * @return the value quoted
	 */
	private static String shellQuoted(final String macro, final String value, final String line, final int position) {
		final ShellQuote quote = ShellQuote.at(line, position);
		if (quote == ShellQuote.UNKNOWN) {
			// In arithmetic, after eval or sh -c, as a command name...: no quoting would protect it
			log.error("{} is not a mere argument of its command: replaced with nothing.", macro);
		}
		return switch (quote) {
			case SINGLE -> escapeBashSpecialCharacters(value);
			case DOUBLE -> value.replaceAll("([\\\\\"$`])", "\\\\$1");
			case NONE -> "'" + escapeBashSpecialCharacters(value) + "'";
			default -> EMPTY;
		};
	}

	/**
	 * Processes the macro found in the text, replacing it with the corresponding value based on the macro name.
	 *
	 * @param content        The content string with macros.
	 * @param matchedMacro   The matched macro string.
	 * @param macroName      The name of the macro to replace.
	 * @param escapeType     The escape type for the macro value (e.g., JSON, XML).
	 * @param macroNameField A map of macro names and their corresponding values.
	 * @return The content with the macro replaced by the corresponding value.
	 */
	private static String processMacro(
		final String content,
		final String matchedMacro,
		final String macroName,
		final String escapeType,
		final Map<String, String> macroNameField
	) {
		String updatedContent = content;
		if (macroName.startsWith(PASSWORD_BASE64.name())) {
			// PasswordBase64 macros replacement
			updatedContent = replacePasswordBase64(updatedContent, escapeType, matchedMacro, macroNameField);
		} else if (macroName.startsWith(BASIC_AUTH_BASE64.name())) {
			// BasicAuthBase64 macros replacement
			updatedContent = replaceBasicAuthBase64MacroValue(updatedContent, escapeType, matchedMacro, macroNameField);
		} else if (macroName.startsWith(SHA256_AUTH.name())) {
			// Sha256 macros replacement
			updatedContent = replaceSha256MacroValue(updatedContent, escapeType, matchedMacro, macroNameField);
		} else {
			// Simple macro replacement: username, password, hostname and authenticationToken macros
			updatedContent = updateSimpleMacro(updatedContent, matchedMacro, macroName, escapeType, macroNameField);
		}
		return updatedContent;
	}

	/**
	 * Updates simple macros (e.g., USERNAME, PASSWORD) in the content based on the provided escape type.
	 * Replaces the macro with the corresponding value from the macroNameField map.
	 *
	 * @param content        The content string with macros.
	 * @param matchedMacro   The matched macro string.
	 * @param macroName      The name of the macro to be replaced.
	 * @param escapeType     The escape type (e.g., JSON, XML) for the macro value.
	 * @param macroNameField A map of macro names and their corresponding values.
	 * @return The content with the macro replaced by the corresponding value.
	 */
	private static String updateSimpleMacro(
		final String content,
		final String matchedMacro,
		final String macroName,
		final String escapeType,
		final Map<String, String> macroNameField
	) {
		final String replacement = macroNameField.getOrDefault(macroName, EMPTY);
		final String maybeEscapedReplacement =
			escapeType != null ? escapeReplacement(replacement, escapeType) : replacement;
		return protectAndReplaceMatchedMacro(content, matchedMacro, maybeEscapedReplacement);
	}

	/**
	 * Replaces all occurrences of a matched macro in the given content using case-insensitive matching.
	 * The macro is escaped for regex safety, and the replacement is treated as a literal.
	 *
	 * @param content                 The original content where the macro will be replaced
	 * @param matchedMacro            The macro to be replaced, matched case-insensitively
	 * @param maybeEscapedReplacement The literal replacement string for the macro
	 * @return The content with the macro replaced by the provided replacement
	 */
	protected static String protectAndReplaceMatchedMacro(
		String content,
		final String matchedMacro,
		final String maybeEscapedReplacement
	) {
		// Call the protectCaseInsensitiveRegex to protect the matched macro string for case-insensitive matching
		final String protectedMatchedMacro = protectCaseInsensitiveRegex(matchedMacro);

		// Use a case-insensitive pattern to match the content, and replace it with the literal replacement
		return content.replaceAll(protectedMatchedMacro, Matcher.quoteReplacement(maybeEscapedReplacement));
	}

	/**
	 * Escapes special characters in a string based on the provided escape type.
	 *
	 * @param replacement The string to escape (e.g. username, password)
	 * @param escapeType  The type of escape to apply (e.g. JSON, XML, URL).
	 * @return The escaped string.
	 */
	private static String escapeReplacement(final String replacement, final String escapeType) {
		return Optional.ofNullable(EscapeType.fromString(escapeType))
			.map(type -> type.escape(replacement))
			.orElse(replacement);
	}

	/**
	 * Replaces the %{BASIC_AUTH} macro with the corresponding base64-encoded username and password.
	 *
	 * @param valueToUpdate  The string to update.
	 * @param escapeType     The escape type to apply (e.g., JSON, XML).
	 * @param matchedMacro   The matched macro string.
	 * @param macroNameField A map of macro names and their corresponding values.
	 * @return The updated string with the %{BASIC_AUTH} macro replaced.
	 */
	private static String replaceBasicAuthBase64MacroValue(
		final String valueToUpdate,
		final String escapeType,
		final String matchedMacro,
		final Map<String, String> macroNameField
	) {
		// Join the username and password with a colon `username:password`
		// and encode the resulting string in `base64`
		// then replace the macro with the resulting value
		final String formattedBasicAuthString = String.format(
			"%s:%s",
			macroNameField.get(USERNAME.name()),
			macroNameField.get(PASSWORD.name())
		);
		final String escapedValue = escapeReplacement(
			Base64.getEncoder().encodeToString((formattedBasicAuthString).getBytes(StandardCharsets.UTF_8)),
			escapeType
		);
		return protectAndReplaceMatchedMacro(valueToUpdate, matchedMacro, escapedValue);
	}

	/**
	 * Replaces the %{SHA256} macro with the corresponding SHA-256 encoded authentication token.
	 *
	 * @param valueToUpdate  The string to update.
	 * @param escapeType     The escape type to apply (e.g., JSON, XML).
	 * @param matchedMacro   The matched macro string.
	 * @param macroNameField A map of macro names and their corresponding values.
	 * @return The updated string with the %{SHA256} macro replaced.
	 */
	private static String replaceSha256MacroValue(
		final String valueToUpdate,
		final String escapeType,
		final String matchedMacro,
		final Map<String, String> macroNameField
	) {
		// Encode the authentication token into SHA256 string
		// then replace the macro with the resulting value
		final String authenticationToken = macroNameField.get(AUTHENTICATIONTOKEN.name());
		if (authenticationToken == null || authenticationToken.isEmpty()) {
			return protectAndReplaceMatchedMacro(valueToUpdate, matchedMacro, EMPTY);
		}
		final String escapedHashedToken = escapeReplacement(encodeSha256(authenticationToken), escapeType);
		return protectAndReplaceMatchedMacro(valueToUpdate, matchedMacro, escapedHashedToken);
	}

	/**
	 * Replaces the %{PASSWORD_BASE64} macro with the base64-encoded password.
	 *
	 * @param valueToUpdate  The string to update.
	 * @param escapeType     The escape type to apply (e.g., JSON, XML).
	 * @param matchedMacro   The matched macro string.
	 * @param macroNameField A map of macro names and their corresponding values.
	 * @return The updated string with the %{PASSWORD_BASE64} macro replaced.
	 */
	private static String replacePasswordBase64(
		final String valueToUpdate,
		final String escapeType,
		final String matchedMacro,
		final Map<String, String> macroNameField
	) {
		// Encode the password into a base64 string
		// then replace the macro with the resulting value
		final String escapedValue = escapeReplacement(
			Base64.getEncoder().encodeToString(macroNameField.get(PASSWORD.name()).getBytes(StandardCharsets.UTF_8)),
			escapeType
		);
		return protectAndReplaceMatchedMacro(valueToUpdate, matchedMacro, escapedValue);
	}

	/**
	 * Escape special characters in a JSON string value: {@code \}, {@code "} and every control character
	 * (U+0000 to U+001F), with its short form when JSON has one ({@code \n}, {@code \t}...).
	 *
	 * @param value The value to escape.
	 * @return The escaped value
	 */
	static String escapeJsonSpecialCharacters(final String value) {
		final StringBuilder escaped = new StringBuilder(value.length());
		for (final char c : value.toCharArray()) {
			switch (c) {
				case '\\' -> escaped.append("\\\\");
				case '"' -> escaped.append("\\\"");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				case '\b' -> escaped.append("\\b");
				case '\f' -> escaped.append("\\f");
				default -> escaped.append(c < 0x20 ? "\\u%04x".formatted((int) c) : String.valueOf(c));
			}
		}
		return escaped.toString();
	}

	/**
	 * Counts the macros of a text, as {@link #update} would find them.
	 *
	 * @param text a text, {@code null} for none
	 * @return the number of {@code %{...}} and {@code ${...}} macros
	 */
	public static int countMacros(final String text) {
		return text == null ? 0 : (int) MACRO_PATTERN.matcher(text).results().count();
	}

	/**
	 * Escapes special URL characters by replacing them with their percent-encoded equivalents.
	 *
	 * @param value the input string that may contain special URL characters
	 * @return a string where special URL characters have been percent-encoded
	 */
	static String escapeUrlSpecialCharacters(final String value) {
		// Escape common URL characters
		final String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
		// A segment of dots only would climb the path once normalized, where a server decodes %2F (or %5C) first too:
		// the dots of ., .., ../admin are percent-encoded
		return DOT_SEGMENT.matcher(encoded).replaceAll(segment -> segment.group().replace(".", "%2E"));
	}

	/**
	 * A segment of dots only in a URL-encoded value, between its start or end and an encoded {@code /} or {@code \}.
	 */
	private static final Pattern DOT_SEGMENT = Pattern.compile("(?<=^|%2F|%5C)\\.+(?=%2F|%5C|$)");

	/**
	 * Escapes a value for a regular expression as the connectors write them, in the PSL syntax converted by
	 * {@code PslUtils.psl2JavaRegex} (keepOnlyMatchingLines, keep, exclude...): a backslash before
	 * {@code ^ $ . * + ? [ ] \}, while {@code ( ) | { }} are literal in PSL already.
	 *
	 * @param value the input string that may contain special regex characters
	 * @return a PSL regular expression matching the value literally
	 */
	static String escapeRegexSpecialCharacters(final String value) {
		final StringBuilder escaped = new StringBuilder(value.length());
		for (final char c : value.toCharArray()) {
			if ("^$.*+?[]\\".indexOf(c) >= 0) {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return escaped.toString();
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
	 * Escapes a value for the inside of a single-quoted PowerShell string ({@code '...'}), where nothing is
	 * expanded and the only special characters are the single quotes. PowerShell takes the typographic quotes
	 * (‘ ’ ‚ ‛) for single quotes too, and doubling any of them makes it literal.
	 *
	 * @param value The value to escape.
	 * @return The escaped value
	 */
	static String escapePowershellSpecialCharacters(final String value) {
		return value
			.replace("'", "''")
			.replace("\u2018", "\u2018\u2018")
			.replace("\u2019", "\u2019\u2019")
			.replace("\u201A", "\u201A\u201A")
			.replace("\u201B", "\u201B\u201B");
	}

	/**
	 * Escapes a value for the inside of a single-quoted POSIX shell string ({@code '...'}), where nothing is
	 * expanded: a single quote closes the string, is written escaped, and the string reopens.
	 *
	 * @param value the input string that may contain special shell characters
	 * @return a string to place between single quotes
	 */
	static String escapeBashSpecialCharacters(final String value) {
		return value.replace("'", "'\\''");
	}

	/**
	 * Escapes a value for the inside of a single-quoted SQL string ({@code '...'}). Backslashes are doubled
	 * first, as MySQL and MariaDB read them as escapes by default.
	 *
	 * @param value the input string that may contain special SQL characters
	 * @return a string where special SQL characters have been escaped
	 */
	static String escapeSqlSpecialCharacters(final String value) {
		return value
			.replace("\\", "\\\\") // First, or it would double the backslashes added below
			.replace("'", "''")
			.replace("\"", "\\\"") // Only if applicable for the specific SQL dialect
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	/**
	 * Escapes a value for the inside of a WQL string, single- or double-quoted, where the backslash is the
	 * escape character.
	 *
	 * @param value the input string that may contain special WQL characters
	 * @return a string where special WQL characters have been escaped
	 */
	static String escapeWqlSpecialCharacters(final String value) {
		return value.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
	}
}
