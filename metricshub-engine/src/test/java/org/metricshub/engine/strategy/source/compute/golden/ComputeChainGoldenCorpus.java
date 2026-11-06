package org.metricshub.engine.strategy.source.compute.golden;

import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.ContentTypeReportEntry;
import org.metricshub.engine.connector.model.ContentTypeReportEntry.Kind;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToText;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.connector.parser.ConnectorStoreComposer;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.ComputeProcessor;
import org.metricshub.engine.strategy.source.compute.ComputeUpdaterProcessor;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Golden corpus of the compute chains defined in the connector repositories (epic #1508, issue #1509).
 * <p>
 * For every connector of every configured repository, every source holding at least one compute is run through
 * {@link ComputeProcessor} on a small deterministic set of inputs, and the resulting {@link SourceTable} (table and
 * raw text) is recorded in one golden file per connector. The inputs mirror the shapes the legacy source processors
 * produce (text only, table only, or both, see {@link Inputs}) plus a JSON or XML document for the chains starting
 * with <code>json2Csv</code> or <code>xml2Csv</code>.
 * <p>
 * The corpus pins what the engine does today. Stage 1 of the epic must reproduce it byte for byte, stage 2 compares
 * each rewritten chain with its old chain on the same inputs, and stage 4 regenerates it once.
 * <p>
 * Configuration (system properties, or environment variables for the paths):
 * <ul>
 * <li>{@value #ROOTS_PROPERTY} (or {@value #ROOTS_ENV}): the connector repository roots, separated by the platform
 * path separator. A root is used as is when it has no <code>src/main/connector</code> directory. Default: the
 * community and enterprise repositories checked out next to this repository. A missing root is skipped.</li>
 * <li>{@value #UPDATE_PROPERTY}: when <code>true</code>, the goldens are (re)written instead of verified.</li>
 * <li>{@value #COMPARE_PROPERTY}: <code>exact</code> (default) compares the whole output, <code>typed</code> compares
 * what the chain produces for its consumers, see {@link CompareMode#TYPED}.</li>
 * <li>{@value #CONNECTORS_PROPERTY}: a comma-separated list of connector ids (case-insensitive) restricting the
 * connectors checked and reported. Default: every connector.</li>
 * </ul>
 * {@link GoldenCheck} runs the same checks outside Maven.
 * The goldens are written into each connector repository (<code>src/test/goldens/compute-chains</code>) and
 * committed there; this module records the revision of each repository they were generated from. The update mode
 * does not touch that pin (the regenerated goldens are not committed yet): after committing them, run
 * <code>GoldenCheck --root &lt;repository&gt; --pin</code>, which records the repository's new commit. CI checks the
 * community repository out at the pinned revision.
 */
@NoArgsConstructor
public class ComputeChainGoldenCorpus {

	public static final String ROOTS_PROPERTY = "metricshub.connectors.roots";
	public static final String ROOTS_ENV = "METRICSHUB_CONNECTORS_ROOTS";
	public static final String UPDATE_PROPERTY = "metricshub.goldens.update";
	public static final String COMPARE_PROPERTY = "metricshub.goldens.compare";
	public static final String CONNECTORS_PROPERTY = "metricshub.goldens.connectors";

	/** Prefix of a difference that is an intended change of the migration: printed, never failing. */
	public static final String INTENDED_PREFIX = "intended: ";

	/** Suffix of the input names whose differences are intended (a <code>;</code> inside a cell). */
	public static final String SEPARATOR_IN_CELL_SUFFIX = "SeparatorInCell";

	/**
	 * How a generated golden is compared with the stored one.
	 */
	public enum CompareMode {
		/** The whole output (table, raw text, error, time-out), the source type and the compute list. */
		EXACT,
		/**
		 * What the chain produces for its consumers: the table (trailing empty cells stripped from every row on both
		 * sides, raw text ignored) when the generated chain ends in a table, the raw text only when it ends in text;
		 * error and time-out as they are. A changed compute list or source type is not a difference, and the
		 * differences on the {@value #SEPARATOR_IN_CELL_SUFFIX} inputs are intended ones.
		 */
		TYPED;

		/**
		 * Parses a mode name, case-insensitively.
		 *
		 * @param name The name, null or blank for the default (exact)
		 * @return the mode
		 */
		public static CompareMode parse(final String name) {
			return name == null || name.isBlank() ? EXACT : valueOf(name.trim().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * The corpus directory of this module, relative to the module directory: it holds the revision pin only. The
	 * goldens themselves live in the connector repository they describe (see {@link #REPOSITORY_GOLDEN_DIRECTORY}):
	 * the enterprise repository is private, so its goldens never leave it.
	 */
	public static final Path GOLDEN_DIRECTORY = Path.of("src", "test", "resources", "goldens", "compute-chains");

	/** Where the goldens of a connector repository live, relative to that repository's root. */
	public static final Path REPOSITORY_GOLDEN_DIRECTORY = Path.of("src", "test", "goldens", "compute-chains");

	private static final List<String> DEFAULT_ROOTS = List.of(
		"../../metricshub-community-connectors",
		"../../metricshub-enterprise-connectors"
	);

	private static final String HOSTNAME = "golden-host";

	/** How long one chain may run on one input before it is abandoned. */
	public static final Duration RUN_TIMEOUT = Duration.ofSeconds(10);

	private static final ObjectMapper MAPPER = JsonHelper.buildObjectMapper()
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

	/**
	 * The configured repository roots, existing or not.
	 *
	 * @return the roots in configuration order
	 */
	public static List<Path> configuredRoots() {
		String configured = System.getProperty(ROOTS_PROPERTY);
		if (configured == null || configured.isBlank()) {
			configured = System.getenv(ROOTS_ENV);
		}
		final List<String> roots = configured == null || configured.isBlank()
			? DEFAULT_ROOTS
			: Arrays.asList(configured.split(Pattern.quote(File.pathSeparator)));
		return roots.stream().map(String::trim).filter(root -> !root.isEmpty()).map(Path::of).toList();
	}

	/**
	 * Whether the goldens are to be rewritten rather than verified.
	 *
	 * @return <code>true</code> in update mode
	 */
	public static boolean isUpdateMode() {
		return Boolean.getBoolean(UPDATE_PROPERTY);
	}

	/**
	 * The configured comparison mode.
	 *
	 * @return the mode, exact unless {@value #COMPARE_PROPERTY} says otherwise
	 */
	public static CompareMode compareMode() {
		return CompareMode.parse(System.getProperty(COMPARE_PROPERTY));
	}

	/**
	 * Whether a connector is selected by {@value #CONNECTORS_PROPERTY}.
	 *
	 * @param connectorId The connector identifier
	 * @return <code>true</code> when the property is unset or lists the connector (case-insensitively)
	 */
	public static boolean isSelected(final String connectorId) {
		final String selection = System.getProperty(CONNECTORS_PROPERTY);
		if (selection == null || selection.isBlank()) {
			return true;
		}
		return Stream.of(selection.split(",")).map(String::trim).anyMatch(connectorId::equalsIgnoreCase);
	}

	/**
	 * The ids listed by {@value #CONNECTORS_PROPERTY} that match none of the given connectors: a misspelt id would
	 * otherwise select nothing and pass silently.
	 *
	 * @param connectorIds The ids of every loaded connector, across all repositories
	 * @return the listed ids no connector carries (case-insensitively); empty when the property is unset
	 */
	public static List<String> unknownSelection(final Collection<String> connectorIds) {
		final String selection = System.getProperty(CONNECTORS_PROPERTY);
		if (selection == null || selection.isBlank()) {
			return List.of();
		}
		final TreeSet<String> known = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		known.addAll(connectorIds);
		return Stream.of(selection.split(","))
			.map(String::trim)
			.filter(id -> !id.isEmpty() && !known.contains(id))
			.toList();
	}

	/**
	 * Whether a difference line is an intended change (see {@link CompareMode#TYPED}).
	 *
	 * @param difference The difference line
	 * @return <code>true</code> when it carries the {@value #INTENDED_PREFIX} prefix
	 */
	public static boolean isIntended(final String difference) {
		return difference.startsWith(INTENDED_PREFIX);
	}

	/**
	 * The directory holding the connectors of a repository root.
	 *
	 * @param root The repository root
	 * @return <code>root/src/main/connector</code> when it exists, else the root itself
	 */
	public static Path connectorDirectory(final Path root) {
		final Path standard = root.resolve("src").resolve("main").resolve("connector");
		return Files.isDirectory(standard) ? standard : root;
	}

	/**
	 * The directory holding the goldens of a repository root: inside that repository, so that the goldens of a
	 * private repository stay private.
	 *
	 * @param root The repository root
	 * @return <code>root/src/test/goldens/compute-chains</code>
	 */
	public static Path goldenDirectory(final Path root) {
		return root.resolve(REPOSITORY_GOLDEN_DIRECTORY);
	}

	/**
	 * Parses every connector of a repository, including the ones declaring variables (with their default values).
	 *
	 * @param connectorDirectory The directory holding the connectors
	 * @return the connector store
	 */
	public static ConnectorStore loadConnectors(final Path connectorDirectory) {
		final ConnectorStoreComposer composer = ConnectorStoreComposer.builder()
			.withRawConnectorStore(new RawConnectorStore(connectorDirectory))
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
			.build();
		final ConnectorStore store = composer.generateStaticConnectorStore();
		store.addMany(composer.resolveConnectorStoreVariables(store).getCustomConnectorsMap());
		return store;
	}

	/**
	 * Runs every compute chain of a connector on the generated inputs.
	 *
	 * @param connectorStore The store holding the connector (and its embedded files and translation tables)
	 * @param connectorId    The identifier of the connector in the store
	 * @return the golden of the connector, sources without computes left out
	 */
	public static ConnectorGolden generate(final ConnectorStore connectorStore, final String connectorId) {
		final Connector connector = connectorStore.getStore().get(connectorId);
		final Map<String, Source> sources = sourcesOf(connector);
		final ConnectorGolden golden = new ConnectorGolden();
		golden.setConnector(connectorId);
		sources.forEach((key, source) -> {
			// The chain as written in the connector; the implicit conversions inserted by the desugaring step run too,
			// but they are not part of the golden's identity: the goldens pin the results of the written chains
			final List<Compute> explicitComputes = explicitComputes(source);
			if (explicitComputes.isEmpty()) {
				return;
			}
			final SourceGolden sourceGolden = new SourceGolden();
			sourceGolden.setSource(source.getType());
			sourceGolden.setComputes(explicitComputes.stream().map(Compute::getType).toList());
			sourceGolden.setEnding(explicitComputes.get(explicitComputes.size() - 1).getOutputContentType());
			// A chain ending in text from a legacy dual producer gets an implicit toTable: its consumers read both the
			// text and the table
			final Compute last = source.getComputes().get(source.getComputes().size() - 1);
			sourceGolden.setImplicitTable(last instanceof ToTable toTable && toTable.isImplicit());
			Inputs.inputsFor(source, sources)
				.forEach(
					(name, input) -> sourceGolden.getOutputs().put(name, run(connectorStore, connectorId, source, input.get()))
				);
			golden.getSources().put(key, sourceGolden);
		});
		return golden;
	}

	/**
	 * The computes written in the connector, without the implicit conversions inserted by the content-type
	 * desugaring step.
	 *
	 * @param source The source
	 * @return the explicit computes
	 */
	public static List<Compute> explicitComputes(final Source source) {
		return source.getComputes() == null
			? List.of()
			: source.getComputes().stream().filter(compute -> !isImplicit(compute)).toList();
	}

	private static boolean isImplicit(final Compute compute) {
		return ((compute instanceof ToTable toTable && toTable.isImplicit()) ||
			(compute instanceof ToText toText && toText.isImplicit()));
	}

	/**
	 * Every source of the connector, indexed by its key, in definition order.
	 *
	 * @param connector The connector
	 * @return the sources
	 */
	public static Map<String, Source> sourcesOf(final Connector connector) {
		final Map<String, Source> sources = new LinkedHashMap<>();
		if (connector.getBeforeAll() != null) {
			connector.getBeforeAll().values().forEach(source -> sources.put(source.getKey(), source));
		}
		for (final MonitorJob job : connector.getMonitors().values()) {
			if (job instanceof StandardMonitorJob standard) {
				if (standard.getDiscovery() != null) {
					standard.getDiscovery().getSources().values().forEach(source -> sources.put(source.getKey(), source));
				}
				if (standard.getCollect() != null) {
					standard.getCollect().getSources().values().forEach(source -> sources.put(source.getKey(), source));
				}
			} else if (job instanceof SimpleMonitorJob simple && simple.getSimple() != null) {
				simple.getSimple().getSources().values().forEach(source -> sources.put(source.getKey(), source));
			}
		}
		if (connector.getAfterAll() != null) {
			connector.getAfterAll().values().forEach(source -> sources.put(source.getKey(), source));
		}
		// The tools run their sources through the same compute processor
		if (connector.getTools() != null) {
			connector.getTools()
				.values()
				.forEach(tool -> tool.getSources().values().forEach(source -> sources.put(source.getKey(), source)));
		}
		return sources;
	}

	/**
	 * Runs the compute chain of a source on one input, exactly as the strategies do: each compute is copied, its
	 * references resolved by {@link ComputeUpdaterProcessor} (no source table is available, so a source reference
	 * resolves to an empty string) and applied by {@link ComputeProcessor}.
	 * <p>
	 * An AWK script written for a specific command output can loop forever on a generic input (SunPrtpicl does).
	 * The chain therefore runs on its own daemon thread and is abandoned after {@link #RUN_TIMEOUT}: the output then
	 * carries the <code>timedOut</code> flag instead of a table. A chain that throws (the strategies do not catch
	 * that either: the whole job fails) gives an output carrying the exception in <code>error</code>.
	 *
	 * @param connectorStore The store holding the connector
	 * @param connectorId    The identifier of the connector
	 * @param source         The source defining the chain
	 * @param input          The input, mutated in place
	 * @return the output
	 */
	public static Output run(
		final ConnectorStore connectorStore,
		final String connectorId,
		final Source source,
		final SourceTable input
	) {
		final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "golden-" + connectorId);
			thread.setDaemon(true);
			return thread;
		});
		try {
			return executor.submit(() -> runChain(connectorStore, connectorId, source, input))
				.get(RUN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			return new Output(null, null, true, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (ExecutionException e) {
			final Throwable cause = e.getCause();
			return new Output(null, null, null, cause.getClass().getSimpleName() + ": " + cause.getMessage());
		} finally {
			executor.shutdownNow();
		}
	}

	private static Output runChain(
		final ConnectorStore connectorStore,
		final String connectorId,
		final Source source,
		final SourceTable input
	) {
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(HOSTNAME).hostId(HOSTNAME).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().build())
			.connectorStore(connectorStore)
			.build();
		final ComputeProcessor computeProcessor = ComputeProcessor.builder()
			.sourceKey(source.getKey())
			.sourceTable(input)
			.connectorId(connectorId)
			.hostname(HOSTNAME)
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.telemetryManager(telemetryManager)
			.attributes(Map.of())
			.build();
		final ComputeUpdaterProcessor updater = ComputeUpdaterProcessor.builder()
			.computeProcessor(computeProcessor)
			.attributes(Map.of())
			.connectorId(connectorId)
			.telemetryManager(telemetryManager)
			.build();
		final List<Compute> computes = source.getComputes();
		for (int index = 0; index < computes.size(); index++) {
			computeProcessor.setIndex(index);
			computes.get(index).accept(updater);
		}
		return new Output(input.getTable(), input.getRawData(), null, null);
	}

	/**
	 * The stored goldens whose connector file no longer exists (a deleted or renamed connector): safe to delete.
	 *
	 * @param repository   The repository
	 * @param connectorIds The ids of the connectors loaded from the repository
	 * @return the ids of the orphan goldens, in file order
	 * @throws IOException when a directory cannot be listed
	 */
	public static List<String> orphanGoldens(final Repository repository, final Collection<String> connectorIds)
		throws IOException {
		final Set<String> files = connectorFiles(repository);
		return goldensWithoutConnector(repository, connectorIds).stream().filter(id -> !files.contains(id)).toList();
	}

	/**
	 * The stored goldens whose connector file exists but did not load (a parse error): never deleted, a failure.
	 *
	 * @param repository   The repository
	 * @param connectorIds The ids of the connectors loaded from the repository
	 * @return the ids of the connectors that failed to load, in file order
	 * @throws IOException when a directory cannot be listed
	 */
	public static List<String> unloadedGoldens(final Repository repository, final Collection<String> connectorIds)
		throws IOException {
		final Set<String> files = connectorFiles(repository);
		return goldensWithoutConnector(repository, connectorIds).stream().filter(files::contains).toList();
	}

	/**
	 * The ids of the connector files of the repository: the base names of the YAML files under its connector
	 * directory, case-insensitive.
	 */
	private static Set<String> connectorFiles(final Repository repository) throws IOException {
		final Set<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (Files.isDirectory(repository.getConnectorDirectory())) {
			try (Stream<Path> files = Files.walk(repository.getConnectorDirectory())) {
				files.map(file -> file.getFileName().toString())
					// The loader accepts the extension in any case
					.filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".yaml"))
					.forEach(name -> ids.add(name.substring(0, name.length() - ".yaml".length())));
			}
		}
		return ids;
	}

	private static List<String> goldensWithoutConnector(
		final Repository repository,
		final Collection<String> connectorIds
	) throws IOException {
		if (!Files.isDirectory(repository.getGoldenDirectory())) {
			return List.of();
		}
		final Set<String> known = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		known.addAll(connectorIds);
		try (Stream<Path> files = Files.list(repository.getGoldenDirectory())) {
			return files.map(file -> file.getFileName().toString())
				.filter(name -> name.endsWith(".json"))
				.map(name -> name.substring(0, name.length() - ".json".length()))
				.filter(connectorId -> !known.contains(connectorId))
				.toList();
		}
	}

	/**
	 * The file recording the revision of each connector repository the goldens were generated from, as
	 * <code>&lt;root directory name&gt;=&lt;commit&gt;</code> lines. CI checks out these revisions.
	 */
	public static final Path REVISIONS_FILE = GOLDEN_DIRECTORY.resolve("revisions.properties");

	/**
	 * Records the revision (<code>git rev-parse HEAD</code>) of a repository in {@link #REVISIONS_FILE}, keeping
	 * the other repositories' lines. Nothing is written when the root is not a git checkout.
	 *
	 * @param root The repository root
	 * @throws IOException when the file cannot be read or written
	 */
	public static void recordRevision(final Path root) throws IOException {
		final String revision = gitHead(root);
		if (revision == null) {
			return;
		}
		final String name = root.toAbsolutePath().normalize().getFileName().toString();
		final Map<String, String> revisions = new TreeMap<>();
		if (Files.exists(REVISIONS_FILE)) {
			for (final String line : Files.readAllLines(REVISIONS_FILE)) {
				final int equal = line.indexOf('=');
				if (equal > 0 && !line.startsWith("#")) {
					revisions.put(line.substring(0, equal).trim(), line.substring(equal + 1).trim());
				}
			}
		}
		revisions.put(name, revision);
		final StringBuilder content = new StringBuilder(
			"# Revision of each connector repository the goldens were generated from (written by the update mode)"
		);
		revisions.forEach(
			(repository, commit) -> content.append(System.lineSeparator()).append(repository).append('=').append(commit)
		);
		content.append(System.lineSeparator());
		Files.createDirectories(REVISIONS_FILE.getParent());
		Files.writeString(REVISIONS_FILE, content.toString());
	}

	/**
	 * The current commit of a repository.
	 *
	 * @param root The repository root
	 * @return <code>git rev-parse HEAD</code>, null when the root is missing or not a git checkout
	 */
	static String gitHead(final Path root) {
		try {
			final Process process = new ProcessBuilder("git", "-C", root.toAbsolutePath().toString(), "rev-parse", "HEAD")
				.redirectErrorStream(true)
				.start();
			final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			return process.waitFor() == 0 && output.matches("[0-9a-f]{40}") ? output : null;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/**
	 * Writes the golden of a connector.
	 *
	 * @param file   The golden file
	 * @param golden The golden
	 * @throws IOException when the file cannot be written
	 */
	public static void write(final Path file, final ConnectorGolden golden) throws IOException {
		Files.createDirectories(file.getParent());
		MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), golden);
	}

	/**
	 * Generates the golden of a connector and checks it against the stored one (or writes it in update mode), as
	 * the test and {@link GoldenCheck} do.
	 *
	 * @param repository  The repository holding the connector and its golden
	 * @param store       The store holding the connector
	 * @param connectorId The identifier of the connector
	 * @param mode        The comparison mode
	 * @return the differences, one line each (see {@link #isIntended(String)}), empty when the golden matches or
	 *         was written
	 * @throws IOException when the golden cannot be read or written
	 */
	public static List<String> check(
		final Repository repository,
		final ConnectorStore store,
		final String connectorId,
		final CompareMode mode
	) throws IOException {
		final ConnectorGolden golden = generate(store, connectorId);
		final Path file = repository.getGoldenDirectory().resolve(connectorId + ".json");
		if (isUpdateMode()) {
			if (golden.getSources().isEmpty()) {
				Files.deleteIfExists(file);
			} else {
				write(file, golden);
			}
			return List.of();
		}
		if (golden.getSources().isEmpty()) {
			return Files.exists(file)
				? List.of(connectorId + " has no compute chain but a stored golden " + file)
				: List.of();
		}
		if (!Files.exists(file)) {
			return List.of(
				connectorId + " has no stored golden " + file + ": generate it with -D" + UPDATE_PROPERTY + "=true"
			);
		}
		return diff(file, golden, mode);
	}

	/**
	 * Compares a generated golden with the stored one, exactly.
	 *
	 * @param file   The stored golden file
	 * @param golden The generated golden
	 * @return the differences, one line each, empty when the goldens are equal
	 * @throws IOException when the stored golden cannot be read
	 */
	public static List<String> diff(final Path file, final ConnectorGolden golden) throws IOException {
		return diff(file, golden, CompareMode.EXACT);
	}

	/**
	 * Compares a generated golden with the stored one.
	 *
	 * @param file   The stored golden file
	 * @param golden The generated golden
	 * @param mode   The comparison mode
	 * @return the differences, one line each, empty when the goldens are equal; in typed mode the intended ones
	 *         carry {@value #INTENDED_PREFIX}
	 * @throws IOException when the stored golden cannot be read
	 */
	public static List<String> diff(final Path file, final ConnectorGolden golden, final CompareMode mode)
		throws IOException {
		final JsonNode stored = MAPPER.readTree(file.toFile());
		final JsonNode generated = MAPPER.valueToTree(golden);
		final List<String> differences = new ArrayList<>();
		final JsonNode storedSources = stored.path("sources");
		final JsonNode generatedSources = generated.path("sources");
		storedSources.fieldNames().forEachRemaining(key -> {
			if (!generatedSources.has(key)) {
				differences.add(key + ": no longer has computes");
			}
		});
		generatedSources.fieldNames().forEachRemaining(key -> {
			final JsonNode storedSource = storedSources.get(key);
			if (storedSource == null) {
				differences.add(key + ": new compute chain, no stored golden");
				return;
			}
			final JsonNode generatedSource = generatedSources.get(key);
			if (mode == CompareMode.EXACT) {
				if (!storedSource.path("source").equals(generatedSource.path("source"))) {
					differences.add(
						key + ": source type changed " + storedSource.path("source") + " -> " + generatedSource.path("source")
					);
				}
				if (!storedSource.path("computes").equals(generatedSource.path("computes"))) {
					differences.add(
						key + ": compute chain changed " + storedSource.path("computes") + " -> " + generatedSource.path("computes")
					);
				}
			}
			final ContentType ending = golden.getSources().get(key).getEnding();
			final boolean implicitTable = golden.getSources().get(key).isImplicitTable();
			final JsonNode storedOutputs = storedSource.path("outputs");
			final JsonNode generatedOutputs = generatedSource.path("outputs");
			generatedOutputs.fieldNames().forEachRemaining(input -> {
				final JsonNode storedOutput = storedOutputs.get(input);
				final JsonNode generatedOutput = generatedOutputs.get(input);
				final boolean same = mode == CompareMode.EXACT
					? generatedOutput.equals(storedOutput)
					: sameForConsumers(storedOutput, generatedOutput, ending, implicitTable);
				if (!same) {
					// Only a content difference on a separator-in-cell input is intended: a chain that now throws or
					// hangs on that input, or an input without a stored output, is a regression like any other
					final String prefix = mode == CompareMode.TYPED &&
						input.endsWith(SEPARATOR_IN_CELL_SUFFIX) &&
						storedOutput != null &&
						sameFlags(storedOutput, generatedOutput) ? INTENDED_PREFIX : "";
					differences.add(prefix + key + " [" + input + "]: expected " + storedOutput + " but was " + generatedOutput);
				}
			});
			// An input the generator no longer produces leaves a stale output behind: report it too
			storedOutputs.fieldNames().forEachRemaining(input -> {
				if (!generatedOutputs.has(input)) {
					differences.add(key + " [" + input + "]: stored output no longer generated");
				}
			});
		});
		return differences;
	}

	/**
	 * Whether two outputs failed the same way: same <code>error</code>, same <code>timedOut</code>.
	 *
	 * @param stored    The stored output
	 * @param generated The generated output
	 * @return <code>true</code> when both flags are equal
	 */
	static boolean sameFlags(final JsonNode stored, final JsonNode generated) {
		return stored.path("error").equals(generated.path("error")) &&
			stored.path("timedOut").equals(generated.path("timedOut"));
	}

	/**
	 * Whether two outputs are the same for the consumers of the chain (see {@link CompareMode#TYPED}).
	 *
	 * @param stored    The stored output, null when the stored golden has none for the input
	 * @param generated The generated output
	 * @param ending    The content type the generated chain ends in
	 * @param both      Whether the text ending is followed by an implicit <code>toTable</code>: both are then read
	 * @return <code>true</code> when the consumers see the same result
	 */
	static boolean sameForConsumers(
		final JsonNode stored,
		final JsonNode generated,
		final ContentType ending,
		final boolean both
	) {
		if (stored == null || !sameFlags(stored, generated)) {
			return false;
		}
		final boolean sameText = stored.path("rawData").equals(generated.path("rawData"));
		final boolean sameTable = stripTrailingEmptyCells(stored.path("table")).equals(
			stripTrailingEmptyCells(generated.path("table"))
		);
		if (both) {
			return sameText && sameTable;
		}
		return ending == ContentType.TEXT ? sameText : sameTable;
	}

	/**
	 * The rows of a JSON table, each one without its trailing empty cells (<code>commandLine</code> drops them with
	 * <code>String.split</code>, <code>toTable</code> keeps them).
	 *
	 * @param table The JSON array of rows, or a missing node
	 * @return the stripped rows
	 */
	static List<List<String>> stripTrailingEmptyCells(final JsonNode table) {
		final List<List<String>> rows = new ArrayList<>();
		for (final JsonNode row : table) {
			final List<String> cells = new ArrayList<>();
			row.forEach(cell -> cells.add(cell.asText()));
			while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
				cells.remove(cells.size() - 1);
			}
			rows.add(cells);
		}
		return rows;
	}

	/**
	 * Renders the content-type desugaring report of the selected connectors of each repository: the worklist of
	 * stage 2 of #1508 and the exit criterion of stage 4.
	 *
	 * @param stores The connector store of each repository, in order
	 * @return the report
	 */
	public static String contentTypeReport(final Map<Repository, ConnectorStore> stores) {
		final StringBuilder report = new StringBuilder();
		final Map<Kind, Integer> totals = new EnumMap<>(Kind.class);
		int connectorsWithFindings = 0;
		for (final Map.Entry<Repository, ConnectorStore> repository : stores.entrySet()) {
			final Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
			final Path root = repository.getKey().getRoot();
			report.append("# ").append(root.toAbsolutePath().normalize()).append('\n');
			for (final Map.Entry<String, Connector> entry : new TreeMap<>(repository.getValue().getStore()).entrySet()) {
				final List<ContentTypeReportEntry> findings = entry.getValue().getContentTypeReport();
				if (findings.isEmpty() || !isSelected(entry.getKey())) {
					continue;
				}
				connectorsWithFindings++;
				report.append('\n').append(entry.getKey()).append(" (").append(findings.size()).append(")\n");
				for (final ContentTypeReportEntry finding : findings) {
					report.append("  ").append(finding).append('\n');
					counts.merge(finding.kind(), 1, Integer::sum);
				}
			}
			report.append("\nTotals for ").append(root.getFileName()).append(":\n");
			counts.forEach((kind, count) -> report.append("  ").append(kind).append(": ").append(count).append('\n'));
			counts.forEach((kind, count) -> totals.merge(kind, count, Integer::sum));
			report.append('\n');
		}
		report.append("Connectors with findings: ").append(connectorsWithFindings).append('\n');
		totals.forEach((kind, count) -> report.append("  ").append(kind).append(": ").append(count).append('\n'));
		return report.toString();
	}

	/**
	 * The golden of one connector: one entry per source holding computes.
	 */
	@Data
	public static class ConnectorGolden {

		private String connector;
		private Map<String, SourceGolden> sources = new TreeMap<>();
	}

	/**
	 * The golden of one source: its type, its compute chain and the output of the chain for each named input.
	 */
	@Data
	public static class SourceGolden {

		private String source;
		private List<String> computes;
		private Map<String, Output> outputs = new TreeMap<>();

		/**
		 * The content type the chain ends in: that of the last explicit compute. Not stored: the typed comparison
		 * reads it from the generated golden.
		 */
		@JsonIgnore
		private ContentType ending;

		/**
		 * Whether the chain as desugared ends with an implicit <code>toTable</code> (a text ending from a legacy dual
		 * producer, read as text and as a table). Not stored.
		 */
		@JsonIgnore
		private boolean implicitTable;
	}

	/**
	 * The result of a chain: the table and the raw text of the {@link SourceTable}, null raw text preserved.
	 */
	@Value
	public static class Output {

		List<List<String>> table;
		String rawData;

		/** <code>true</code> when the chain did not finish within {@link #RUN_TIMEOUT}, else null (left out). */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		Boolean timedOut;

		/** The exception thrown by the chain (class and message), else null (left out). */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		String error;
	}

	/**
	 * The inputs fed to the chains. All of them derive from one text and one table:
	 * <ul>
	 * <li>the text holds a line with <code>;</code> separators, a line with an empty cell and a trailing
	 * <code>;</code>, a blank line, numbers, a cell containing a comma and a single-cell line;</li>
	 * <li>the table holds the same, plus trailing empty cells.</li>
	 * </ul>
	 * Every table-derived shape has a <code>&lt;shape&gt;SeparatorInCell</code> twin built from a two-row table whose
	 * second row holds <code>;</code> inside a cell, and every shape has a <code>&lt;shape&gt;Wide</code> twin built
	 * from a 3 × 16 table of cells <code>r1c1</code> to <code>r3c16</code> (text shapes: the cells joined with
	 * <code>;</code>, the lines with a line feed), so that a compute indexing a high column runs in the regime of a
	 * real table.
	 * <p>
	 * Each source type gets the shapes its legacy processor produces:
	 * <ul>
	 * <li><code>http</code>, <code>file</code>, <code>ipmi</code>: raw text only, with and without a trailing
	 * line feed;</li>
	 * <li><code>commandLine</code>: the raw lines joined with line feeds (no trailing <code>;</code>) and the table
	 * built with <code>String.split(";")</code>;</li>
	 * <li><code>awk</code> source: the raw text and <code>csvToTable</code> of it;</li>
	 * <li><code>sql</code>, <code>jmx</code>: the table and its serialization with <code>;</code> inside cells
	 * replaced by <code>,</code>;</li>
	 * <li><code>static</code>: the table parsed from the declared value and its serialization;</li>
	 * <li><code>copy</code>: the shapes of the copied source, or the serialized table when that source has computes;</li>
	 * <li><code>tableUnion</code>: every dual shape;</li>
	 * <li>every other source: the table only.</li>
	 * </ul>
	 * Every source also gets the empty input. A chain starting with <code>json2Csv</code> or <code>xml2Csv</code>
	 * additionally gets a document generated from the compute's entry key, record tag and properties.
	 */
	public static final class Inputs {

		static final String TEXT = "a;b;c\nx;;z;\n\n1;2.5;3\nval;with,comma\nonly\n";

		/** The wide table as text: 3 lines of 16 cells joined with <code>;</code>, trailing line feed included. */
		static final String WIDE_TEXT = wideTable().stream()
			.map(row -> String.join(TABLE_SEP, row))
			.collect(Collectors.joining("\n", "", "\n"));

		private static final Pattern INDEXED = Pattern.compile("^(.*?)\\[(\\d+)\\]$");

		private Inputs() {}

		static List<List<String>> table() {
			return rows("a|b|c", "x||z|", "1|2.5|3", "only");
		}

		/** A normal row and a row holding <code>;</code> inside a cell. */
		static List<List<String>> separatorInCellTable() {
			return rows("a|b|c", "cell;with;sep||");
		}

		/** 3 rows of 16 cells, <code>r1c1</code> to <code>r3c16</code>. */
		static List<List<String>> wideTable() {
			final List<List<String>> table = new ArrayList<>();
			for (int row = 1; row <= 3; row++) {
				final List<String> cells = new ArrayList<>();
				for (int column = 1; column <= 16; column++) {
					cells.add("r" + row + "c" + column);
				}
				table.add(cells);
			}
			return table;
		}

		static List<List<String>> rows(final String... rows) {
			final List<List<String>> table = new ArrayList<>();
			for (final String row : rows) {
				table.add(new ArrayList<>(Arrays.asList(row.split("\\|", -1))));
			}
			return table;
		}

		static SourceTable text(final String text) {
			return SourceTable.builder().rawData(text).build();
		}

		static SourceTable tableOnly(final List<List<String>> table) {
			return SourceTable.builder().table(table).build();
		}

		static SourceTable csv(final List<List<String>> table, final boolean replaceSeparator) {
			return SourceTable.builder()
				.table(table)
				.rawData(SourceTable.tableToCsv(table, TABLE_SEP, replaceSeparator))
				.build();
		}

		static SourceTable commandLine(final String text) {
			final List<String> lines = SourceTable.lineToList(text, "\n");
			return SourceTable.builder()
				.rawData(String.join("\n", lines))
				.table(
					lines.stream().map(line -> (List<String>) new ArrayList<>(Arrays.asList(line.split(TABLE_SEP)))).toList()
				)
				.build();
		}

		static SourceTable awkSource(final String text) {
			return SourceTable.builder().rawData(text).table(SourceTable.csvToTable(text, TABLE_SEP)).build();
		}

		/**
		 * The named inputs of a source, each one a supplier of a fresh (mutable) source table.
		 *
		 * @param source  The source
		 * @param sources Every source of the connector, to resolve a <code>copy</code>
		 * @return the inputs, in a stable order
		 */
		public static Map<String, Supplier<SourceTable>> inputsFor(final Source source, final Map<String, Source> sources) {
			final Map<String, Supplier<SourceTable>> inputs = new LinkedHashMap<>();
			addShapes(inputs, source, sources, 0);
			inputs.put("empty", SourceTable::empty);
			final Compute first = explicitComputes(source).get(0);
			if (first instanceof Json2Csv json2Csv) {
				inputs.put("json", () -> text(json(json2Csv)));
			} else if (first instanceof Xml2Csv xml2Csv) {
				inputs.put("xml", () -> text(xml(xml2Csv)));
			}
			return inputs;
		}

		private static void addShapes(
			final Map<String, Supplier<SourceTable>> inputs,
			final Source source,
			final Map<String, Source> sources,
			final int depth
		) {
			if (source instanceof HttpSource || source instanceof FileSource || source instanceof IpmiSource) {
				textShape(inputs, "text", Inputs::text, true);
				textShape(inputs, "textNoTrailingNewline", Inputs::text, false);
			} else if (source instanceof CommandLineSource) {
				textShape(inputs, "commandLine", Inputs::commandLine, true);
			} else if (source instanceof JawkSource) {
				textShape(inputs, "awkSource", Inputs::awkSource, true);
			} else if (source instanceof SqlSource || source instanceof JmxSource) {
				tableShape(inputs, "csvReplaceSeparator", table -> csv(table, true));
			} else if (source instanceof StaticSource staticSource) {
				final String value = staticSource.getValue() == null ? "" : staticSource.getValue();
				if (staticSource.getCells() != null) {
					// The list form is the single row of exactly these cells
					// A fresh row (the chain mutates it), serialized separator-safe as the processor does for the list form
					inputs.put("static", () -> csv(List.of(new ArrayList<>(staticSource.getCells())), true));
					twins(inputs, "static", table -> csv(table, true));
				} else if (SOURCE_REF_PATTERN.matcher(value).find()) {
					// The processor copies the referenced source's table and serializes it
					tableShape(inputs, "csv", table -> csv(table, false));
				} else {
					inputs.put("static", () -> csv(SourceTable.csvToTable(value, TABLE_SEP), false));
					twins(inputs, "static", table -> csv(table, false));
				}
			} else if (source instanceof CopySource copySource) {
				final Source origin = copySource.getFrom() == null ? null : sources.get(copySource.getFrom());
				if (origin == null || depth > 5) {
					addDualShapes(inputs);
				} else if (!explicitComputes(origin).isEmpty()) {
					tableShape(inputs, "csv", table -> csv(table, false));
				} else {
					addShapes(inputs, origin, sources, depth + 1);
				}
			} else if (source instanceof TableUnionSource) {
				addDualShapes(inputs);
			} else {
				tableShape(inputs, "table", Inputs::tableOnly);
			}
		}

		private static void addDualShapes(final Map<String, Supplier<SourceTable>> inputs) {
			textShape(inputs, "text", Inputs::text, true);
			tableShape(inputs, "table", Inputs::tableOnly);
			textShape(inputs, "commandLine", Inputs::commandLine, true);
			textShape(inputs, "awkSource", Inputs::awkSource, true);
			tableShape(inputs, "csv", table -> csv(table, false));
			tableShape(inputs, "csvReplaceSeparator", table -> csv(table, true));
		}

		/**
		 * A text-derived shape and its wide twin.
		 *
		 * @param inputs          The inputs to add to
		 * @param name            The shape name
		 * @param builder         Builds the source table from the text
		 * @param trailingNewline Whether the text ends with a line feed
		 */
		private static void textShape(
			final Map<String, Supplier<SourceTable>> inputs,
			final String name,
			final Function<String, SourceTable> builder,
			final boolean trailingNewline
		) {
			inputs.put(name, () -> builder.apply(trailingNewline ? TEXT : TEXT.substring(0, TEXT.length() - 1)));
			inputs.put(
				name + "Wide",
				() -> builder.apply(trailingNewline ? WIDE_TEXT : WIDE_TEXT.substring(0, WIDE_TEXT.length() - 1))
			);
		}

		/**
		 * A table-derived shape on the generic table, and its twins.
		 *
		 * @param inputs  The inputs to add to
		 * @param name    The shape name
		 * @param builder Builds the source table from a table
		 */
		private static void tableShape(
			final Map<String, Supplier<SourceTable>> inputs,
			final String name,
			final Function<List<List<String>>, SourceTable> builder
		) {
			inputs.put(name, () -> builder.apply(table()));
			twins(inputs, name, builder);
		}

		/**
		 * The separator-in-cell and wide twins of a table-derived shape.
		 *
		 * @param inputs  The inputs to add to
		 * @param name    The shape name
		 * @param builder Builds the source table from a table
		 */
		private static void twins(
			final Map<String, Supplier<SourceTable>> inputs,
			final String name,
			final Function<List<List<String>>, SourceTable> builder
		) {
			inputs.put(name + SEPARATOR_IN_CELL_SUFFIX, () -> builder.apply(separatorInCellTable()));
			inputs.put(name + "Wide", () -> builder.apply(wideTable()));
		}

		/**
		 * A JSON document matching the entry key and the properties of a <code>json2Csv</code> compute: the entry key
		 * leads to an array of four entries (one entry when the key is the root), each property is set on each
		 * entry (<code>..</code> climbs to the enclosing object, <code>name[n]</code> indexes an array). The values
		 * are, per entry: a name, a value containing <code>;</code>, an empty string and a number.
		 *
		 * @param json2Csv The compute
		 * @return the document
		 */
		public static String json(final Json2Csv json2Csv) {
			final List<String> keySegments = Stream.of(json2Csv.getEntryKey() == null ? "" : json2Csv.getEntryKey())
				.flatMap(key -> Stream.of(key.split("/")))
				.filter(segment -> !segment.isEmpty())
				.toList();
			final List<String> properties = json2Csv.getProperties() == null
				? List.of()
				: SourceTable.lineToList(json2Csv.getProperties(), TABLE_SEP);
			final boolean rootEntry = keySegments.isEmpty() || keySegments.equals(List.of("*"));
			final JsonNode root = buildJson(keySegments, 0, new ArrayList<>(), properties, rootEntry);
			try {
				// Compact: a pretty-printed document would carry the platform line separator into the goldens
				return MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		private static JsonNode buildJson(
			final List<String> segments,
			final int index,
			final List<ObjectNode> chain,
			final List<String> properties,
			final boolean rootEntry
		) {
			// A trailing "*" means "each element of the array", which the array already is
			if (index == segments.size() || (index == segments.size() - 1 && "*".equals(segments.get(index)))) {
				if (rootEntry) {
					final ObjectNode entry = MAPPER.createObjectNode();
					fill(entry, chain, properties, 0);
					return entry;
				}
				final ArrayNode entries = MAPPER.createArrayNode();
				for (int entryIndex = 0; entryIndex < 4; entryIndex++) {
					final ObjectNode entry = MAPPER.createObjectNode();
					entries.add(entry);
					fill(entry, chain, properties, entryIndex);
				}
				return entries;
			}
			final String segment = segments.get(index);
			if ("*".equals(segment)) {
				final ArrayNode elements = MAPPER.createArrayNode();
				for (int element = 0; element < 2; element++) {
					final ObjectNode object = MAPPER.createObjectNode();
					elements.add(object);
					final List<ObjectNode> elementChain = new ArrayList<>(chain);
					elementChain.add(object);
					object.setAll(buildJsonObject(segments, index + 1, elementChain, properties));
				}
				return elements;
			}
			final ObjectNode object = MAPPER.createObjectNode();
			final List<ObjectNode> objectChain = new ArrayList<>(chain);
			objectChain.add(object);
			object.set(segment, buildJson(segments, index + 1, objectChain, properties, rootEntry));
			return object;
		}

		private static ObjectNode buildJsonObject(
			final List<String> segments,
			final int index,
			final List<ObjectNode> chain,
			final List<String> properties
		) {
			final JsonNode node = buildJson(segments, index, chain, properties, false);
			if (node instanceof ObjectNode objectNode) {
				return objectNode;
			}
			// "*" followed by "*": wrap the array in an object
			final ObjectNode object = MAPPER.createObjectNode();
			object.set("*", node);
			return object;
		}

		private static void fill(
			final ObjectNode entry,
			final List<ObjectNode> chain,
			final List<String> properties,
			final int entryIndex
		) {
			for (final String property : properties) {
				final List<String> segments = Stream.of(property.split("/")).filter(s -> !s.isEmpty()).toList();
				if (segments.isEmpty()) {
					continue;
				}
				int climb = 0;
				while (climb < segments.size() && "..".equals(segments.get(climb))) {
					climb++;
				}
				final List<String> path = segments.subList(climb, segments.size());
				if (path.isEmpty() || climb > chain.size()) {
					continue;
				}
				final ObjectNode target = climb == 0 ? entry : chain.get(chain.size() - climb);
				final String leaf = INDEXED.matcher(path.get(path.size() - 1)).replaceAll("$1");
				// A value on an enclosing object is shared by the entries: the first entry sets it
				final JsonNode value = climb > 0
					? MAPPER.getNodeFactory().textNode(leaf + "-up")
					: jsonValueOf(leaf, entryIndex);
				setValue(target, path, value, climb > 0);
			}
		}

		/**
		 * The JSON value of an entry: a text, but a real number for the numeric entry.
		 */
		static JsonNode jsonValueOf(final String leaf, final int entryIndex) {
			return entryIndex == 3
				? MAPPER.getNodeFactory().numberNode(42)
				: MAPPER.getNodeFactory().textNode(valueOf(leaf, entryIndex));
		}

		static String valueOf(final String leaf, final int entryIndex) {
			return switch (entryIndex) {
				case 0 -> leaf + "-0";
				case 1 -> leaf + ";1";
				case 2 -> "";
				default -> "42";
			};
		}

		private static void setValue(
			final ObjectNode target,
			final List<String> path,
			final JsonNode value,
			final boolean keepExisting
		) {
			JsonNode current = target;
			for (int i = 0; i < path.size(); i++) {
				final boolean last = i == path.size() - 1;
				final Matcher indexed = INDEXED.matcher(path.get(i));
				final String name = indexed.matches() ? indexed.group(1) : path.get(i);
				final int arrayIndex = indexed.matches() ? Integer.parseInt(indexed.group(2)) : -1;
				if (!(current instanceof ObjectNode object)) {
					return;
				}
				if (arrayIndex < 0) {
					if (last) {
						if (!keepExisting || !object.has(name)) {
							if (!object.has(name) || object.get(name).isValueNode()) {
								object.set(name, value);
							}
						}
						return;
					}
					if (!object.has(name)) {
						object.set(name, MAPPER.createObjectNode());
					}
					current = object.get(name);
				} else {
					if (!object.has(name)) {
						object.set(name, MAPPER.createArrayNode());
					}
					if (!(object.get(name) instanceof ArrayNode array)) {
						return;
					}
					if (last) {
						while (array.size() <= arrayIndex) {
							array.add(value);
						}
						return;
					}
					while (array.size() <= arrayIndex) {
						array.addObject();
					}
					current = array.get(arrayIndex);
				}
			}
		}

		/**
		 * An XML document matching the record tag and the properties of an <code>xml2Csv</code> compute. A property
		 * is <code>path/to/element&gt;attribute</code> (or <code>path/to/element</code> for the element text). The
		 * record tag leads to three records (one when the tag is the root), each made of one element per distinct
		 * first path segment, holding the nested elements, attributes and texts of the properties. The values follow
		 * {@link #valueOf(String, int)}.
		 *
		 * @param xml2Csv The compute
		 * @return the document
		 */
		public static String xml(final Xml2Csv xml2Csv) {
			final List<String> tagSegments = Stream.of(xml2Csv.getRecordTag() == null ? "" : xml2Csv.getRecordTag())
				.flatMap(tag -> Stream.of(tag.split("/")))
				.filter(segment -> !segment.isEmpty())
				.toList();
			final List<XmlProperty> properties = (xml2Csv.getProperties() == null
				? List.<String>of()
				: SourceTable.lineToList(xml2Csv.getProperties(), TABLE_SEP)).stream()
				.map(XmlProperty::parse)
				.filter(property -> !property.elements().isEmpty())
				.toList();
			final StringBuilder xml = new StringBuilder();
			tagSegments.forEach(segment -> xml.append('<').append(segment).append('>'));
			final int records = tagSegments.isEmpty() ? 1 : 3;
			for (int record = 0; record < records; record++) {
				// One element per distinct first segment, holding every property that starts with it
				final Map<String, List<XmlProperty>> byElement = new LinkedHashMap<>();
				properties.forEach(
					property -> byElement.computeIfAbsent(property.elements().get(0), k -> new ArrayList<>()).add(property)
				);
				for (final Map.Entry<String, List<XmlProperty>> element : byElement.entrySet()) {
					appendElement(xml, element.getKey(), element.getValue(), 1, record);
				}
			}
			for (int i = tagSegments.size() - 1; i >= 0; i--) {
				xml.append("</").append(tagSegments.get(i)).append('>');
			}
			return xml.toString();
		}

		/**
		 * One <code>xml2Csv</code> property: the element path and the attribute, null for the element text.
		 */
		record XmlProperty(List<String> elements, String attribute) {
			static XmlProperty parse(final String property) {
				final String[] parts = property.split(">", 2);
				final List<String> elements = Stream.of(parts[0].split("/"))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.toList();
				final String attribute = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
				return new XmlProperty(elements, attribute);
			}
		}

		private static void appendElement(
			final StringBuilder xml,
			final String name,
			final List<XmlProperty> properties,
			final int depth,
			final int record
		) {
			xml.append('<').append(name);
			String text = null;
			final Map<String, List<XmlProperty>> children = new LinkedHashMap<>();
			for (final XmlProperty property : properties) {
				if (property.elements().size() == depth) {
					if (property.attribute() != null) {
						xml.append(' ')
							.append(property.attribute())
							.append("=\"")
							.append(escape(valueOf(property.attribute(), record)))
							.append('"');
					} else {
						text = valueOf(name, record);
					}
				} else if (property.elements().size() > depth) {
					children.computeIfAbsent(property.elements().get(depth), k -> new ArrayList<>()).add(property);
				}
			}
			xml.append('>');
			if (text != null) {
				xml.append(escape(text));
			}
			children.forEach((child, childProperties) -> appendElement(xml, child, childProperties, depth + 1, record));
			xml.append("</").append(name).append('>');
		}

		private static String escape(final String value) {
			return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
		}
	}

	/**
	 * A connector repository root, its connector directory and its golden directory.
	 */
	@Value
	@Builder
	public static class Repository {

		Path root;
		Path connectorDirectory;
		Path goldenDirectory;

		/**
		 * The repositories among the configured roots that exist on this machine.
		 *
		 * @return the existing repositories
		 */
		public static List<Repository> existing() {
			return configuredRoots().stream().filter(Files::isDirectory).map(Repository::of).toList();
		}

		/**
		 * The repository at a root.
		 *
		 * @param root The repository root
		 * @return the repository
		 */
		public static Repository of(final Path root) {
			return Repository.builder()
				.root(root)
				.connectorDirectory(connectorDirectory(root))
				.goldenDirectory(goldenDirectory(root))
				.build();
		}
	}
}
