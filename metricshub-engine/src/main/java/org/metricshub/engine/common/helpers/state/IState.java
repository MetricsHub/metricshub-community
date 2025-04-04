package org.metricshub.engine.common.helpers.state;

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

import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.NumberHelper;

/**
 * Interface representing a generic state.
 */
public interface IState {
	/**
	 * Get the numeric value associated with the state.
	 *
	 * @return The numeric value as int
	 */
	int getNumericValue();

	/**
	 * Interpret the given {@code state} value based on the {@code stateTranslations} lookup.
	 *
	 * @param <T>               Type extending {@link IState}
	 * @param state             The state value to interpret
	 * @param stateTranslations The translation lookup from which we get {@link IState} by key
	 * @param type              The type used to cast the result
	 * @return {@link Optional} of {@code T extends IState}
	 */
	static <T extends IState> Optional<T> interpret(
		final String state,
		final @NonNull Map<String, T> stateTranslations,
		final @NonNull Class<T> type
	) {
		if (state == null || state.isBlank()) {
			return Optional.empty();
		}

		final IState status = stateTranslations.get(NumberHelper.cleanUpEnumInput(state));

		return Optional.ofNullable(status).map(type::cast);
	}
}
