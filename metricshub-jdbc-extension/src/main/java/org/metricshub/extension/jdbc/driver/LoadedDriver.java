package org.metricshub.extension.jdbc.driver;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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

import java.sql.Driver;

/**
 * Output of {@link JdbcDriverRegistry#resolve(String, String)}: a ready-to-use vendor driver
 * instance together with the {@link ClassLoader} it was loaded from and the descriptor that
 * produced it.
 *
 * <p>For built-in drivers, {@code classLoader} is the JDBC extension's own classloader and no
 * {@link IsolatedDriverClassLoader} is created. For all other drivers, {@code classLoader} is the
 * dedicated isolated loader keyed by {@code (driverClass, explicitJarPath)}.
 *
 * @param driver      the vendor {@link Driver} instance.
 * @param classLoader the classloader that defined {@code driver}'s class.
 * @param descriptor  the descriptor that produced this driver.
 */
public record LoadedDriver(Driver driver, ClassLoader classLoader, JdbcDriverDescriptor descriptor) {}
