package com.sentrysoftware.matrix.connector.model.common;

import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.DEFAULT;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TranslationTable implements ITranslationTable {

	private static final long serialVersionUID = 1L;

	@Default
	@JsonIgnore
	private Map<String, String> translations = new HashMap<>();

	/**
	 * Copy the {@link TranslationTable} instance
	 *
	 * @return {@link TranslationTable} deep copy
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
	 * Update the given translation table
	 *
	 * @param updater An operation on a single operand that produces a result of the
	 *                same type as its operand.
	 */
	public void update(UnaryOperator<String> updater) {
		if (translations != null) {
			translations.replaceAll((key, val) -> updater.apply(val));
		}
	}

	@JsonAnySetter
	public void setTranslation(String key, String value) {
		translations.put(key.equalsIgnoreCase(DEFAULT) ? DEFAULT : key, value);
	}

	@JsonAnyGetter
	public Map<String, String> getTranslations() {
		return this.translations;
	}
}
