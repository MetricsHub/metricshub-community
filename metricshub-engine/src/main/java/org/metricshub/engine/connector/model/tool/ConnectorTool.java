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
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.metricshub.engine.common.helpers.JsonHelper;
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
	 * A {@code ${param::name}} reference, with its optional {@code esc(TYPE)::} prefix. {@code ${attribute::name}}
	 * too: a tool's arguments are its attributes, replaced from the same map.
	 */
	private static final Pattern PARAMETER_REFERENCE = Pattern.compile(
		"\\$\\{(?:esc\\((?<esc>[^)]*)\\)::)?(?:param|attribute)::(?<name>[^}]+)\\}",
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
	 * Describes this tool for the LLM. Its string parameters declaring neither an enum nor a pattern take the
	 * {@link ToolParameter#DEFAULT_PATTERN}. Where the sources insert the parameters is not checked here: that is
	 * for the review of the connector.
	 *
	 * @param name the name the connector declares the tool under
	 * @return the definition of the tool
	 * @throws IllegalArgumentException if a reference names an undeclared parameter, an optional parameter without
	 *                                  default, or an unknown escape type: the reference would stay in the source
	 */
	public ToolDefinition toDefinition(final String name) {
		final Matcher matcher = PARAMETER_REFERENCE.matcher(serializedSources(name));
		while (matcher.find()) {
			final String reference = matcher.group();
			final ToolParameter parameter = parameters.get(matcher.group("name").trim());
			if (parameter == null) {
				throw new IllegalArgumentException(
					"Tool %s references %s, which it does not declare".formatted(name, reference)
				);
			}
			if (!parameter.required() && parameter.defaultValue() == null) {
				throw new IllegalArgumentException(
					"Tool %s references %s, optional without a default: declare a default (even '') or make it required".formatted(
						name,
						reference
					)
				);
			}
			final String escape = matcher.group("esc");
			if (escape != null && EscapeType.fromString(escape) == null) {
				throw new IllegalArgumentException("Tool %s uses an unknown escape type in %s".formatted(name, reference));
			}
		}
		final Map<String, ToolParameter> constrained = new LinkedHashMap<>();
		parameters.forEach((key, parameter) -> constrained.put(key, parameter.withDefaultPattern()));
		return new ToolDefinition(name, description, constrained, destructive);
	}

	/**
	 * @param name the tool name, for the error message
	 * @return the sources of the tool and their computes, every field included, as JSON
	 */
	private String serializedSources(final String name) {
		try {
			return JsonHelper.buildObjectMapper().writeValueAsString(sources);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Tool %s has sources that cannot be read".formatted(name), e);
		}
	}
}
