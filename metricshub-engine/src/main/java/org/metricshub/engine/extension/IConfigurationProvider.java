package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Contract for configuration providers.<br>
 */
public interface IConfigurationProvider {
	/**
	 * Load configuration fragments based on the provider's implementation.
	 *
	 * @param path The path to the configuration directory.
	 * @return A collection of {@link JsonNode} representing the configuration fragments.
	 */
	Collection<JsonNode> load(Path path);

	/**
	 * Load the fragments of the UI managed configuration file ({@code metricshub-ui.yaml}).
	 * <p>
	 * These fragments are merged after the regular {@link #load(Path)} fragments of
	 * <b>all</b> providers, so the UI configuration always takes precedence over any
	 * other configuration source, regardless of provider discovery order or file
	 * listing order. Providers that do not manage the UI configuration return an
	 * empty collection.
	 *
	 * @param path The path to the configuration directory.
	 * @return A collection of {@link JsonNode} representing the UI configuration fragments.
	 */
	default Collection<JsonNode> loadUiFragments(Path path) {
		return Collections.emptyList();
	}

	/**
	 * Get the set of the file extensions that this configuration provider can handle.
	 *
	 * @return A collection of file extensions.
	 */
	Set<String> getFileExtensions();

	/**
	 * Render the given template file into its resulting configuration content, without
	 * parsing it into configuration fragments.
	 * <p>
	 * This is used by tooling (for example, the web configuration editor) to preview the
	 * output a provider produces from a single template. Providers that do not support
	 * template rendering return an empty {@link Optional}.
	 *
	 * @param templateFile The path to the template file to render.
	 * @return An {@link Optional} containing the rendered content, or empty if this provider
	 *         does not handle the given file.
	 * @throws Exception if the template cannot be rendered.
	 */
	default Optional<String> renderTemplate(Path templateFile) throws Exception {
		return Optional.empty();
	}

	/**
	 * Returns the re-evaluations this provider declares. A configuration source whose output can
	 * change over time (for example a template that queries a CMDB) exposes one entry per re-evaluable
	 * unit, so the agent can drive its periodic re-evaluation. Providers with nothing to re-evaluate
	 * return an empty collection.
	 *
	 * @return the scheduled re-evaluations, empty by default.
	 */
	default Collection<ScheduledReEvaluation> getScheduledReEvaluations() {
		return Collections.emptyList();
	}

	/**
	 * Re-evaluates the unit with the given id and returns the freshly produced fragment. Returns an
	 * empty {@link Optional} when the id is unknown or the re-evaluation failed, in which case the
	 * previously published fragment is kept.
	 *
	 * @param reEvaluationId an id from {@link #getScheduledReEvaluations()}.
	 * @return the newly produced fragment, or empty.
	 */
	default Optional<JsonNode> reevaluate(String reEvaluationId) {
		return Optional.empty();
	}

	/**
	 * Returns the fragment currently published for the given re-evaluation, as produced by the last
	 * load or re-evaluation. Used to seed change detection so a first firing with unchanged data does
	 * not trigger a reload. Reads from what the provider already holds: it re-runs nothing.
	 *
	 * @param reEvaluationId an id from {@link #getScheduledReEvaluations()}.
	 * @return the current fragment, or empty when the id is unknown or nothing was produced yet.
	 */
	default Optional<JsonNode> currentFragment(String reEvaluationId) {
		return Optional.empty();
	}
}
