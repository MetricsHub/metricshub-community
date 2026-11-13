package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.update.ContentTypeDesugaring;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Repository;

/**
 * Every AWK script of the connector repositories compiles when its connector loads (#1576), in the Jawk sandbox
 * (#1575): the scripts of the <code>awk</code> computes and those of the <code>awk</code> sources, which the
 * compute-chain goldens do not run. Skipped when no connector repository is checked out.
 */
class AwkCompilationCorpusTest {

	@Test
	void everyScriptOfTheConnectorRepositoriesCompiles() throws IOException {
		final List<Repository> repositories = Repository.existing();
		assumeFalse(repositories.isEmpty(), "No connector repository is checked out");

		final List<String> notCompiled = new ArrayList<>();
		final int[] compiled = { 0 };
		for (final Repository repository : repositories) {
			ComputeChainGoldenCorpus.load(repository)
				.store()
				.getStore()
				.forEach((connectorId, connector) -> ContentTypeDesugaring.forEachSource(connector, source -> {
					final List<Object> programs = new ArrayList<>();
					if (source instanceof JawkSource jawkSource) {
						programs.add(jawkSource.getProgram());
					}
					if (source.getComputes() != null) {
						source.getComputes().stream().filter(Awk.class::isInstance).forEach(compute -> {
							programs.add(((Awk) compute).getProgram());
							// No reference is replaced in a script: it gets its values through its variables
							if (((Awk) compute).getScript().matches("(?s).*\\$\\{(?!file::)[^{}]*::.*")) {
								notCompiled.add(connectorId + " " + source.getKey() + " (a reference in the script)");
							}
						});
					}
					compiled[0] += programs.size();
					if (programs.contains(null)) {
						notCompiled.add(connectorId + " " + source.getKey());
					}
				}));
		}

		assertEquals(List.of(), notCompiled, "The AWK scripts of these sources do not compile");
		assertTrue(compiled[0] > 0, "The repositories have AWK scripts");
	}
}
