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

import io.jawk.AwkProgram;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;

/**
 * Compiles the AWK scripts of a connector, so that the connector holds the compiled programs: the script of every
 * <code>awk</code> source (the <code>program</code> of {@link JawkSource}) and of every <code>awk</code> compute (the
 * <code>program</code> of {@link Awk}), in <code>beforeAll</code>, <code>afterAll</code>, the monitor jobs and the
 * tools.
 * <p>
 * A script that does not compile is reported here, when the connector loads. Its program stays null and the run
 * reports the script again, as it did before the scripts were compiled at load.
 */
@Slf4j
public class AwkCompilationUpdate extends AbstractConnectorUpdateChain {

	/**
	 * Stands for the hostname in the messages of {@link EmbeddedFileHelper}: no host is involved when a connector loads.
	 */
	private static final String NO_HOST = "n/a";

	/**
	 * A reference that is replaced when a compute runs, such as <code>${param::days}</code> or
	 * <code>${attribute::id}</code>. Not <code>${file::script.awk}</code>, which names the script.
	 */
	private static final Pattern REFERENCE = Pattern.compile("\\$\\{(?!file::)[^{}]*::", Pattern.CASE_INSENSITIVE);

	@Override
	void doUpdate(final Connector connector) {
		ContentTypeDesugaring.forEachSource(connector, source -> {
			if (source instanceof JawkSource jawkSource) {
				jawkSource.setProgram(compile(connector, source, jawkSource.getScript(), true));
			}
			if (source.getComputes() != null) {
				for (final Compute compute : source.getComputes()) {
					// An inline compute script that holds a reference (${param::days}) is not AWK yet: the reference is
					// replaced when the compute runs, and the resulting script is compiled then
					if (compute instanceof Awk awk && !REFERENCE.matcher(awk.getScript()).find()) {
						awk.setProgram(compile(connector, source, awk.getScript(), false));
					}
				}
			}
		});
	}

	/**
	 * Compiles the script of an <code>awk</code> source or compute.
	 *
	 * @param connector    The connector, for its embedded files and its identifier
	 * @param source       The source that holds the script, or the compute that holds it
	 * @param script       The <code>script</code> property: a reference to an embedded file, or the code
	 * @param sourceScript Whether the script is the script of an <code>awk</code> source, which may call the
	 *                     MetricsHub functions
	 * @return the compiled program, null when there is no script or when it does not compile
	 */
	private static AwkProgram compile(
		final Connector connector,
		final Source source,
		final String script,
		final boolean sourceScript
	) {
		if (script == null) {
			return null;
		}
		final String connectorId = connectorId(connector);
		try {
			final Optional<EmbeddedFile> embeddedFile = EmbeddedFileHelper.findScript(
				script,
				connector::getEmbeddedFiles,
				NO_HOST,
				connectorId
			);
			if (embeddedFile.isEmpty()) {
				log.error("Connector {} - Source {} - AWK script {} cannot be found.", connectorId, source.getKey(), script);
				return null;
			}
			final String code = embeddedFile.get().getContentAsString();
			return sourceScript
				? AwkExecutor.compileSourceScript(code)
				: AwkExecutor.compile(code, AwkExecutor.getUtilityEngine());
		} catch (Exception e) {
			// The cause holds the message of Jawk: the line and what it rejects
			log.error(
				"Connector {} - Source {} - AWK script {} does not compile: {}",
				connectorId,
				source.getKey(),
				script.lines().findFirst().orElse(script),
				e.getCause() != null ? e.getCause().getMessage() : e.getMessage()
			);
			log.debug("Connector {} - Source {} - AWK script compilation failure: ", connectorId, source.getKey(), e);
			return null;
		}
	}

	/**
	 * The identifier of the connector for the messages. A connector that is not in a store yet (a configured monitor
	 * job, a test) has none.
	 *
	 * @param connector The connector
	 * @return the identifier, or a placeholder
	 */
	private static String connectorId(final Connector connector) {
		final String id = connector.getConnectorIdentity() != null
			? connector.getConnectorIdentity().getCompiledFilename()
			: null;
		return id != null ? id : "<unnamed>";
	}
}
