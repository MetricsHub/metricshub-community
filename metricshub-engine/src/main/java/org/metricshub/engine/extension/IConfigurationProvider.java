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
	 * Load configuration fragments that must be applied last, after the regular
	 * fragments of <b>all</b> providers have been merged.
	 * <p>
	 * This lets a provider defer specific fragments (for example, the UI managed
	 * {@code metricshub-ui.yaml} file) so they always take precedence over any other
	 * configuration source, regardless of provider discovery order or file listing order.
	 * Providers with no such fragments return an empty collection.
	 *
	 * @param path The path to the configuration directory.
	 * @return A collection of {@link JsonNode} representing the configuration fragments to merge last.
	 */
	default Collection<JsonNode> loadLast(Path path) {
		return Collections.emptyList();
	}

	/**
	 * Order of this provider within the {@link #loadLast(Path)} merge pass.
	 * <p>
	 * When several providers return deferred fragments, they are merged in ascending
	 * order: fragments from providers with a greater order are merged later and thus
	 * take precedence. The provider managing the UI configuration file returns
	 * {@link Integer#MAX_VALUE} so it is always merged last.
	 *
	 * @return The merge order of this provider's deferred fragments. Defaults to {@code 0}.
	 */
	default int loadLastOrder() {
		return 0;
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
}
