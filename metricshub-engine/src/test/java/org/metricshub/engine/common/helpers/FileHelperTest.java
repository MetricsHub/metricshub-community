package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FileHelperTest {

	@Test
	void testGetExtension() {
		// Verify extensions
		assertEquals(".pdf", FileHelper.getExtension("example.pdf"));
		assertEquals(".gz", FileHelper.getExtension("archive.tar.gz"));
		assertEquals(MetricsHubConstants.EMPTY, FileHelper.getExtension(".env"));
		assertEquals(MetricsHubConstants.EMPTY, FileHelper.getExtension("no_extension"));
	}

	@Test
	void testGetBaseName() {
		// Verify base names
		assertEquals("example", FileHelper.getBaseName("example.pdf"));
		assertEquals("archive.tar", FileHelper.getBaseName("archive.tar.gz"));
		assertEquals(".env", FileHelper.getBaseName(".env"));
		assertEquals("no_extension", FileHelper.getBaseName("no_extension"));
	}
}
