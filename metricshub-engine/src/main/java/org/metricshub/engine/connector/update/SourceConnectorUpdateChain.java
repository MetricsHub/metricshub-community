package org.metricshub.engine.connector.update;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.VERTICAL_BAR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * Abstract class for the source connector update chain, providing functionality to update source dependencies.
 */
public abstract class SourceConnectorUpdateChain extends AbstractConnectorUpdateChain {

	/**
	 * Get the adjacency collection of the sources in a job or under pre section
	 *
	 * @param sources       The sources map defined in the monitor's task or under the pre section.
	 * @param context       Context pattern
	 * @param sourceGroup  The index of a capturing source identifier group in the matcher's context pattern
	 * @return A list of source levels. Structure example: [ [Source1, Source2], [Source3] ]
	 */
	protected List<Set<String>> updateSourceDependency(
		final Map<String, Source> sources,
		final Pattern context,
		final int sourceGroup
	) {
		if (sources.isEmpty()) {
			return new ArrayList<>();
		}

		// Source path is a string like: ${source::monitors.enclosure.discovery.sources.Source1} // NOSONAR on comment
		// Source identifier (id) is a string like: Source1

		// mapLevels is key-value pairs where the key is the source level integer value and
		// the value contains a set of source identifiers for the corresponding level
		final Map<Integer, Set<String>> mapLevels = new LinkedHashMap<>();

		// Build a key-value pairs where the key is the source id and the values are the dependency identifiers
		// of the current context. Thus, dependencies can be retrieved easily when looping over the sources.
		// E.g:
		// source1 -> []
		// source2 -> []
		// source3 -> [source1, source2]
		// Open a stream and call buildDependencyEntry to fetch the dependency identifiers on the same context
		final Map<String, Set<String>> sourceIdToDependencyIds = sources
			.entrySet()
			.stream()
			.map(entry -> buildDependencyEntry(entry, context, sourceGroup))
			.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (v1, v2) -> v1));

		// All the source identifiers are pending
		final Set<String> pendingSourceIds = sources.entrySet().stream().map(Entry::getKey).collect(Collectors.toSet());

		// Initialize the Map of found dependencies
		// With this map we will avoid looping over all the dependencies each time if all the
		// levels are not computed yet.
		final Map<String, Map<String, Integer>> foundDependencies = sourceIdToDependencyIds
			.entrySet()
			.stream()
			.filter(entry -> !entry.getValue().isEmpty())
			.map(entry -> Map.entry(entry.getKey(), new HashMap<String, Integer>()))
			.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (v1, v2) -> v1));

		// Loop until all the pending source identifiers are processed
		do {
			final Set<String> processedSourceIds = new HashSet<>();

			// Loop over pending source identifiers
			for (final String sourceId : pendingSourceIds) {
				// Get dependency identifiers
				final Set<String> dependencyIds = sourceIdToDependencyIds.get(sourceId);

				// Does the current source depend on one or more sources from the current job context?
				if (!dependencyIds.isEmpty()) {
					// Dependencies already found
					final Map<String, Integer> found = foundDependencies.get(sourceId);

					// Find dependencies levels.
					findDependencyLevels(mapLevels, dependencyIds, found);

					// It means that all the dependencies are found
					// In that case, let's add the source in the next level
					if (found.size() == dependencyIds.size()) {
						addSourceIdToLevel(mapLevels, sourceId, Collections.max(found.values()) + 1, processedSourceIds);
					}
				} else {
					// There is no dependency or the source depends on external dependencies.
					// let's add the source at the first level
					addSourceIdToLevel(mapLevels, sourceId, 1, processedSourceIds);
				}
			}

			// Remove processed sources, so that we continue the loop on the pending source identifiers
			// to find next levels
			pendingSourceIds.removeAll(processedSourceIds);
		} while (!pendingSourceIds.isEmpty());

		// Build the final result
		// A result like: [ [Source1, Source2], [Source3] ]
		return mapLevels.values().stream().map(Function.identity()).collect(Collectors.toList()); //NOSONAR;
	}

	/**
	 * Loop over remaining dependencies in dependencyIds then for each dependency id try to find its
	 * level.
	 *
	 * @param mapLevels Map of integer level to source identifiers
	 * @param dependencyIds dependency source identifiers
	 * @param found The dependencies that have already been found and removed from dependencyIds
	 */
	private void findDependencyLevels(
		final Map<Integer, Set<String>> mapLevels,
		final Set<String> dependencyIds,
		final Map<String, Integer> found
	) {
		final Set<String> remaining = dependencyIds.stream().collect(Collectors.toSet());
		remaining.removeAll(found.keySet());

		for (String depId : remaining) {
			mapLevels
				.entrySet()
				.stream()
				.filter(entry -> entry.getValue().contains(depId))
				.findFirst()
				.ifPresent(entry -> found.put(depId, entry.getKey()));
		}
	}

	/**
	 * Get the dependency of the given source. Use the context pattern to
	 * extract all the dependency identifiers belonging to the current monitor task or pre section.
	 *
	 * @param sourceEntry The source entry (key-value) we want to extract its dependencies
	 * @param context The context pattern which helps extracting a dependency identifier from a path reference
	 * that only belongs to the current monitor task.
	 * @param sourceGroup  The index of a capturing source identifier group in the matcher's context pattern
	 * @return Entry of String to Set where String key is the source id and
	 * the Set contains the dependency source identifiers.
	 * Example:
	 * source3 -> [source1, source2]<br>
	 */
	private Entry<String, Set<String>> buildDependencyEntry(
		final Entry<String, Source> sourceEntry,
		final Pattern context,
		final int sourceGroup
	) {
		final Source source = sourceEntry.getValue();
		final String sourceId = sourceEntry.getKey();
		final Set<String> references = source.getReferences();
		return getContextDependencies(sourceId, context, sourceGroup, references.toArray(new String[references.size()]));
	}

	/**
	 * Get the dependency of the given source identifier. Use the context pattern to
	 * extract all the dependency identifiers belonging to the current monitor task or pre section.
	 *
	 * @param sourceId      The source identifier we want to extract its dependencies
	 * @param context       The context pattern which helps extracting dependency identifier
	 * that only belongs to the current monitor task.
	 * @param sourceGroup   The index of a capturing source identifier group in the matcher's context pattern.
	 * @param refs          Source identifier references from which we want to extract dependency identifiers.
	 * @return new Entry instance of source id to its dependency source identifiers in the same context
	 */
	private Entry<String, Set<String>> getContextDependencies(
		final String sourceId,
		final Pattern context,
		final int sourceGroup,
		final String... refs
	) {
		final Set<String> dependencies = new HashSet<>();

		// Loop over the references and extract sources
		for (String ref : refs) {
			final Matcher includeMatcher = context.matcher(ref);

			while (includeMatcher.find()) {
				// Get source id, example: Source1
				// Means this source identifier is defined in the current monitor task job or beforeAll/afterAll sources context.
				dependencies.add(includeMatcher.group(sourceGroup));
			}
		}
		return Map.entry(sourceId, dependencies);
	}

	/**
	 * Add the given source identifier to the map levels and update the processed
	 * sources set.
	 *
	 * @param mapLevels key-value pairs where the key is the source level and
	 * the value contains a set of source identifiers for the corresponding level
	 * @param sourceId the source id to be inserted in processedSources
	 * @param level the level we wish to insert (integer value)
	 * @param processedSources processed sources which we want to add the source to.
	 *
	 */
	private static void addSourceIdToLevel(
		final Map<Integer, Set<String>> mapLevels,
		final String sourceId,
		final int level,
		final Set<String> processedSources
	) {
		// If the level is not yet available create it then add the key
		mapLevels.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(sourceId);

		// Add the source in the processedSources
		processedSources.add(sourceId);
	}

	/**
	 * Return the source identifiers REGEX such as source1|source2|source3
	 *
	 * @param sources monitor task sources or beforeAll/afterAll sources
	 * @return String value
	 */
	protected String getSourceIdentifiersRegex(final Map<String, Source> sources) {
		return sources.keySet().stream().map(Pattern::quote).collect(Collectors.joining(VERTICAL_BAR));
	}
}
