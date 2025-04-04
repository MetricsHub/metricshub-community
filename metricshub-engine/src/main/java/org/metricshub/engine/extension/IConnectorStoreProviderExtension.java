package org.metricshub.engine.extension;

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

import org.metricshub.engine.connector.model.ConnectorStore;

/**
 * Defines the contract for extensions that provide and manage a connector store. A connector store
 * is a collection of connectors that define how to interact with different data sources, services, or protocols.
 * Implementations of this interface are  responsible for loading the connector store and providing access to it.
 */
public interface IConnectorStoreProviderExtension {
	/**
	 * Loads the connector store into memory. This method initializes connector store, making it ready for use.
	 */
	void load();

	/**
	 * Retrieves the loaded connector store.
	 * @return A {@link ConnectorStore} instance containing the connectors to be used by the engine. The returned
	 *         connector store is expected to be fully initialized and ready for use.
	 */
	ConnectorStore getConnectorStore();
}
