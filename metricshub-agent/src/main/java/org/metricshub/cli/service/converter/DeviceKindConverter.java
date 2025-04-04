package org.metricshub.cli.service.converter;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import lombok.NonNull;
import org.metricshub.engine.connector.model.common.DeviceKind;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Converter for converting a string to a {@link DeviceKind}.
 * It uses the {@link DeviceKind#detect(String)} method for conversion.
 */
public class DeviceKindConverter implements ITypeConverter<DeviceKind> {

	/**
	 * Converts the given string to a {@link DeviceKind} using the {@link DeviceKind#detect(String)} method.
	 *
	 * @param type the string representation of the device kind
	 * @return the corresponding {@link DeviceKind} instance
	 * @throws TypeConversionException if an error occurs during conversion
	 */
	@Override
	public DeviceKind convert(@NonNull final String type) throws Exception {
		try {
			return DeviceKind.detect(type);
		} catch (Exception e) {
			throw new TypeConversionException(e.getMessage());
		}
	}
}
