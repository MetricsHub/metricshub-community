package org.metricshub.engine.connector.update;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import lombok.Data;
import lombok.NonNull;
import org.metricshub.engine.connector.model.Connector;

/**
 * An abstract class implementing the {@link ConnectorUpdateChain} interface.
 * It provides the structure for handling the update chain and ensures that the next chain in the sequence is called.
 */
@Data
public abstract class AbstractConnectorUpdateChain implements ConnectorUpdateChain {

	protected ConnectorUpdateChain nextChain;

	@Override
	public void setNextUpdateChain(ConnectorUpdateChain nextChain) {
		this.nextChain = nextChain;
	}

	@Override
	public void update(@NonNull Connector connector) {
		doUpdate(connector);

		// Call next update chain
		if (nextChain != null) {
			nextChain.update(connector);
		}
	}

	abstract void doUpdate(Connector connector);
}
