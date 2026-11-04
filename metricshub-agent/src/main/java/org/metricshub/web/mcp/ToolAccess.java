package org.metricshub.web.mcp;

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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The access class of an agent tool, which MetricsHub Central turns into the permission a user needs to
 * call it: {@code tools:read} for {@link Access#READ}, {@code tools:write} and an approval for every call
 * for {@link Access#WRITE}. Required on every {@code @Tool} method of an {@link IMCPToolService}: the agent
 * refuses to start with one that has none, so a new tool cannot ship unclassified.
 * <p>
 * The tools declared by the protocol extensions and the connectors carry no annotation: theirs follows
 * their {@code destructive} flag.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolAccess {
	/**
	 * @return the tool's access class
	 */
	Access value();

	/**
	 * What a tool lets its caller do on the monitored hosts.
	 */
	enum Access {
		/**
		 * Reads: it changes nothing on a host, even when it makes the agent connect to it and collect.
		 */
		READ,
		/**
		 * Changes the state of a host (a command, a reboot, a kill).
		 */
		WRITE;

		/**
		 * @return the class as the tunnel carries it, {@code read} or {@code write}
		 */
		public String wire() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
