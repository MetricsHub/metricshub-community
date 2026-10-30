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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.metricshub.engine.common.helpers.EscapeType;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
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
	 * The escapes a command line takes.
	 */
	private static final Set<EscapeType> SHELL_ESCAPES = EnumSet.of(
		EscapeType.BASH,
		EscapeType.LINUX,
		EscapeType.POWERSHELL,
		EscapeType.CMD,
		EscapeType.WINDOWS
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
	 * @param name the name the connector declares the tool under
	 * @return the definition of the tool
	 * @throws IllegalArgumentException if a source or an awk script inserts a parameter unsafely (see
	 *                                  {@link #checkReferences})
	 */
	public ToolDefinition toDefinition(final String name) {
		for (final Source source : sources.values()) {
			// Copies: update() is the visitor of every field a reference is replaced in
			source.copy().update(value -> checkReferences(name, value, null));
			// A command or a query only takes the escapes of its language
			if (source instanceof CommandLineSource commandLineSource) {
				checkReferences(name, commandLineSource.getCommandLine(), SHELL_ESCAPES);
			} else if (source instanceof SqlSource sqlSource) {
				checkReferences(name, sqlSource.getQuery(), EnumSet.of(EscapeType.SQL));
			} else if (source instanceof WmiSource wmiSource) {
				checkReferences(name, wmiSource.getQuery(), EnumSet.of(EscapeType.WQL));
			} else if (source instanceof WbemSource wbemSource) {
				checkReferences(name, wbemSource.getQuery(), EnumSet.of(EscapeType.WQL));
			}
			// An awk script is code; the other computes only transform the table the source returned
			source
				.getComputes()
				.stream()
				.filter(Awk.class::isInstance)
				.forEach(compute -> compute.copy().update(value -> checkReferences(name, value, null)));
		}
		return new ToolDefinition(name, description, parameters, destructive);
	}

	/**
	 * Checks the {@code ${param::name}} references of a field that is code (a command, a query, a request, a
	 * script):
	 * <ul>
	 * <li>a parameter that is not safe as is (see {@link ToolParameter#isSafeUnescaped()}) must be escaped with
	 * {@code ${esc(TYPE)::param::name}},</li>
	 * <li>the escape type must exist, and be one of {@code allowed} when given,</li>
	 * <li>an escape that only covers the inside of a string (see {@link #quoteRule}) must be inside such a string.</li>
	 * </ul>
	 *
	 * @param toolName the tool name, for the error message
	 * @param field    the field of a source or an awk script
	 * @param allowed  the only escape types the field takes, {@code null} for any
	 * @return the field, unchanged
	 */
	private String checkReferences(final String toolName, final String field, final Set<EscapeType> allowed) {
		if (field == null) {
			return null;
		}
		final Matcher matcher = PARAMETER_REFERENCE.matcher(field);
		while (matcher.find()) {
			final String reference = matcher.group();
			final String escape = matcher.group("esc");
			if (escape == null) {
				final ToolParameter parameter = parameters.get(matcher.group("name").trim());
				if (parameter != null && !parameter.isSafeUnescaped()) {
					throw new IllegalArgumentException(
						"Tool %s inserts %s unescaped: write ${esc(TYPE)::param::%s}, ".formatted(
								toolName,
								reference,
								matcher.group("name")
							) +
							"or declare an enum, a non-string type or a pattern refusing the shell and query metacharacters"
					);
				}
				continue;
			}
			final EscapeType escapeType = EscapeType.fromString(escape);
			if (escapeType == null) {
				throw new IllegalArgumentException("Tool %s uses an unknown escape type in %s".formatted(toolName, reference));
			}
			if (allowed != null && !allowed.contains(escapeType)) {
				throw new IllegalArgumentException(
					"Tool %s uses %s in a field that takes %s".formatted(toolName, reference, allowed)
				);
			}
			final QuoteRule rule = quoteRule(escapeType);
			if (rule != null && rule.quotes().indexOf(quoteAt(field, matcher.start(), rule)) < 0) {
				throw new IllegalArgumentException(
					"Tool %s must write %s between %s quotes, the only place its escape protects".formatted(
						toolName,
						reference,
						rule.quotes()
					)
				);
			}
		}
		return field;
	}

	/**
	 * How the strings of a language are quoted.
	 *
	 * @param quotes               the quotes an escaped value must be inside
	 * @param escape               the character that makes the next one literal
	 * @param escapeInSingleQuotes whether {@code escape} also works inside single quotes
	 */
	private record QuoteRule(String quotes, char escape, boolean escapeInSingleQuotes) {}

	/**
	 * @param escapeType an escape type
	 * @return the strings the escape type covers the inside of, {@code null} if it protects a value anywhere
	 */
	private static QuoteRule quoteRule(final EscapeType escapeType) {
		return switch (escapeType) {
			case BASH, LINUX -> new QuoteRule("'", '\\', false);
			case POWERSHELL -> new QuoteRule("'", '`', false);
			case SQL -> new QuoteRule("'", '\0', false);
			case WQL -> new QuoteRule("'\"", '\\', true);
			case JSON -> new QuoteRule("\"", '\\', true);
			default -> null;
		};
	}

	/**
	 * @param field    a command, a query or a request
	 * @param position a position in the field
	 * @param rule     how the language quotes its strings
	 * @return the quote the position is inside of, {@code '\0'} outside any quote
	 */
	private static char quoteAt(final String field, final int position, final QuoteRule rule) {
		char quote = '\0';
		for (int i = 0; i < position; i++) {
			final char c = field.charAt(i);
			if (c == rule.escape() && (quote != '\'' || rule.escapeInSingleQuotes())) {
				i++;
			} else if (quote == '\0' && (c == '\'' || c == '"')) {
				quote = c;
			} else if (c == quote) {
				quote = '\0';
			}
		}
		return quote;
	}
}
