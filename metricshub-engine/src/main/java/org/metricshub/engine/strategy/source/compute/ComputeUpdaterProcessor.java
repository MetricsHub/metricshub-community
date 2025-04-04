package org.metricshub.engine.strategy.source.compute;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Add;
import org.metricshub.engine.connector.model.monitor.task.source.compute.And;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ArrayTranslate;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Convert;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Divide;
import org.metricshub.engine.connector.model.monitor.task.source.compute.DuplicateColumn;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ExcludeMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Extract;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ExtractPropertyFromWbemPath;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepColumns;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepOnlyMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Multiply;
import org.metricshub.engine.connector.model.monitor.task.source.compute.PerBitTranslation;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Prepend;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Substring;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Subtract;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Translate;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;
import org.metricshub.engine.strategy.source.SourceUpdaterProcessor;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code ComputeUpdaterProcessor} class is responsible for processing various compute operations such as
 * array translation, logical AND, addition, AWK, conversion, division, duplicating columns, excluding matching lines,
 * extracting data, extracting property from Wbem path, JSON to CSV conversion, keeping specified columns,
 * keeping only matching lines, left concatenation, multiplication, per-bit translation, replacement, right concatenation,
 * subtraction, substring, translation, and XML to CSV conversion. It is part of the compute processing strategy and
 * delegates operations to the provided {@link IComputeProcessor} instance.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComputeUpdaterProcessor implements IComputeProcessor {

	private IComputeProcessor computeProcessor;
	private TelemetryManager telemetryManager;
	private String connectorId;
	private Map<String, String> attributes;

	@Override
	public void process(final ArrayTranslate arrayTranslate) {
		arrayTranslate.accept(computeProcessor);
	}

	@Override
	public void process(final And and) {
		processCompute(and);
	}

	@Override
	public void process(final Add add) {
		processCompute(add);
	}

	@Override
	public void process(final Awk awk) {
		processCompute(awk);
	}

	@Override
	public void process(final Convert convert) {
		processCompute(convert);
	}

	@Override
	public void process(final Divide divide) {
		processCompute(divide);
	}

	@Override
	public void process(final DuplicateColumn duplicateColumn) {
		processCompute(duplicateColumn);
	}

	@Override
	public void process(final ExcludeMatchingLines excludeMatchingLines) {
		processCompute(excludeMatchingLines);
	}

	@Override
	public void process(final Extract extract) {
		processCompute(extract);
	}

	@Override
	public void process(final ExtractPropertyFromWbemPath extractPropertyFromWbemPath) {
		processCompute(extractPropertyFromWbemPath);
	}

	@Override
	public void process(final Json2Csv json2csv) {
		processCompute(json2csv);
	}

	@Override
	public void process(final KeepColumns keepColumns) {
		processCompute(keepColumns);
	}

	@Override
	public void process(final KeepOnlyMatchingLines keepOnlyMatchingLines) {
		processCompute(keepOnlyMatchingLines);
	}

	@Override
	public void process(final Prepend prepend) {
		processCompute(prepend);
	}

	@Override
	public void process(final Multiply multiply) {
		processCompute(multiply);
	}

	@Override
	public void process(final PerBitTranslation perBitTranslation) {
		processCompute(perBitTranslation);
	}

	@Override
	public void process(final Replace replace) {
		processCompute(replace);
	}

	@Override
	public void process(final Append append) {
		processCompute(append);
	}

	@Override
	public void process(final Subtract subtract) {
		processCompute(subtract);
	}

	@Override
	public void process(final Substring substring) {
		processCompute(substring);
	}

	@Override
	public void process(final Translate translate) {
		processCompute(translate);
	}

	@Override
	public void process(final Xml2Csv xml2csv) {
		processCompute(xml2csv);
	}

	/**
	 * Copy the given compute, replace device id when running mono-instance collects, replace
	 * source reference and finally call the compute visitor
	 *
	 * @param origin original compute instance
	 */
	private void processCompute(final Compute origin) {
		// Deep copy
		final Compute copy = origin.copy();

		// Replace device id (mono instance)
		copy.update(value -> SourceUpdaterProcessor.replaceAttributeReferences(value, attributes));

		// Replace source reference
		copy.update(value -> replaceSourceReference(value, copy));

		// Call the next compute visitor
		copy.accept(computeProcessor);
	}

	/**
	 * Replace referenced source in the given compute attributes
	 *
	 * @param value The value containing a source reference such as %Enclosure.Discovery.Source(1)%.
	 * @param compute {@link Compute} instance we wish to update with the content of the referenced source
	 * @return String value
	 */
	private String replaceSourceReference(final String value, final Compute compute) {
		// Null check
		if (value == null) {
			return value;
		}

		return SourceUpdaterProcessor.replaceSourceReferenceContent(
			value,
			telemetryManager,
			connectorId,
			compute.getClass().getSimpleName(),
			compute.getType()
		);
	}
}
