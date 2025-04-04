package org.metricshub.engine.strategy.source;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * The OrderedSources class is responsible for building a list of ordered sources based on either the
 * execution order or the source dependency tree. It includes methods for ordering and validating the sources.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@Slf4j
public class OrderedSources {

	@Getter
	@Default
	private List<Source> sources = new ArrayList<>();

	/**
	 * The OrderedSourcesBuilder class extends the generated builder for additional methods related to
	 * building the list of ordered sources.
	 *
	 * @see OrderedSources
	 */
	public static class OrderedSourcesBuilder {

		/**
		 * Build the list of ordered sources using the execution order or
		 * the source dependency tree.
		 *
		 * @param sources        Map of source instances
		 * @param executionOrder The source order defined by the connector
		 * @param sourceDepTree  The source dependency tree this is built by the compiler on each monitor's job
		 * @param jobInfo        Information about the job (discovery, collect, etc.) used for logging
		 * @return this {@link OrderedSourcesBuilder}
		 */
		public OrderedSourcesBuilder sources(
			final Map<String, Source> sources,
			final List<String> executionOrder,
			final List<Set<String>> sourceDepTree,
			final JobInfo jobInfo
		) {
			if (sources == null || sources.isEmpty()) {
				this.sources$value = new ArrayList<>();
				this.sources$set = true;
				return this;
			}

			if (executionOrder != null && !executionOrder.isEmpty()) {
				return orderSources(sources, executionOrder, "execution order", jobInfo);
			} else if (sourceDepTree != null && !sourceDepTree.isEmpty()) {
				return orderSources(
					sources,
					sourceDepTree.stream().flatMap(Collection::stream).collect(Collectors.toList()), // NOSONAR
					"dependency tree",
					jobInfo
				);
			}

			this.sources$value = sources.values().stream().collect(Collectors.toList()); // NOSONAR
			this.sources$set = true;
			return this;
		}

		/**
		 * Order the given source map based on the provided order
		 *
		 * @param sources          Map of source instances
		 * @param order            The order list of the sources, this order list contains the name of each source
		 * @param orderDescription Description used for logging in case of errors
		 * @param jobInfo          Information about the job (discovery, collect, etc.) used for logging
		 * @return this {@link OrderedSourcesBuilder}
		 */
		OrderedSourcesBuilder orderSources(
			final Map<String, Source> sources,
			final List<String> order,
			final String orderDescription,
			final JobInfo jobInfo
		) {
			if (order.size() != sources.size()) {
				final String message = String.format(
					"Hostname %s - The %s size (%d) is not equals to the sources size (%d)." +
					" The sources will not be processed. Context: connector %s, monitor type: %s, job: %s.",
					jobInfo.getHostname(),
					orderDescription,
					order.size(),
					sources.size(),
					jobInfo.getConnectorId(),
					jobInfo.getMonitorType(),
					jobInfo.getJobName()
				);
				log.error(message);
				throw new IllegalStateException(message);
			}

			this.sources$value =
				sources
					.entrySet()
					.stream()
					.sorted(Comparator.comparing(entry -> findSourcePosition(entry.getKey(), order, orderDescription, jobInfo)))
					.map(Entry::getValue)
					.collect(Collectors.toList()); // NOSONAR

			this.sources$set = true;
			return this;
		}

		/**
		 * Find the position of the first occurrence of the specified source name in the order list
		 *
		 * @param sourceName       The name of the source to find used
		 * @param order            The order list of the sources, this order list contains the name of each source
		 * @param orderDescription Description used for logging in case of errors
		 * @param jobInfo          Information about the job (discovery, collect, etc.) used for logging
		 * @return int value
		 */
		int findSourcePosition(
			final String sourceName,
			final List<String> order,
			final String orderDescription,
			final JobInfo jobInfo
		) {
			final int index = order.indexOf(sourceName);

			if (index == -1) {
				final String message = String.format(
					"Hostname %s - The source (%s) listed in the %s is not defined." +
					" The sources will not be processed. Context: connector %s, monitor type: %s, job: %s.",
					jobInfo.getHostname(),
					sourceName,
					orderDescription,
					jobInfo.getConnectorId(),
					jobInfo.getMonitorType(),
					jobInfo.getJobName()
				);
				log.error(message);
				throw new IllegalStateException(message);
			}

			return index;
		}
	}
}
