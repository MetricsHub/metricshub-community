package org.metricshub.web.mcp.tool;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.metricshub.web.mcp.ToolAccess;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.util.ClassUtils;

/**
 * One of the agent's own {@code @Tool} methods, with the access class its {@link ToolAccess} declares.
 *
 * @param delegate the Spring AI callback that runs the method
 * @param access   the method's access class
 */
public record ProductToolCallback(ToolCallback delegate, ToolAccess.Access access) implements ToolCallback {
	/**
	 * Gives each callback the access class of its {@code @Tool} method.
	 *
	 * @param callbacks the callbacks Spring AI built from {@code tools}
	 * @param tools     the beans whose {@code @Tool} methods they run
	 * @return the same callbacks, classified
	 * @throws IllegalStateException when a {@code @Tool} method has no {@link ToolAccess}: a tool the agent could
	 *                               not tell Central the class of is a defect, never something to guess
	 */
	public static ToolCallback[] classify(final ToolCallback[] callbacks, final Collection<?> tools) {
		final Map<String, ToolAccess.Access> byName = new HashMap<>();
		for (final Object tool : tools) {
			for (final Method method : ClassUtils.getUserClass(tool).getMethods()) {
				if (method.isAnnotationPresent(Tool.class)) {
					final ToolAccess access = method.getAnnotation(ToolAccess.class);
					if (access == null) {
						throw new IllegalStateException(
							"Tool method %s.%s has no @ToolAccess".formatted(method.getDeclaringClass().getName(), method.getName())
						);
					}
					byName.put(ToolUtils.getToolName(method), access.value());
				}
			}
		}
		final List<ToolCallback> classified = new ArrayList<>(callbacks.length);
		for (final ToolCallback callback : callbacks) {
			final ToolAccess.Access access = byName.get(callback.getToolDefinition().name());
			if (access == null) {
				throw new IllegalStateException("Tool " + callback.getToolDefinition().name() + " has no @ToolAccess");
			}
			classified.add(new ProductToolCallback(callback, access));
		}
		return classified.toArray(ToolCallback[]::new);
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return delegate.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return delegate.getToolMetadata();
	}

	@Override
	public String call(final String toolInput) {
		return delegate.call(toolInput);
	}

	@Override
	public String call(final String toolInput, final ToolContext toolContext) {
		return delegate.call(toolInput, toolContext);
	}
}
