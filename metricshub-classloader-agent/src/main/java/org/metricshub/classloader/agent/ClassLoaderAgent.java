package org.metricshub.classloader.agent;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub ClassLoader Agent
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

import java.lang.instrument.Instrumentation;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * Class Loader Agent for dynamic class loading. (Needed for module-winrm and
 * java 11)
 *
 * See: https://cgjennings.ca/articles/java-9-dynamic-jar-loading/
 */
public class ClassLoaderAgent {

	private ClassLoaderAgent() {}

	private static Instrumentation inst;

	/**
	 * Called by the JRE. <em>Do not call this method from user code.</em>
	 *
	 * @param unusedArgs      currently ignored
	 * @param instrumentation provided by the JRE
	 */
	public static void premain(final String unusedArgs, final Instrumentation instrumentation) {
		agentmain(unusedArgs, instrumentation);
	}

	/**
	 * Called by the JRE. <em>Do not call this method from user code.</em>
	 *
	 * @param unusedArgs      currently ignored
	 * @param instrumentation provided by the JRE
	 */
	public static void agentmain(final String unusedArgs, final Instrumentation instrumentation) {
		inst = instrumentation;
	}

	/**
	 * Adds a JAR file to the system class loader's classpath.
	 *
	 * @param jarFile the JAR file to add to the classpath
	 */
	public static synchronized void addToClassPath(final JarFile jarFile) {
		getInstrumentation().ifPresent(instrumentation -> instrumentation.appendToSystemClassLoaderSearch(jarFile));
	}

	/**
	 * Retrieves the current instrumentation instance, if available.
	 *
	 * @return an {@code Optional} containing the current instrumentation instance, or an empty {@code Optional} if not available
	 */
	static Optional<Instrumentation> getInstrumentation() {
		return Optional.ofNullable(inst);
	}
}
