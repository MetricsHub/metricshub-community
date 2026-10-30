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
import org.metricshub.engine.connector.model.monitor.task.source.Source;
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
	 * Describes this tool for the LLM, once its sources are checked to insert every free-text parameter escaped.
	 *
	 * @param name the name the connector declares the tool under
	 * @return the definition of the tool
	 * @throws IllegalArgumentException if a source inserts a free-text parameter unescaped, or with an unknown
	 *                                  escape type
	 */
	public ToolDefinition toDefinition(final String name) {
		for (final Source source : sources.values()) {
			// Copies: update() is the visitor of every field a reference is replaced in
			source.copy().update(value -> checkParameterReferences(name, value));
			source.getComputes().forEach(compute -> compute.copy().update(value -> checkParameterReferences(name, value)));
		}
		return new ToolDefinition(name, description, parameters, destructive);
	}

	/**
	 * Checks the {@code ${param::name}} references of a source field: a free-text parameter (see
	 * {@link ToolParameter#isFreeText()}) may only be inserted through {@code ${esc(TYPE)::param::name}}, as it could
	 * otherwise close a quote and run a command or a query of its own.
	 *
	 * @param toolName the tool name, for the error message
	 * @param value    the field of a source or a compute
	 * @return the field, unchanged
	 */
	private String checkParameterReferences(final String toolName, final String value) {
		if (value == null) {
			return null;
		}
		final Matcher matcher = PARAMETER_REFERENCE.matcher(value);
		while (matcher.find()) {
			final String escape = matcher.group("esc");
			final ToolParameter parameter = parameters.get(matcher.group("name").trim());
			if (escape != null && EscapeType.fromString(escape) == null) {
				throw new IllegalArgumentException(
					"Tool %s uses the unknown escape type %s in %s".formatted(toolName, escape, matcher.group())
				);
			}
			if (escape == null && parameter != null && parameter.isFreeText()) {
				throw new IllegalArgumentException(
					"Tool %s inserts the free-text parameter %s unescaped: write ${esc(TYPE)::param::%s}, or give the parameter a pattern, an enum or a non-string type".formatted(
						toolName,
						matcher.group("name"),
						matcher.group("name")
					)
				);
			}
		}
		return value;
	}
}
