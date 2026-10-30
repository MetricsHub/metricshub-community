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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
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
		"\\$\\{(?:esc\\((?<esc>[a-z0-9]+)\\)::)?param::(?<name>[^}]+)\\}",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * The device kinds whose commands run in a POSIX shell.
	 */
	private static final Set<DeviceKind> POSIX_KINDS = EnumSet.of(
		DeviceKind.LINUX,
		DeviceKind.AIX,
		DeviceKind.HPUX,
		DeviceKind.SOLARIS
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
	 * Any other command line, Windows in particular, where no escape is reliable.
	 */
	private static final FieldRule COMMAND_LINE = new FieldRule(EnumSet.noneOf(EscapeType.class), true);

	/**
	 * An awk script, where no escape is reliable.
	 */
	private static final FieldRule AWK_SCRIPT = COMMAND_LINE;

	/**
	 * The paths of a file source, where no escape keeps a value from climbing with {@code ../} or starting at
	 * {@code /}: only an enum or a number.
	 */
	private static final FieldRule FILE_PATH = new FieldRule(EnumSet.noneOf(EscapeType.class), false);

	private static final FieldRule SQL_QUERY = new FieldRule(EnumSet.of(EscapeType.SQL), false);

	private static final FieldRule WQL_QUERY = new FieldRule(EnumSet.of(EscapeType.WQL), false);

	/**
	 * The URL, path, headers or token of an HTTP request: percent-encoding is what keeps a value from adding a path
	 * segment, a parameter or a header line.
	 */
	private static final FieldRule HTTP_URL_OR_HEADER = new FieldRule(EnumSet.of(EscapeType.URL), false);

	private static final FieldRule HTTP_BODY = new FieldRule(
		EnumSet.of(EscapeType.JSON, EscapeType.XML, EscapeType.URL),
		false
	);

	/**
	 * The other source fields: an OID, an object name, a static value, the line filters of a command.
	 */
	private static final FieldRule OTHER_FIELD = new FieldRule(
		EnumSet.of(EscapeType.JSON, EscapeType.XML, EscapeType.URL, EscapeType.REGEX),
		true
	);

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
		final Set<String> unescaped = new HashSet<>();
		for (final Source source : sources.values()) {
			// A command, a query or a request takes the rules of its language: checked apart, then blanked
			final Source others = source.copy();
			if (others instanceof CommandLineSource commandLineSource) {
				final FieldRule rule = runsInPosixShell(commandLineSource, appliesTo) ? POSIX_COMMAND_LINE : COMMAND_LINE;
				checkReferences(name, commandLineSource.getCommandLine(), rule, unescaped);
				commandLineSource.setCommandLine("");
			} else if (others instanceof SqlSource sqlSource) {
				checkReferences(name, sqlSource.getQuery(), SQL_QUERY, unescaped);
				sqlSource.setQuery("");
			} else if (others instanceof WmiSource wmiSource) {
				checkReferences(name, wmiSource.getQuery(), WQL_QUERY, unescaped);
				wmiSource.setQuery("");
			} else if (others instanceof WbemSource wbemSource) {
				checkReferences(name, wbemSource.getQuery(), WQL_QUERY, unescaped);
				wbemSource.setQuery("");
			} else if (others instanceof FileSource fileSource) {
				fileSource.getPaths().forEach(path -> checkReferences(name, path, FILE_PATH, unescaped));
				fileSource.setPaths(new LinkedHashSet<>());
			} else if (others instanceof HttpSource httpSource) {
				for (final String field : Arrays.asList(httpSource.getUrl(), httpSource.getPath(), httpSource.getHeader())) {
					checkReferences(name, field, HTTP_URL_OR_HEADER, unescaped);
				}
				// The token names the source holding it, looked up before any parameter is replaced
				final String token = httpSource.getAuthenticationToken();
				if (token != null && PARAMETER_REFERENCE.matcher(token).find()) {
					throw new IllegalArgumentException(
						"Tool %s references a parameter in authenticationToken, which names a source".formatted(name)
					);
				}
				checkReferences(name, httpSource.getBody(), HTTP_BODY, unescaped);
				httpSource.setUrl(null);
				httpSource.setPath(null);
				httpSource.setHeader(null);
				httpSource.setAuthenticationToken(null);
				httpSource.setBody(null);
			}
			// update() is the visitor of every field a reference is replaced in
			others.update(field -> checkReferences(name, field, OTHER_FIELD, unescaped));
			for (final Compute compute : source.getComputes()) {
				// An awk script is code; the other computes only transform the table
				compute
					.copy()
					.update(field -> checkReferences(name, field, compute instanceof Awk ? AWK_SCRIPT : null, unescaped));
			}
		}
		final Map<String, ToolParameter> checked = new LinkedHashMap<>(parameters);
		unescaped.forEach(parameterName ->
			checked.put(parameterName, parameters.get(parameterName).withoutMetacharacters())
		);
		return new ToolDefinition(name, description, checked, destructive);
	}

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
	 * @param unescaped the names of the parameters inserted unescaped into code, completed here
	 * @return the field, unchanged
	 */
	private String checkReferences(
		final String toolName,
		final String field,
		final FieldRule rule,
		final Set<String> unescaped
	) {
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
				if (
					!rule.patternedStrings() && ToolParameter.STRING.equals(parameter.type()) && parameter.enumValues() == null
				) {
					// Refusing the metacharacters does not cover a query, a request or a path: --, ../, a header line
					throw new IllegalArgumentException(
						"Tool %s inserts the string %s unescaped in a query, a request or a path: escape it or declare an enum".formatted(
							toolName,
							reference
						)
					);
				}
				unescaped.add(parameterName);
			} else if (!rule.escapes().contains(escapeType)) {
				throw new IllegalArgumentException(
					"Tool %s uses %s where only the escapes %s are safe: %s".formatted(
						toolName,
						reference,
						rule.escapes(),
						"in a Windows command line, an awk script or a file path, constrain the parameter instead"
					)
				);
			} else if (!insideString(field, matcher.start(), escapeType)) {
				throw new IllegalArgumentException(
					"Tool %s must write %s inside a string of its language, the only place its escape protects".formatted(
						toolName,
						reference
					)
				);
			} else if (rule == POSIX_COMMAND_LINE && ShellQuote.at(field, matcher.start()) == ShellQuote.UNKNOWN) {
				// The variable replacing the reference is quoted for where it lands, which must be known
				throw new IllegalArgumentException(
					"Tool %s writes %s after a comment, a here-document or backquotes, whose quoting cannot be told".formatted(
						toolName,
						reference
					)
				);
			}
		}
		return field;
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
}
