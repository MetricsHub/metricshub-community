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
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.TextUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;

/**
 * Interface for processing different types of data sources.
 * Implementations of this interface handle specific source types and provide a SourceResult as a result.
 */
public interface ISourceProcessor {
	/**
	 * Process the CopySource and return a SourceResult.
	 *
	 * @param copySource The CopySource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(CopySource copySource);

	/**
	 * Process the HttpSource and return a SourceResult.
	 *
	 * @param httpSource The HttpSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(HttpSource httpSource);

	/**
	 * Process the IpmiSource and return a SourceResult.
	 *
	 * @param ipmiSource The IpmiSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(IpmiSource ipmiSource);

	/**
	 * Process the IpmiSelSource and return a SourceResult.
	 *
	 * @param ipmiSelSource The IpmiSelSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(IpmiSelSource ipmiSelSource);

	/**
	 * Process the IpmiRawSource and return a SourceResult.
	 *
	 * @param ipmiRawSource The IpmiRawSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(IpmiRawSource ipmiRawSource);

	/**
	 * Process the CommandLineSource and return a SourceResult.
	 *
	 * @param commandLineSource The CommandLineSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(CommandLineSource commandLineSource);

	/**
	 * Process the SnmpGetSource and return a SourceResult.
	 *
	 * @param snmpSource The SnmpGetSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(SnmpGetSource snmpSource);

	/**
	 * Process the SnmpTableSource and return a SourceResult.
	 *
	 * @param snmpTableSource The SnmpTableSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(SnmpTableSource snmpTableSource);

	/**
	 * Process the StaticSource and return a SourceResult.
	 *
	 * @param staticSource The StaticSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(StaticSource staticSource);

	/**
	 * Process the TableJoinSource and return a SourceResult.
	 *
	 * @param tableJoinSource The TableJoinSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(TableJoinSource tableJoinSource);

	/**
	 * Process the TableUnionSource and return a SourceResult.
	 *
	 * @param tableUnionSource The TableUnionSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(TableUnionSource tableUnionSource);

	/**
	 * Process the TextUnionSource and return a SourceResult.
	 *
	 * @param textUnionSource The TextUnionSource to process.
	 * @return The SourceResult, a text.
	 */
	SourceResult process(TextUnionSource textUnionSource);

	/**
	 * Process the WbemSource and return a SourceResult.
	 *
	 * @param wbemSource The WbemSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(WbemSource wbemSource);

	/**
	 * Process the WmiSource and return a SourceResult.
	 *
	 * @param wmiSource The WmiSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(WmiSource wmiSource);

	/**
	 * Process the {@link InternalDbQuerySource} and return a SourceResult.
	 *
	 * @param internalDbQuery The {@link InternalDbQuerySource} to process.
	 * @return The SourceResult.
	 */
	SourceResult process(InternalDbQuerySource internalDbQuery);

	/**
	 * Process the {@link JawkSource} and return a SourceResult.
	 *
	 * @param jawkSource The {@link JawkSource} to process.
	 * @return The SourceResult.
	 */
	SourceResult process(JawkSource jawkSource);

	/**
	 * This method processes {@link SqlSource} source
	 * @param sqlSource {@link SqlSource} source instance
	 * @return {@link SourceResult} instance
	 */
	SourceResult process(SqlSource sqlSource);

	/**
	 * Processes a JmxSource and returns a SourceResult.
	 *
	 * @param jmxSource The JmxSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(JmxSource jmxSource);

	/**
	 * Processes an EventLogSource and returns a SourceResult.
	 *
	 * @param eventLogSource The EventLogSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(EventLogSource eventLogSource);

	/**
	 * Processes a FileSource and returns a SourceResult.
	 *
	 * @param fileSource The FileSource to process.
	 * @return The SourceResult.
	 */
	SourceResult process(FileSource fileSource);
}
