package org.metricshub.engine.connector.model.metric;

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

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The MetricType enum represents different types of metrics.
 * Each type is associated with a specific metric key type, and each key type can accept a visitor
 * to implement its specific business logic.
 */
@AllArgsConstructor
public enum MetricType implements IMetricType {
	/**
	 * Gauge Metric Type
	 */
	@JsonAlias("gauge")
	GAUGE(new Gauge()),
	/**
	 * Counter Metric Type
	 */
	@JsonAlias("counter")
	COUNTER(new Counter()),
	/**
	 * Up-Down Counter Metric Type
	 */
	@JsonAlias(value = { "upDownCounter", "up_down_counter" })
	UP_DOWN_COUNTER(new UpDownCounter());

	/**
	 * The metric key type associated with the metric type
	 */
	@Getter
	private IMetricKeyType metricKeyType;

	@Override
	public MetricType get() {
		return this;
	}

	/**
	 * The IMetricKeyType interface represents the key type of a metric.
	 * Each key type can accept a visitor to implement its specific business logic.
	 */
	public interface IMetricKeyType {
		/**
		 * Accept the given visitor
		 *
		 * @param visitor visitor class having its specific business logic
		 */
		void accept(IMetricTypeVisitor visitor);
	}

	/**
	 * The Gauge class represents the key type for Gauge metrics.
	 */
	public static class Gauge implements IMetricKeyType {

		@Override
		public void accept(final IMetricTypeVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * The Counter class represents the key type for Counter metrics.
	 */
	public static class Counter implements IMetricKeyType {

		@Override
		public void accept(final IMetricTypeVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * The UpDownCounter class represents the key type for Up-Down Counter metrics.
	 */
	public static class UpDownCounter implements IMetricKeyType {

		@Override
		public void accept(final IMetricTypeVisitor visitor) {
			visitor.visit(this);
		}
	}

	/**
	 * The IMetricTypeVisitor interface defines the visit method for each metric type.
	 */
	public interface IMetricTypeVisitor {
		/**
		 * Visit the given Gauge type.
		 *
		 * @param gauge the Gauge metric type
		 */
		void visit(Gauge gauge);

		/**
		 * Visit the given Counter type.
		 *
		 * @param counter the Counter metric type
		 */
		void visit(Counter counter);

		/**
		 * Visit the given UpDownCounter type.
		 *
		 * @param upDownCounter the UpDownCounter metric type
		 */
		void visit(UpDownCounter upDownCounter);
	}
}
