package org.metricshub.cli.service.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.DeviceKind;
import picocli.CommandLine.TypeConversionException;

class DeviceKindConverterTest {

	@Test
	void testConvert() throws Exception {
		final DeviceKindConverter deviceKindConverter = new DeviceKindConverter();
		assertEquals(DeviceKind.WINDOWS, deviceKindConverter.convert("win"));
		assertThrows(TypeConversionException.class, () -> deviceKindConverter.convert("unknown"));
	}
}
