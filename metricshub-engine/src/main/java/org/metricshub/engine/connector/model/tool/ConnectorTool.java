package org.metricshub.engine.connector.model.tool;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.metricshub.engine.common.helpers.EscapeType;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.ShellQuote;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.AbstractConcat;
import org.metricshub.engine.connector.model.monitor.task.source.compute.AbstractMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Decode;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Encode;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolParameter;

/**
 * A tool declared by a connector under its {@code tools:} section: the sources and computes to run on the host, the
 * parameters an invocation may pass to them through {@code ${param::name}}, and the source whose table is returned.
 * The sources run exactly as a monitor job's, so a tool has no mapping and creates no monitor.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConnectorTool extends AbstractMonitorTask {

	private static final long serialVersionUID = 1L;

	/**
	 * A {@code ${param::name}} reference, with its optional {@code esc(TYPE)::} prefix.
	 */
	private static final Pattern PARAMETER_REFERENCE = Pattern.compile(
		// ${attribute::name} too: a tool's arguments are its attributes, replaced from the same map
		// Any escape name, so that a malformed one (power-shell) is refused rather than left unseen
		"\\$\\{(?:esc\\((?<esc>[^)]*)\\)::)?(?:param|attribute)::(?<name>[^}]+)\\}",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * A {@code LIKE} operator right before a string.
	 */
	private static final Pattern LIKE_BEFORE = Pattern.compile("\\bLIKE\\s*$", Pattern.CASE_INSENSITIVE);

	/**
	 * The device kinds whose commands run in a POSIX shell.
	 */
	private static final Set<DeviceKind> POSIX_KINDS = EnumSet.of(
		DeviceKind.LINUX,
		DeviceKind.AIX,
		DeviceKind.HPUX,
		DeviceKind.SOLARIS,
		DeviceKind.TRU64
	);

	/**
	 * What a field that is code takes.
	 *
	 * @param escapes         the escape types that protect a value there
	 * @param patternedStrings whether a string constrained by a pattern only may be inserted unescaped, refusing the
	 *                        shell metacharacters: not in a query or a request, whose comments ({@code --}), paths
	 *                        ({@code ../}) or lines need no metacharacter
	 */
	private record FieldRule(Set<EscapeType> escapes, boolean patternedStrings) {}

	/**
	 * A command line run by a POSIX shell, where an escaped value is assigned to a variable rather than inserted
	 * (see {@code SourceUpdaterProcessor}).
	 */
	private static final FieldRule POSIX_COMMAND_LINE = new FieldRule(
		EnumSet.of(EscapeType.BASH, EscapeType.LINUX),
		true
	);

	/**
	 * Any other command line, Windows in particular, where no escape is reliable and nothing tells whether a word is
	 * a command: only an enum or a number.
	 */
	private static final FieldRule COMMAND_LINE = new FieldRule(EnumSet.noneOf(EscapeType.class), false);

	/**
	 * An awk script, where no escape is reliable and the characters a pattern lets through ({@code / + - =}) are
	 * operators: only an enum or a number.
	 */
	private static final FieldRule AWK_SCRIPT = new FieldRule(EnumSet.noneOf(EscapeType.class), false);

	/**
	 * The regular expressions of a command line source ({@code keep}, {@code exclude}) and of the matching-lines
	 * computes ({@code regExp}): escaped to match literally, or an enum or a number. A value used as a regular
	 * expression could backtrack without end, a {@code +} needing no metacharacter.
	 */
	private static final FieldRule LINE_FILTER = new FieldRule(EnumSet.of(EscapeType.REGEX), false);

	/**
	 * The paths of a file source, where no escape keeps a value from climbing with {@code ../} or starting at
	 * {@code /}: only an enum or a number.
	 */
	private static final FieldRule FILE_PATH = new FieldRule(EnumSet.noneOf(EscapeType.class), false);

	private static final FieldRule SQL_QUERY = new FieldRule(EnumSet.of(EscapeType.SQL), false);

	private static final FieldRule WQL_QUERY = new FieldRule(EnumSet.of(EscapeType.WQL), false);

	/**
	 * The URL or path of an HTTP request: percent-encoding is what keeps a value from adding a path segment or a
	 * parameter.
	 */
	private static final FieldRule HTTP_URL = new FieldRule(EnumSet.of(EscapeType.URL), false);

	/**
	 * The headers of an HTTP request, whose values are not URL-decoded: percent-encoding would change them, and no
	 * other escape keeps a line out. Only an enum or a number.
	 */
	private static final FieldRule HTTP_HEADER = new FieldRule(EnumSet.noneOf(EscapeType.class), false);

	private static final FieldRule HTTP_BODY = new FieldRule(
		EnumSet.of(EscapeType.JSON, EscapeType.XML, EscapeType.URL),
		false
	);

	/**
	 * The fields without a rule of their own (an OID, a JMX object name, a static value...), whose syntax no escape
	 * covers: only an enum or a number.
	 */
	private static final FieldRule OTHER_FIELD = new FieldRule(EnumSet.noneOf(EscapeType.class), false);

	/**
	 * What the tool does, as read by the LLM.
	 */
	private String description;

	/**
	 * The arguments the tool accepts, by name, in declaration order.
	 */
	@JsonSetter(nulls = SKIP)
	private Map<String, ToolParameter> parameters = new LinkedHashMap<>(); // NOSONAR LinkedHashMap is Serializable

	/**
	 * Whether the tool changes the state of the host, e.g. a reboot.
	 */
	private boolean destructive;

	/**
	 * The source whose table the tool returns.
	 */
	private ToolResult result;

	/**
	 * Constructs a tool with its sources and description.
	 *
	 * @param sources        the sources to run, by name
	 * @param executionOrder the names of the sources in the order they must run, empty to follow their dependencies
	 * @param description    what the tool does
	 * @param parameters     the arguments the tool accepts, by name
	 * @param destructive    whether the tool changes the state of the host
	 * @param result         the source whose table the tool returns
	 */
	@Builder
	public ConnectorTool(
		final Map<String, Source> sources,
		final Set<String> executionOrder,
		final String description,
		final Map<String, ToolParameter> parameters,
		final boolean destructive,
		final ToolResult result
	) {
		super(sources, null, executionOrder);
		this.description = description;
		this.parameters = parameters == null ? new LinkedHashMap<>() : parameters;
		this.destructive = destructive;
		this.result = result;
	}

	/**
	 * Describes this tool for the LLM, once its sources are checked to insert every parameter safely.
	 *
	 * @param name     the name the connector declares the tool under
	 * @param appliesTo the device kinds the connector applies to, which tell the shell its command lines run in
	 * @return the definition of the tool, whose string parameters inserted unescaped refuse the shell and query
	 *         metacharacters (see {@link ToolParameter#withoutMetacharacters()})
	 * @throws IllegalArgumentException if a source or an awk script inserts a parameter unsafely (see
	 *                                  {@link #checkReferences})
	 */
	public ToolDefinition toDefinition(final String name, final Set<DeviceKind> appliesTo) {
		final Usages usages = new Usages(new HashSet<>(), new HashSet<>());
		for (final Source source : sources.values()) {
			// A command, a query or a request takes the rules of its language: checked apart, then blanked
			final Source others = source.copy();
			if (others instanceof CommandLineSource commandLineSource) {
				final FieldRule rule = runsInPosixShell(commandLineSource, appliesTo) ? POSIX_COMMAND_LINE : COMMAND_LINE;
				checkReferences(name, commandLineSource.getCommandLine(), rule, usages);
				// keep and exclude are regular expressions; separators and selectColumns are not
				checkReferences(name, commandLineSource.getKeep(), LINE_FILTER, usages);
				checkReferences(name, commandLineSource.getExclude(), LINE_FILTER, usages);
				commandLineSource.setCommandLine("");
				commandLineSource.setKeep(null);
				commandLineSource.setExclude(null);
			} else if (others instanceof SqlSource sqlSource) {
				checkReferences(name, sqlSource.getQuery(), SQL_QUERY, usages);
				sqlSource.setQuery("");
			} else if (others instanceof WmiSource wmiSource) {
				checkReferences(name, wmiSource.getQuery(), WQL_QUERY, usages);
				wmiSource.setQuery("");
			} else if (others instanceof WbemSource wbemSource) {
				checkReferences(name, wbemSource.getQuery(), WQL_QUERY, usages);
				wbemSource.setQuery("");
			} else if (others instanceof FileSource fileSource) {
				fileSource.getPaths().forEach(path -> checkReferences(name, path, FILE_PATH, usages));
				fileSource.setPaths(new LinkedHashSet<>());
			} else if (others instanceof HttpSource httpSource) {
				checkReferences(name, httpSource.getUrl(), HTTP_URL, usages);
				checkReferences(name, httpSource.getPath(), HTTP_URL, usages);
				checkReferences(name, httpSource.getHeader(), HTTP_HEADER, usages);
				// The token names the source holding it, looked up before any parameter is replaced
				final String token = httpSource.getAuthenticationToken();
				if (token != null && PARAMETER_REFERENCE.matcher(token).find()) {
					throw new IllegalArgumentException(
						"Tool %s references a parameter in authenticationToken, which names a source".formatted(name)
					);
				}
				checkReferences(name, httpSource.getBody(), HTTP_BODY, usages);
				httpSource.setUrl(null);
				httpSource.setPath(null);
				httpSource.setHeader(null);
				httpSource.setAuthenticationToken(null);
				httpSource.setBody(null);
			}
			// The fields no reference is ever replaced in would run with the reference left in
			for (final String field : unsubstitutedFields(source)) {
				if (field != null && PARAMETER_REFERENCE.matcher(field).find()) {
					throw new IllegalArgumentException(
						"Tool %s references a parameter in a field of a %s source, where it is not replaced".formatted(
							name,
							source.getType()
						)
					);
				}
			}
			// update() is the visitor of every field a reference is replaced in
			others.update(field -> checkReferences(name, field, OTHER_FIELD, usages));
			if (source instanceof JawkSource jawkSource && jawkSource.getVariables() != null) {
				// Left out of update(), replaced apart as awk variables: data, but declared ones
				jawkSource
					.getVariables()
					.values()
					.forEach(value -> checkReferences(name, value, null, usages));
			}
			for (final Compute compute : source.getComputes()) {
				checkCompute(name, compute.copy(), usages);
			}
		}
		final Map<String, ToolParameter> checked = new LinkedHashMap<>(parameters);
		usages
			.shellArguments()
			.forEach(parameterName -> checked.put(parameterName, parameters.get(parameterName).withoutLeadingDash()));
		usages
			.unescaped()
			.forEach(parameterName -> checked.put(parameterName, parameters.get(parameterName).withoutMetacharacters()));
		return new ToolDefinition(name, description, checked, destructive);
	}

	/**
	 * The parameters whose values the tool's sources take with extra restrictions.
	 *
	 * @param unescaped      the parameters inserted unescaped into code, whose strings refuse the metacharacters
	 * @param shellArguments the parameters escaped into a POSIX command line, whose strings may not start with
	 *                       {@code -}: one word still, but an option to the command
	 */
	private record Usages(Set<String> unescaped, Set<String> shellArguments) {}

	/**
	 * Describes this tool for the LLM (see {@link #toDefinition(String, Set)}).
	 *
	 * @param name      the name the connector declares the tool under
	 * @param connector the connector declaring the tool
	 * @return the definition of the tool
	 */
	public ToolDefinition toDefinition(final String name, final Connector connector) {
		return toDefinition(
			name,
			Optional.ofNullable(connector.getConnectorIdentity())
				.map(ConnectorIdentity::getDetection)
				.map(Detection::getAppliesTo)
				.orElse(Set.of())
		);
	}

	/**
	 * Checks the references of a compute, field by field, like a source's: the fields known to be data (a value
	 * appended or replaced, a list of values, the variables of an awk script) take any declared parameter, the
	 * regular expressions take {@code esc(regex)}, an awk script takes enums and numbers, and every other field (a
	 * separator that is a regular expression, a JSON or XML path...) only enums and numbers.
	 *
	 * @param name      the tool name
	 * @param copy      a copy of the compute, blanked as its fields are checked
	 * @param usages    the parameters inserted unescaped into code or escaped into a POSIX command line, completed here
	 */
	private void checkCompute(final String name, final Compute copy, final Usages usages) {
		if (copy instanceof AbstractConcat concat) {
			checkReferences(name, concat.getValue(), null, usages);
			concat.setValue("");
		} else if (copy instanceof Replace replace) {
			// A literal replacement
			checkReferences(name, replace.getExistingValue(), null, usages);
			checkReferences(name, replace.getNewValue(), null, usages);
			replace.setExistingValue(null);
			replace.setNewValue(null);
		} else if (copy instanceof Encode || copy instanceof Decode) {
			// Their update() is a no-op: a reference in their encoding is never replaced
			final String encoding = copy instanceof Encode encode ? encode.getEncoding() : ((Decode) copy).getEncoding();
			if (encoding != null && PARAMETER_REFERENCE.matcher(encoding).find()) {
				throw new IllegalArgumentException(
					"Tool %s references a parameter in the encoding of an encode or decode compute, where it is not replaced".formatted(
						name
					)
				);
			}
		} else if (copy instanceof AbstractMatchingLines matchingLines) {
			checkReferences(name, matchingLines.getRegExp(), LINE_FILTER, usages);
			checkReferences(name, matchingLines.getValueList(), null, usages);
			matchingLines.setRegExp(null);
			matchingLines.setValueList(null);
		} else if (copy instanceof Awk awk) {
			checkReferences(name, awk.getScript(), AWK_SCRIPT, usages);
			checkReferences(name, awk.getKeep(), LINE_FILTER, usages);
			checkReferences(name, awk.getExclude(), LINE_FILTER, usages);
			if (awk.getVariables() != null) {
				// Left out of update(), replaced apart as awk variables: data, but declared ones
				awk
					.getVariables()
					.values()
					.forEach(value -> checkReferences(name, value, null, usages));
			}
			awk.setScript("");
			awk.setKeep(null);
			awk.setExclude(null);
		}
		copy.update(field -> checkReferences(name, field, OTHER_FIELD, usages));
	}

	/**
	 * @param source a source
	 * @return the string fields of the source that its {@code update()} leaves out, so that no reference is ever
	 *         replaced in them
	 */
	private static List<String> unsubstitutedFields(final Source source) {
		if (source instanceof InternalDbQuerySource internalDbQuerySource) {
			return Collections.singletonList(internalDbQuerySource.getQuery());
		}
		if (source instanceof JawkSource jawkSource) {
			return Collections.singletonList(jawkSource.getScript());
		}
		if (source instanceof TableUnionSource tableUnionSource) {
			return tableUnionSource.getTables();
		}
		return List.of();
	}

	/**
	 * @param source    a command line source
	 * @param appliesTo the device kinds the connector applies to
	 * @return whether the command runs in a POSIX shell, on the agent's own system when it runs locally
	 */
	private static boolean runsInPosixShell(final CommandLineSource source, final Set<DeviceKind> appliesTo) {
		if (Boolean.TRUE.equals(source.getExecuteLocally())) {
			return !LocalOsHandler.isWindows();
		}
		return appliesTo != null && !appliesTo.isEmpty() && POSIX_KINDS.containsAll(appliesTo);
	}

	/**
	 * Checks the {@code ${param::name}} references of a field. Every reference must name a declared parameter and
	 * a known escape type. In a field that is code ({@code rule} given):
	 * <ul>
	 * <li>a parameter inserted unescaped must be constrained (see {@link ToolParameter#isConstrained()}), by an enum
	 * or a non-string type where the rule takes no {@link FieldRule#patternedStrings()}, and is added to
	 * {@code unescaped},</li>
	 * <li>the escape type must be one of the rule's,</li>
	 * <li>the {@code sql}, {@code wql} and {@code json} escapes, which only cover the inside of a string, must be
	 * inside such a string (see {@link #insideString}).</li>
	 * </ul>
	 *
	 * @param toolName  the tool name, for the error message
	 * @param field     the field of a source or a compute
	 * @param rule      what the field takes, {@code null} for a field that is data
	 * @param usages    the parameters inserted unescaped into code or escaped into a POSIX command line, completed here
	 * @return the field, unchanged
	 */
	private String checkReferences(final String toolName, final String field, final FieldRule rule, final Usages usages) {
		if (field == null) {
			return null;
		}
		final Matcher matcher = PARAMETER_REFERENCE.matcher(field);
		while (matcher.find()) {
			final String reference = matcher.group();
			final String parameterName = matcher.group("name").trim();
			final ToolParameter parameter = parameters.get(parameterName);
			if (parameter == null) {
				throw new IllegalArgumentException(
					"Tool %s references %s, which it does not declare".formatted(toolName, reference)
				);
			}
			final String escape = matcher.group("esc");
			final EscapeType escapeType = escape == null ? null : EscapeType.fromString(escape);
			if (escape != null && escapeType == null) {
				throw new IllegalArgumentException("Tool %s uses an unknown escape type in %s".formatted(toolName, reference));
			}
			if (!parameter.required() && parameter.defaultValue() == null) {
				// Absent from an invocation, it would leave the reference in the source
				throw new IllegalArgumentException(
					"Tool %s references %s, optional without a default: declare a default (even '') or make it required".formatted(
						toolName,
						reference
					)
				);
			}
			if (rule == null) {
				continue;
			}
			if (escapeType == null) {
				if (!parameter.isConstrained()) {
					throw new IllegalArgumentException(
						"Tool %s inserts %s unescaped: escape it, or declare an enum, a pattern or a non-string type".formatted(
							toolName,
							reference
						)
					);
				}
				final boolean string = ToolParameter.STRING.equals(parameter.type()) && parameter.enumValues() == null;
				if (string && !rule.patternedStrings()) {
					// Refusing the metacharacters does not cover a query, a request or a path: --, ../, a header line
					throw new IllegalArgumentException(
						"Tool %s inserts the string %s unescaped in a query, a request or a path: escape it or declare an enum".formatted(
							toolName,
							reference
						)
					);
				}
				if (string && rule == POSIX_COMMAND_LINE) {
					// Without any metacharacter, a word is still a command to eval or in a command's place
					checkShellArgument(toolName, field, matcher.start(), reference);
				}
				usages.unescaped().add(parameterName);
			} else if (!rule.escapes().contains(escapeType)) {
				throw new IllegalArgumentException(
					"Tool %s uses %s where only the escapes %s are safe: %s".formatted(
						toolName,
						reference,
						rule.escapes(),
						"in a Windows command line, an awk script or a file path, constrain the parameter instead"
					)
				);
			} else if (!escapeProtects(field, matcher.start(), escapeType)) {
				throw new IllegalArgumentException(
					"Tool %s writes %s where its escape does not protect the value: %s".formatted(
						toolName,
						reference,
						"after a backslash, outside of a string of its language, in XML markup or in a regex character class"
					)
				);
			} else if (rule == POSIX_COMMAND_LINE) {
				// The variable replacing the reference is quoted for where it lands, which must be known
				checkShellArgument(toolName, field, matcher.start(), reference);
				// One word, but -delete would still be an option to the command
				usages.shellArguments().add(parameterName);
			}
		}
		return field;
	}

	/**
	 * Checks a value lands as a mere argument of a POSIX command line (see {@link ShellQuote}).
	 *
	 * @param toolName    the tool name, for the error message
	 * @param commandLine the command line
	 * @param position    the position of the reference
	 * @param reference   the reference, for the error message
	 * @throws IllegalArgumentException if the value would be a command name, a redirection target, an argument
	 *                                  evaluated again, or follows a construct whose quoting cannot be told
	 */
	private static void checkShellArgument(
		final String toolName,
		final String commandLine,
		final int position,
		final String reference
	) {
		if (ShellQuote.at(commandLine, position) == ShellQuote.UNKNOWN) {
			throw new IllegalArgumentException(
				(
					"Tool %s writes %s where it would not be a mere argument (a command name, a redirection, an argument of " +
					"eval, a shell, an interpreter or arithmetic), or after a comment, a here-document or backquotes"
				).formatted(toolName, reference)
			);
		}
	}

	/**
	 * Tells whether a position of a query or a request is inside a string that the escape type covers: a
	 * single-quoted SQL string, a single- or double-quoted WQL string, a double-quoted JSON string. Where it cannot
	 * tell, a SQL comment or a backslash (an escape in MySQL, a character in standard SQL), the answer is no.
	 *
	 * @param field    a query or a request
	 * @param position a position in the field
	 * @param type     the escape type
	 * @return whether the position is inside such a string; always {@code true} for an escape that protects a value
	 *         anywhere ({@code bash}, assigned to a variable; {@code url}, {@code xml}, {@code regex})
	 */
	static boolean insideString(final String field, final int position, final EscapeType type) {
		final String quotes = switch (type) {
			case SQL -> "'";
			case WQL -> "'\"";
			case JSON -> "\"";
			default -> null;
		};
		if (quotes == null) {
			return true;
		}
		final boolean sql = type == EscapeType.SQL;
		char quote = '\0';
		for (int i = 0; i < position; i++) {
			final char c = field.charAt(i);
			if (
				sql && (c == '\\' || (quote == '\0' && (c == '#' || field.startsWith("--", i) || field.startsWith("/*", i))))
			) {
				return false;
			}
			if (quote == '\0') {
				if (c == '"' || (c == '\'' && type != EscapeType.JSON)) {
					quote = c;
				}
			} else if (c == '\\') {
				i++;
			} else if (c == quote) {
				quote = '\0';
			}
		}
		return quotes.indexOf(quote) >= 0;
	}

	/**
	 * @param field    a field
	 * @param position the position of an escaped reference
	 * @param type     its escape type
	 * @return whether the escape protects the value there: not after a backslash, inside a string of its language
	 *         ({@link #insideString}), in XML character data or a quoted attribute value, outside of a regex
	 *         character class
	 */
	private static boolean escapeProtects(final String field, final int position, final EscapeType type) {
		if (followsBackslash(field, position) || !insideString(field, position, type)) {
			return false;
		}
		if (type == EscapeType.XML) {
			return insideXmlValue(field, position);
		}
		if (type == EscapeType.WQL) {
			return !insideWqlLike(field, position);
		}
		return type != EscapeType.REGEX || !insideCharacterClass(field, position);
	}

	/**
	 * @param query    a WQL query
	 * @param position a position inside one of its strings
	 * @return whether the string is the pattern of a {@code LIKE}, where WQL does not read {@code \} as an escape:
	 *         the {@code wql} escape would not keep a quote from closing it
	 */
	static boolean insideWqlLike(final String query, final int position) {
		int open = -1;
		char quote = '\0';
		for (int i = 0; i < position; i++) {
			final char c = query.charAt(i);
			if (quote == '\0') {
				if (c == '"' || c == '\'') {
					quote = c;
					open = i;
				}
			} else if (c == quote) {
				quote = '\0';
			}
		}
		return quote != '\0' && LIKE_BEFORE.matcher(query.substring(0, open)).find();
	}

	/**
	 * @param field    a field
	 * @param position a position in the field
	 * @return whether an odd run of backslashes precedes the position, which would escape the first character of the
	 *         value inserted there, an escaped quote first of all
	 */
	static boolean followsBackslash(final String field, final int position) {
		int count = 0;
		while (position - count > 0 && field.charAt(position - count - 1) == '\\') {
			count++;
		}
		return count % 2 == 1;
	}

	/**
	 * Tells whether a position of an XML document is in character data or in a quoted attribute value, the only
	 * places the {@code xml} escape protects: not in a tag name, a comment, a CDATA section or a declaration.
	 *
	 * @param body     an XML document
	 * @param position a position in the document
	 * @return whether the position is in character data or a quoted attribute value
	 */
	static boolean insideXmlValue(final String body, final int position) {
		char quote = '\0';
		boolean inTag = false;
		for (int i = 0; i < position; i++) {
			final char c = body.charAt(i);
			if (quote != '\0') {
				if (c == quote) {
					quote = '\0';
				}
			} else if (inTag) {
				if (c == '"' || c == '\'') {
					quote = c;
				} else if (c == '>') {
					inTag = false;
				}
			} else if (c == '<') {
				final String end = markupEnd(body, i);
				if (end == null) {
					inTag = true;
				} else {
					final int close = body.indexOf(end, i);
					if (close < 0 || close + end.length() > position) {
						return false;
					}
					i = close + end.length() - 1;
				}
			}
		}
		return !inTag || quote != '\0';
	}

	/**
	 * @param body an XML document
	 * @param i    the position of a {@code <}
	 * @return the end of the comment, CDATA section, processing instruction or declaration starting there,
	 *         {@code null} for an element tag
	 */
	private static String markupEnd(final String body, final int i) {
		if (body.startsWith("<!--", i)) {
			return "-->";
		}
		if (body.startsWith("<![CDATA[", i)) {
			return "]]>";
		}
		if (body.startsWith("<?", i)) {
			return "?>";
		}
		return body.startsWith("<!", i) ? ">" : null;
	}

	/**
	 * @param regex    a PSL regular expression
	 * @param position a position in the regular expression
	 * @return whether the position is inside a {@code [...]} character class, where an escaped value still forms
	 *         ranges ({@code a-z})
	 */
	static boolean insideCharacterClass(final String regex, final int position) {
		boolean inClass = false;
		for (int i = 0; i < position; i++) {
			final char c = regex.charAt(i);
			if (c == '\\') {
				i++;
			} else if (c == '[') {
				inClass = true;
			} else if (c == ']') {
				inClass = false;
			}
		}
		return inClass;
	}
}
