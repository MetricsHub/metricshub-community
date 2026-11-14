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

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;

/**
 * Checks the AWK scripts of the <code>awk</code> computes when the connector loads: a script is code, compiled once
 * and kept in the connector, so no reference is replaced in it. A script that holds one, such as
 * <code>${param::days}</code> or <code>${attribute::id}</code>, would read it as text: it is reported, and the value
 * belongs in the <code>variables</code> of the compute.
 * <p>
 * The scripts themselves are compiled the first time they run (see the <code>program</code> of {@link Awk}).
 */
@Slf4j
public class AwkScriptCheck extends AbstractConnectorUpdateChain {

	/**
	 * A reference such as <code>${param::days}</code> or <code>${attribute::id}</code>. Not
	 * <code>${file::script.awk}</code>, which names the script.
	 */
	private static final Pattern REFERENCE = Pattern.compile("\\$\\{(?!file::)[^{}]*::", Pattern.CASE_INSENSITIVE);

	/**
	 * Whether the script of an <code>awk</code> compute holds a reference, which is not replaced in a script.
	 *
	 * @param script The <code>script</code> property
	 * @return whether it holds a reference
	 */
	public static boolean holdsReference(final String script) {
		return script != null && REFERENCE.matcher(script).find();
	}

	@Override
	void doUpdate(final Connector connector) {
		ContentTypeDesugaring.forEachSource(connector, source -> {
			if (source.getComputes() == null) {
				return;
			}
			for (final Compute compute : source.getComputes()) {
				if (compute instanceof Awk awk && holdsReference(awk.getScript())) {
					log.error(
						"Connector {} - Source {} - The AWK script holds a reference, which is not replaced in a script:" +
							" pass the value through the variables of the awk compute.",
						connector.getConnectorIdentity() != null && connector.getConnectorIdentity().getCompiledFilename() != null
							? connector.getConnectorIdentity().getCompiledFilename()
							: "<unnamed>",
						source.getKey()
					);
				}
			}
		});
	}
}
