package org.metricshub.extension.emulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.extension.emulation.wmi.WmiEmulationImage;

/**
 * Tests for {@link EmulationImageCacheManager}.
 */
class EmulationImageCacheManagerTest {

	@Test
	void testGetImageReusesCachedImageWhenFileUnchanged(@TempDir final Path tempDir) throws Exception {
		final Path indexFile = tempDir.resolve("image.yaml");
		Files.writeString(
			indexFile,
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Win32_ComputerSystem
			      namespace: root\\cimv2
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);

		final EmulationImageCacheManager cacheManager = new EmulationImageCacheManager();
		final WmiEmulationImage firstRead = cacheManager.getImage(indexFile, WmiEmulationImage.class);
		final WmiEmulationImage secondRead = cacheManager.getImage(indexFile, WmiEmulationImage.class);

		assertSame(firstRead, secondRead);
	}

	@Test
	void testGetImageReloadsWhenFileChanges(@TempDir final Path tempDir) throws Exception {
		final Path indexFile = tempDir.resolve("image.yaml");
		Files.writeString(
			indexFile,
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Win32_ComputerSystem
			      namespace: root\\cimv2
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);

		final EmulationImageCacheManager cacheManager = new EmulationImageCacheManager();
		final WmiEmulationImage firstRead = cacheManager.getImage(indexFile, WmiEmulationImage.class);

		Files.writeString(
			indexFile,
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Win32_ComputerSystem
			      namespace: root\\cimv2
			    response: r1.txt
			  - request:
			      wql: SELECT Name FROM Win32_Process
			      namespace: root\\cimv2
			    response: r2.txt
			""",
			StandardCharsets.UTF_8
		);

		final WmiEmulationImage secondRead = cacheManager.getImage(indexFile, WmiEmulationImage.class);

		assertNotSame(firstRead, secondRead);
		assertEquals(2, secondRead.getImage().size());
	}
}
