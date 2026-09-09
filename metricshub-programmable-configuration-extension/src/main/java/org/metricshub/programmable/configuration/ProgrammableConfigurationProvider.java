package org.metricshub.programmable.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Programmable Configuration Extension
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.tools.generic.Alternator;
import org.apache.velocity.tools.generic.CollectionTool;
import org.apache.velocity.tools.generic.ComparisonDateTool;
import org.apache.velocity.tools.generic.ContextTool;
import org.apache.velocity.tools.generic.DisplayTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.apache.velocity.tools.generic.FieldTool;
import org.apache.velocity.tools.generic.FormatConfig;
import org.apache.velocity.tools.generic.JsonTool;
import org.apache.velocity.tools.generic.LinkTool;
import org.apache.velocity.tools.generic.LogTool;
import org.apache.velocity.tools.generic.LoopTool;
import org.apache.velocity.tools.generic.MathTool;
import org.apache.velocity.tools.generic.RenderTool;
import org.apache.velocity.tools.generic.ValueParser;
import org.apache.velocity.tools.generic.XmlTool;
import org.codehaus.plexus.util.StringUtils;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.extension.IConfigurationProvider;
import org.metricshub.engine.extension.ScheduledReEvaluation;

/**
 * This class lists the .vm files under the configuration directory and loads
 * them as configuration fragments.
 */
@Slf4j
public class ProgrammableConfigurationProvider implements IConfigurationProvider {

	private static final ObjectMapper YAML_MAPPER = JsonHelper.buildYamlMapper();

	private static final Map<String, Object> TOOLS = new HashMap<>();

	static {
		TOOLS.put("sql", new SqlTool());
		TOOLS.put("http", new HttpTool());
		TOOLS.put("json", new JsonTool());
		TOOLS.put("xml", new XmlTool());
		TOOLS.put("date", new ComparisonDateTool());
		TOOLS.put("math", new MathTool());
		TOOLS.put("esc", new EscapeTool());
		TOOLS.put("collection", new CollectionTool());
		TOOLS.put("parser", new ValueParser());
		TOOLS.put("context", new ContextTool());
		TOOLS.put("alternator", new Alternator());
		TOOLS.put("display", new DisplayTool());
		TOOLS.put("field", new FieldTool());
		TOOLS.put("format", new FormatConfig());
		TOOLS.put("link", new LinkTool());
		TOOLS.put("log", new LogTool());
		TOOLS.put("loop", new LoopTool());
		TOOLS.put("render", new RenderTool());
		TOOLS.put("file", new FileTool());
		TOOLS.put("stringUtils", new StringUtils());
		TOOLS.put("env", new EnvTool());
	}

	/**
	 * Loaders retained from the last {@link #load(Path)}, keyed by absolute template path, so a
	 * template that declares a schedule can be re-rendered later without a full configuration reload.
	 */
	private final Map<Path, VelocityConfigurationLoader> loaders = new ConcurrentHashMap<>();

	/**
	 * The fragment last produced per template, keyed by absolute path. Lets
	 * {@link #currentFragment(String)} seed change detection without re-running the template, and
	 * serves {@link #runReusingCachedFragments(Runnable)}.
	 */
	private final Map<Path, JsonNode> lastFragments = new ConcurrentHashMap<>();

	/**
	 * Set while the calling thread runs inside {@link #runReusingCachedFragments(Runnable)}. A load
	 * happening then serves {@link #lastFragments} instead of rendering, so a re-evaluation targeting
	 * one template does not re-run the data sources of all the others. Thread-scoped because the
	 * rebuild runs synchronously on the thread that opened the scope, while other threads (cron
	 * firings, the configuration file watcher) must keep loading normally.
	 */
	private final ThreadLocal<Boolean> reuseCachedFragments = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/**
	 * Returns an unmodifiable view of the Velocity tools map available for
	 * template evaluation.
	 *
	 * @return the tools map
	 */
	public static Map<String, Object> getTools() {
		return Collections.unmodifiableMap(TOOLS);
	}

	@Override
	public Collection<JsonNode> load(final Path configDirectory) {
		final List<JsonNode> configurations = new ArrayList<>();
		final Set<Path> seenPaths = new HashSet<>();

		try (Stream<Path> stream = Files.list(configDirectory)) {
			stream
				.filter((Path path) -> !Files.isDirectory(path))
				.filter((Path path) -> {
					String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
					return getFileExtensions().stream().anyMatch(fileName::endsWith);
				})
				.forEach((Path path) -> {
					seenPaths.add(path.toAbsolutePath());
					readVmFragment(path).ifPresent((JsonNode jsonNode) -> {
						configurations.add(jsonNode);
						log.debug("Successfully loaded YAML configuration fragment: '{}'", path);
					});
				});
		} catch (IOException e) {
			log.error("Failed to list configuration directory: '{}'. Error: {}", configDirectory, e.getMessage());
			log.debug("Failed to list configuration directory: '{}'. Exception:", configDirectory, e);
		}

		// Forget templates whose files are no longer present, so their schedules stop being exposed.
		loaders.keySet().retainAll(seenPaths);
		lastFragments.keySet().retainAll(seenPaths);

		final int size = configurations.size();
		log.info("Loaded {} Velocity configuration fragment{} from '{}'.", size, size > 1 ? "s" : "", configDirectory);
		return configurations;
	}

	/**
	 * This method reads the .vm file and returns the Optional JSON node
	 * representing the configuration.
	 *
	 * @param path           The path to the configuration file.
	 * @return An Optional containing the JSON node representing the configuration, or
	 *         an empty Optional if an error occurred.
	 */
	private Optional<JsonNode> readVmFragment(final Path path) {
		final Path absolutePath = path.toAbsolutePath();

		// Inside a scoped re-evaluation, serve what this template produced last rather than running it
		// again. A template with nothing cached yet is still rendered: there is nothing to reuse.
		if (Boolean.TRUE.equals(reuseCachedFragments.get())) {
			final JsonNode cached = lastFragments.get(absolutePath);
			if (cached != null) {
				log.debug("Reusing the last fragment of template '{}': the reload targets another template.", path);
				return Optional.of(cached);
			}
		}

		try {
			// Retain the loader so this template can be re-rendered later on its own schedule.
			final var loader = loaders.computeIfAbsent(absolutePath, key -> new VelocityConfigurationLoader(path, TOOLS));
			final String yaml = loader.generateYaml();

			if (yaml != null) {
				// Do not log the rendered YAML: it may contain credentials and other sensitive data.
				log.debug("Generated a YAML configuration fragment from template '{}'.", path);
				final JsonNode fragment = YAML_MAPPER.readTree(yaml);
				lastFragments.put(absolutePath, fragment);
				return Optional.of(fragment);
			}
		} catch (Exception e) {
			log.error("Failed to load Velocity configuration fragment: '{}'. Error: {}", path, e.getMessage());
			log.debug("Failed to load Velocity configuration fragment: '{}'. Exception:", path, e);
		}
		return Optional.empty();
	}

	@Override
	public Collection<ScheduledReEvaluation> getScheduledReEvaluations() {
		final List<ScheduledReEvaluation> result = new ArrayList<>();
		loaders.forEach((path, loader) ->
			loader.getCron().ifPresent(cron -> result.add(new ScheduledReEvaluation(path.toString(), cron)))
		);
		return result;
	}

	@Override
	public Optional<JsonNode> reevaluate(final String reEvaluationId) {
		final Path path = Path.of(reEvaluationId);
		final VelocityConfigurationLoader loader = loaders.get(path);
		if (loader == null) {
			log.warn("Unknown scheduled re-evaluation id '{}'.", reEvaluationId);
			return Optional.empty();
		}
		try {
			// A re-evaluation renders the whole template again, so every data source it reads is re-run.
			final JsonNode fragment = YAML_MAPPER.readTree(loader.generateYamlDangerous());
			lastFragments.put(path, fragment);
			return Optional.of(fragment);
		} catch (Exception e) {
			log.error("Failed to re-evaluate template '{}'. Error: {}", reEvaluationId, e.getMessage());
			log.debug("Re-evaluation exception:", e);
			return Optional.empty();
		}
	}

	@Override
	public Optional<JsonNode> currentFragment(final String reEvaluationId) {
		return Optional.ofNullable(lastFragments.get(Path.of(reEvaluationId)));
	}

	@Override
	public void runReusingCachedFragments(final Runnable action) {
		reuseCachedFragments.set(Boolean.TRUE);
		try {
			action.run();
		} finally {
			reuseCachedFragments.remove();
		}
	}

	/**
	 * Returns the set of file extensions that this configuration provider can handle.
	 */
	@Override
	public Set<String> getFileExtensions() {
		return Set.of(".vm");
	}

	@Override
	public Optional<String> renderTemplate(final Path templateFile) throws Exception {
		return Optional.of(new VelocityConfigurationLoader(templateFile, TOOLS).generateYamlDangerous());
	}
}
