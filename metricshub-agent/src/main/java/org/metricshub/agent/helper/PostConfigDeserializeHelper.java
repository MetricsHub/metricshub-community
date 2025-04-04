package org.metricshub.agent.helper;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.agent.deserialization.PostConfigDeserializer;

/**
 * Helper class for adding post-deserialization support to an {@link ObjectMapper}.
 * It provides a utility method to register a {@link PostConfigDeserializer} for post-processing
 * the deserialization of certain types.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PostConfigDeserializeHelper {

	/**
	 * Add the {@link PostConfigDeserializer} as Post deserializer to the given
	 * {@link ObjectMapper}
	 *
	 * @param objectMapper provides functionality for reading and writing JSON
	 * @return the updated {@link ObjectMapper}
	 */
	public static ObjectMapper addPostDeserializeSupport(final ObjectMapper objectMapper) {
		final SimpleModule module = new SimpleModule();
		module.setDeserializerModifier(
			new BeanDeserializerModifier() {
				private static final long serialVersionUID = 1L;

				@Override
				public JsonDeserializer<?> modifyDeserializer(
					DeserializationConfig config,
					BeanDescription beanDescription,
					JsonDeserializer<?> originalDeserializer
				) {
					return new PostConfigDeserializer(originalDeserializer);
				}
			}
		);

		objectMapper.registerModule(module);

		return objectMapper;
	}
}
