package org.metricshub.engine.strategy.source.compute.golden;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeSet;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.CompareMode;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.LoadedRepository;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Repository;

/**
 * Checks (or, with <code>--update</code>, rewrites) the compute-chain goldens of connector repositories outside
 * Maven, so that several agents rewriting connectors can check them concurrently without sharing a
 * <code>target</code> directory. It runs the same code as {@link ComputeChainGoldenCorpusTest}.
 * <p>
 * Usage: <code>GoldenCheck --root &lt;repository&gt; [--root ...] [--connectors A,B] [--compare typed|exact]
 * [--update] [--report] [--pin]</code>
 * <ul>
 * <li><code>--root</code>: a connector repository root (repeatable, community first): each repository is loaded merged
 * with the connector directories of the roots before it (theirs win on a shared path), as the enterprise build
 * merges them, so that an enterprise
 * connector extending a community file (<code>../../semconv/K8s</code>) loads; only its own connectors are checked.
 * A connector file that does not load is a failing difference, golden or not;</li>
 * <li><code>--connectors</code>: the connector ids to check, case-insensitive (default: every connector); an id no
 * connector carries is a failing difference;</li>
 * <li><code>--compare</code>: the comparison mode, see {@link CompareMode} (default: exact);</li>
 * <li><code>--update</code>: rewrite the goldens of the selected connectors (the revision pin is left alone: the
 * rewritten goldens are not committed yet);</li>
 * <li><code>--report</code>: print the content-type desugaring report of the selected connectors;</li>
 * <li><code>--pin</code>: once the regenerated goldens are committed, record the current commit of each root in the
 * revision pin ({@link ComputeChainGoldenCorpus#REVISIONS_FILE}) and check nothing.</li>
 * </ul>
 * One line is printed per difference; the exit code is 1 when any failing difference exists, 0 otherwise.
 * <p>
 * Build the classpath once, from the repository root:
 *
 * <pre>
 * mvn -pl metricshub-engine dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/cp.txt
 * </pre>
 *
 * then run from <code>metricshub-engine</code> (after <code>mvn -pl metricshub-engine test-compile</code>). On
 * Linux and macOS (<code>cp.txt</code> is written with <code>:</code>):
 *
 * <pre>
 * java -cp "target/classes:target/test-classes:$(cat target/cp.txt)" \
 *   org.metricshub.engine.strategy.source.compute.golden.GoldenCheck \
 *   --root ../../metricshub-community-connectors --connectors LinuxService --compare typed --report
 * </pre>
 *
 * On Windows, from Git Bash (<code>cp.txt</code> is written with <code>;</code>):
 *
 * <pre>
 * java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" \
 *   org.metricshub.engine.strategy.source.compute.golden.GoldenCheck \
 *   --root ../../metricshub-community-connectors --connectors LinuxService --compare typed --report
 * </pre>
 */
public final class GoldenCheck {

	private GoldenCheck() {}

	/**
	 * Entry point, see the class documentation.
	 *
	 * @param args The command line
	 * @throws IOException when a golden cannot be read or written
	 */
	public static void main(final String[] args) throws IOException {
		final List<Path> roots = new ArrayList<>();
		boolean report = false;
		boolean pin = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--root" -> roots.add(Path.of(args[++i]));
				case "--connectors" -> System.setProperty(ComputeChainGoldenCorpus.CONNECTORS_PROPERTY, args[++i]);
				case "--compare" -> System.setProperty(ComputeChainGoldenCorpus.COMPARE_PROPERTY, args[++i]);
				case "--update" -> System.setProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY, "true");
				case "--report" -> report = true;
				case "--pin" -> pin = true;
				default -> {
					System.err.println("Unknown argument: " + args[i]);
					System.exit(2);
				}
			}
		}
		if (roots.isEmpty()) {
			System.err.println(
				"Usage: GoldenCheck --root <repository> [--connectors A,B] [--compare typed|exact] [--update] [--report] [--pin]"
			);
			System.exit(2);
		}
		if (pin) {
			// A separate step, after the regenerated goldens are committed: the pin must name a commit that holds them.
			// Every root is checked first, so that a wrong root leaves the whole pin untouched instead of stale
			final List<Path> invalid = roots.stream().filter(root -> ComputeChainGoldenCorpus.gitHead(root) == null).toList();
			if (!invalid.isEmpty()) {
				invalid.forEach(root -> System.err.println("Not a git checkout, nothing pinned: " + root));
				System.exit(1);
			}
			// The pin is keyed by the repository directory name, and CI reads known keys only: a renamed checkout would
			// write a key nobody reads
			final Set<String> known = ComputeChainGoldenCorpus.pinnedRepositories();
			final List<Path> unknown = roots.stream()
				.filter(root -> !known.isEmpty() && !known.contains(root.toAbsolutePath().normalize().getFileName().toString()))
				.toList();
			if (!unknown.isEmpty()) {
				unknown.forEach(
					root -> System.err.println("Unknown repository name, nothing pinned: " + root + " (known: " + known + ")")
				);
				System.exit(1);
			}
			for (final Path root : roots) {
				ComputeChainGoldenCorpus.recordRevision(root);
			}
			System.out.println("Revision pin written to " + ComputeChainGoldenCorpus.REVISIONS_FILE.toAbsolutePath());
			System.exit(0);
		}
		System.exit(run(roots, report) > 0 ? 1 : 0);
	}

	/**
	 * Checks the selected connectors of the repositories, printing every difference and, when asked, the report.
	 *
	 * @param roots  The repository roots
	 * @param report Whether to print the content-type report
	 * @return the number of failing differences
	 * @throws IOException when a golden cannot be read or written
	 */
	static int run(final List<Path> roots, final boolean report) throws IOException {
		final CompareMode mode = ComputeChainGoldenCorpus.compareMode();
		final Map<Repository, ConnectorStore> stores = new LinkedHashMap<>();
		final List<String> allConnectorIds = new ArrayList<>();
		int failing = 0;
		final List<Path> existing = new ArrayList<>();
		for (final Path root : roots) {
			if (Files.isDirectory(root)) {
				existing.add(root);
			} else {
				System.err.println("Not a directory: " + root);
				failing++;
			}
		}
		for (final Repository repository : Repository.of(existing)) {
			final LoadedRepository loaded = ComputeChainGoldenCorpus.load(repository);
			final ConnectorStore store = loaded.store();
			stores.put(repository, store);
			final TreeSet<String> connectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			connectorIds.addAll(store.getStore().keySet());
			allConnectorIds.addAll(connectorIds);
			// A connector file that did not load has no golden, no check and no report: a failure, golden or not (its
			// golden is kept, even in update mode)
			allConnectorIds.addAll(loaded.unloaded().keySet());
			for (final Map.Entry<String, String> unloaded : loaded.unloaded().entrySet()) {
				if (ComputeChainGoldenCorpus.isSelected(unloaded.getKey())) {
					System.out.println(unloaded.getKey() + ": connector file did not load " + unloaded.getValue());
					failing++;
				}
			}
			for (final String connectorId : connectorIds) {
				if (!ComputeChainGoldenCorpus.isSelected(connectorId)) {
					continue;
				}
				for (final String difference : ComputeChainGoldenCorpus.check(repository, store, connectorId, mode)) {
					if (ComputeChainGoldenCorpus.isIntended(difference)) {
						final String prefix = ComputeChainGoldenCorpus.INTENDED_PREFIX;
						System.out.println(prefix + connectorId + ": " + difference.substring(prefix.length()));
					} else {
						System.out.println(connectorId + ": " + difference);
						failing++;
					}
				}
			}
			// A golden of a deleted or renamed connector: removed in update mode, a failure otherwise (as the
			// inventory check of the Maven test does)
			for (final String orphan : ComputeChainGoldenCorpus.orphanGoldens(repository, connectorIds)) {
				if (ComputeChainGoldenCorpus.isUpdateMode()) {
					Files.delete(repository.getGoldenDirectory().resolve(orphan + ".json"));
					System.out.println(orphan + ": stale golden deleted");
				} else {
					System.out.println(orphan + ": stored golden without a connector");
					failing++;
				}
			}
		}
		// A misspelt --connectors id selects nothing: say so instead of passing silently
		for (final String unknown : ComputeChainGoldenCorpus.unknownSelection(allConnectorIds)) {
			System.out.println(unknown + ": unknown connector");
			failing++;
		}
		if (report) {
			System.out.println(ComputeChainGoldenCorpus.contentTypeReport(stores));
		}
		System.out.println(failing + " failing difference(s)");
		return failing;
	}
}
