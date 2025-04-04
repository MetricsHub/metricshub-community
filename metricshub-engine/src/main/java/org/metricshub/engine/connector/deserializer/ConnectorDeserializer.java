package org.metricshub.engine.connector.deserializer;

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

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.Getter;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.Connector;

/**
 * Deserializes YAML connector files.
 */
public class ConnectorDeserializer {

	@Getter
	private ObjectMapper mapper;

	/**
	 * Constructs a new ConnectorDeserializer with the specified ObjectMapper.
	 *
	 * @param mapper The ObjectMapper to use for deserialization.
	 */
	public ConnectorDeserializer(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	/**
	 * Deserializes the given YAML connector input stream.
	 *
	 * @param input YAML as {@link InputStream}.
	 * @return {@link Connector} instance.
	 * @throws IOException If an I/O error occurs during deserialization.
	 */
	public Connector deserialize(final InputStream input) throws IOException {
		return JsonHelper.deserialize(mapper, input, Connector.class);
	}

	/**
	 * Deserializes the given YAML connector file.
	 *
	 * @param src YAML file.
	 * @return {@link Connector} instance.
	 * @throws IOException If an I/O error occurs during deserialization.
	 */
	public Connector deserialize(final File src) throws IOException {
		return deserialize(new FileInputStream(src));
	}

	/**
	 * Deserializes the given YAML connector node.
	 *
	 * @param node YAML as {@link TreeNode}.
	 * @return {@link Connector} instance.
	 * @throws IOException If an I/O error occurs during deserialization.
	 */
	public Connector deserialize(final TreeNode node) throws IOException {
		return JsonHelper.deserialize(mapper, node, Connector.class);
	}
}
