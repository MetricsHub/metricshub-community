package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

class ResourceHelperTest {

	private static final String EXPECTED_TEXT = "text text\n";
	private static final String DATA_TXT = "/test-files/data.txt";
	private static final String DATA_FILE_NOT_FOUND_TXT = "/test-files/fileNotFound.txt";

	@Test
	void testGetResourceAsStringNullPath() {
		assertThrows(IllegalArgumentException.class, () -> ResourceHelper.getResourceAsString(null, ResourceHelper.class));
	}

	@Test
	void testGetResourceAsStringEmptyPath() {
		assertThrows(IllegalArgumentException.class, () -> ResourceHelper.getResourceAsString("", ResourceHelper.class));
	}

	@Test
	void testGetResourceAsStringNullClass() {
		assertThrows(IllegalArgumentException.class, () -> ResourceHelper.getResourceAsString(DATA_TXT, null));
	}

	@Test
	void testGetResourceAsStringFileNotFound() {
		assertThrows(
			IllegalStateException.class,
			() -> ResourceHelper.getResourceAsString(DATA_FILE_NOT_FOUND_TXT, ResourceHelper.class)
		);
	}

	@Test
	void testGetResourceAsString() {
		assertEquals(EXPECTED_TEXT, ResourceHelper.getResourceAsString(DATA_TXT, ResourceHelper.class));
	}

	@Test
	void testFindSource() throws IOException, URISyntaxException {
		assertNotNull(ResourceHelper.findSourceDirectory(ResourceHelper.class));
	}
}
