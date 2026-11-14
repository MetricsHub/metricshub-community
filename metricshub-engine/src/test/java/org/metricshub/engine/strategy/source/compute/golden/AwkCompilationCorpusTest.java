package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.jawk.Awk;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.awk.MetricsHubExtensionForJawk;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.update.AwkScriptCheck;
import org.metricshub.engine.connector.update.ContentTypeDesugaring;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Repository;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;

/**
 * Every AWK script of the connector repositories compiles, in the Jawk sandbox (#1575): the scripts of the
 * <code>awk</code> computes and those of the <code>awk</code> sources, which the compute-chain goldens do not run.
 * The engine compiles a script the first time it runs (#1576), so nothing else tells that a script of a connector
 * nobody tests does not compile. Skipped when no connector repository is checked out.
 */
class AwkCompilationCorpusTest {

	/** An engine as the awk source builds it, with the MetricsHub functions. */
	private static final Awk SOURCE_ENGINE = AwkExecutor.newAwk(null, MetricsHubExtensionForJawk.builder().build());

	@Test
	void everyScriptOfTheConnectorRepositoriesCompiles() throws IOException {
		final List<Repository> repositories = Repository.existing();
		assumeFalse(repositories.isEmpty(), "No connector repository is checked out");

		final List<String> failures = new ArrayList<>();
		final int[] compiled = { 0 };
		for (final Repository repository : repositories) {
			ComputeChainGoldenCorpus.load(repository)
				.store()
				.getStore()
				.forEach((connectorId, connector) -> ContentTypeDesugaring.forEachSource(connector, source -> {
					final String where = connectorId + " " + source.getKey();
					if (source instanceof JawkSource jawkSource) {
						compiled[0]++;
						compile(connector, jawkSource.getScript(), SOURCE_ENGINE, where, failures);
					}
					for (final Compute compute : source.getComputes() == null ? List.<Compute>of() : source.getComputes()) {
						if (compute instanceof org.metricshub.engine.connector.model.monitor.task.source.compute.Awk awk) {
							compiled[0]++;
							compile(connector, awk.getScript(), AwkExecutor.getUtilityEngine(), where, failures);
							// No reference is replaced in a script: it gets its values through its variables
							if (AwkScriptCheck.holdsReference(awk.getScript(), connector)) {
								failures.add(where + " (a reference in the script)");
							}
						}
					}
				}));
		}

		assertEquals(List.of(), failures, "The AWK scripts of these sources do not compile");
		assertTrue(compiled[0] > 0, "The repositories have AWK scripts");
	}

	/**
	 * Compiles a script as the run does: the embedded file the <code>script</code> property references, or the
	 * property itself.
	 */
	private static void compile(
		final Connector connector,
		final String script,
		final Awk engine,
		final String where,
		final List<String> failures
	) {
		try {
			final String code = EmbeddedFileHelper.findScript(script, connector::getEmbeddedFiles, "n/a", where)
				.orElseThrow()
				.getContentAsString();
			AwkExecutor.compile(code, engine);
		} catch (Exception e) {
			failures.add(where);
		}
	}
}
