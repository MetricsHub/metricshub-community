package org.metricshub.engine.connector.model.common;

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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a translation table used for mapping values in the context of a connector.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TranslationTable implements ITranslationTable {

	private static final long serialVersionUID = 1L;

	/**
	 * Translations mapping with case-insensitive keys.
	 */
	@Default
	@JsonIgnore
	private Map<String, String> translations = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * Creates a deep copy of the {@link TranslationTable} instance.
	 *
	 * @return A new instance of {@link TranslationTable} with copied translations.
	 */
	public TranslationTable copy() {
		return TranslationTable
			.builder()
			.translations(
				translations == null
					? null
					: translations
						.entrySet()
						.stream()
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (k1, k2) -> k2, HashMap::new))
			)
			.build();
	}

	/**
	 * Updates the translation values using the specified updater.
	 *
	 * @param updater An operation on a single operand that produces a result of the
	 *                same type as its operand.
	 */
	public void update(UnaryOperator<String> updater) {
		if (translations != null) {
			translations.replaceAll((key, val) -> updater.apply(val));
		}
	}

	/**
	 * Sets a translation for the specified key.
	 *
	 * @param key   The key for the translation.
	 * @param value The translation value.
	 */
	@JsonAnySetter
	public void setTranslation(String key, String value) {
		translations.put(key.toLowerCase(), value);
	}

	/**
	 * Retrieves the translations mapping.
	 *
	 * @return The translations mapping.
	 */
	@JsonAnyGetter
	public Map<String, String> getTranslations() {
		return this.translations;
	}
}
