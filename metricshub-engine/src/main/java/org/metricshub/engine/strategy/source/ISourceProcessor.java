package org.metricshub.engine.strategy.source;

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

import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;

/**
 * Interface for processing different types of data sources.
 * Implementations of this interface handle specific source types and provide a SourceTable as a result.
 */
public interface ISourceProcessor {
	/**
	 * Process the CopySource and return a SourceTable.
	 *
	 * @param copySource The CopySource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(CopySource copySource);

	/**
	 * Process the HttpSource and return a SourceTable.
	 *
	 * @param httpSource The HttpSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(HttpSource httpSource);

	/**
	 * Process the IpmiSource and return a SourceTable.
	 *
	 * @param ipmiSource The IpmiSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(IpmiSource ipmiSource);

	/**
	 * Process the CommandLineSource and return a SourceTable.
	 *
	 * @param commandLineSource The CommandLineSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(CommandLineSource commandLineSource);

	/**
	 * Process the SnmpGetSource and return a SourceTable.
	 *
	 * @param snmpSource The SnmpGetSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(SnmpGetSource snmpSource);

	/**
	 * Process the SnmpTableSource and return a SourceTable.
	 *
	 * @param snmpTableSource The SnmpTableSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(SnmpTableSource snmpTableSource);

	/**
	 * Process the StaticSource and return a SourceTable.
	 *
	 * @param staticSource The StaticSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(StaticSource staticSource);

	/**
	 * Process the TableJoinSource and return a SourceTable.
	 *
	 * @param tableJoinSource The TableJoinSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(TableJoinSource tableJoinSource);

	/**
	 * Process the TableUnionSource and return a SourceTable.
	 *
	 * @param tableUnionSource The TableUnionSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(TableUnionSource tableUnionSource);

	/**
	 * Process the WbemSource and return a SourceTable.
	 *
	 * @param wbemSource The WbemSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(WbemSource wbemSource);

	/**
	 * Process the WmiSource and return a SourceTable.
	 *
	 * @param wmiSource The WmiSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(WmiSource wmiSource);

	/**
	 * Process the {@link InternalDbQuerySource} and return a SourceTable.
	 *
	 * @param internalDbQuery The {@link InternalDbQuerySource} to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(InternalDbQuerySource internalDbQuery);

	/**
	 * Process the {@link JawkSource} and return a SourceTable.
	 *
	 * @param jawkSource The {@link JawkSource} to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(JawkSource jawkSource);

	/**
	 * This method processes {@link SqlSource} source
	 * @param sqlSource {@link SqlSource} source instance
	 * @return {@link SourceTable} instance
	 */
	SourceTable process(SqlSource sqlSource);

	/**
	 * Processes a JmxSource and returns a SourceTable.
	 *
	 * @param jmxSource The JmxSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(JmxSource jmxSource);

	/**
	 * Processes an EventLogSource and returns a SourceTable.
	 *
	 * @param eventLogSource The EventLogSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(EventLogSource eventLogSource);

	/**
	 * Processes a FileSource and returns a SourceTable.
	 *
	 * @param fileSource The FileSource to process.
	 * @return The SourceTable result.
	 */
	SourceTable process(FileSource fileSource);
}
