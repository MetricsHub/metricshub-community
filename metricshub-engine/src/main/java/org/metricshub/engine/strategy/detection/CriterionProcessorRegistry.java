package org.metricshub.engine.strategy.detection;

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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ProductRequirementsCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;

/**
 * Registry of criterion processors
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CriterionProcessorRegistry {

	// @formatter:off
	private static final Map<Class<? extends Criterion>, BiFunction<Criterion, CriterionProcessor, CriterionTestResult>> PROCESSORS = new HashMap<>();

	static {
		registerProcessor(DeviceTypeCriterion.class, (criterion, processor) -> processor.process((DeviceTypeCriterion) criterion));
		registerProcessor(HttpCriterion.class, (criterion, processor) -> processor.process((HttpCriterion) criterion));
		registerProcessor(IpmiCriterion.class, (criterion, processor) -> processor.process((IpmiCriterion) criterion));
		registerProcessor(CommandLineCriterion.class, (criterion, processor) -> processor.process((CommandLineCriterion) criterion));
		registerProcessor(ProcessCriterion.class, (criterion, processor) -> processor.process((ProcessCriterion) criterion));
		registerProcessor(ProductRequirementsCriterion.class, (criterion, processor) -> processor.process((ProductRequirementsCriterion) criterion));
		registerProcessor(ServiceCriterion.class, (criterion, processor) -> processor.process((ServiceCriterion) criterion));
		registerProcessor(SnmpGetCriterion.class, (criterion, processor) -> processor.process((SnmpGetCriterion) criterion));
		registerProcessor(SnmpGetNextCriterion.class, (criterion, processor) -> processor.process((SnmpGetNextCriterion) criterion));
		registerProcessor(WmiCriterion.class, (criterion, processor) -> processor.process((WmiCriterion) criterion));
		registerProcessor(WbemCriterion.class, (criterion, processor) -> processor.process((WbemCriterion) criterion));
		registerProcessor(SqlCriterion.class, (criterion, processor) -> processor.process((SqlCriterion) criterion));
	}

	// @formatter:on

	/**
	 * Register processor for criterion class and processor.
	 *
	 * @param criterionClass The criterion class.
	 * @param processor      The processor function for the criterion.
	 */
	private static void registerProcessor(
		final Class<? extends Criterion> criterionClass,
		final BiFunction<Criterion, CriterionProcessor, CriterionTestResult> processor
	) {
		PROCESSORS.put(criterionClass, processor);
	}

	/**
	 * Get processor for criterion class.
	 *
	 * @param criterion The criterion class to get processor for.
	 * @return The processor function for the criterion or the default processor through extension.
	 */
	public static BiFunction<Criterion, CriterionProcessor, CriterionTestResult> getProcessor(final Criterion criterion) {
		return PROCESSORS.getOrDefault(criterion.getClass(), (c, p) -> p.processCriterionThroughExtension(c));
	}
}
