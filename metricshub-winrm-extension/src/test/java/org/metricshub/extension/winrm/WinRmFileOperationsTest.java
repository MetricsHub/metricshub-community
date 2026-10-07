package org.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.FileHelper.PathPattern;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.winrm.WinRMClient;

class WinRmFileOperationsTest {

	private static boolean matches(final String pathPattern, final String path) {
		final PathPattern pattern = FileHelper.parsePathPattern(pathPattern, DeviceKind.WINDOWS);
		final List<Pattern> globs = pattern.segments().stream().map(WinRmFileOperations::globToRegex).toList();
		return WinRmFileOperations.matches(WinRmFileOperations.rootPrefix(pattern), globs, path);
	}

	@Test
	void testRootPrefix() {
		// A drive root already ends with a backslash, a directory root does not
		assertEquals("C:\\", WinRmFileOperations.rootPrefix(FileHelper.parsePathPattern("C:\\*.log", DeviceKind.WINDOWS)));
		assertEquals(
			"C:\\Program Files\\MetricsHub\\logs\\",
			WinRmFileOperations.rootPrefix(
				FileHelper.parsePathPattern("C:\\Program Files\\MetricsHub\\logs\\*.log", DeviceKind.WINDOWS)
			)
		);
	}

	@Test
	void testMatchesFilenameGlob() {
		assertTrue(matches("C:\\logs\\*.log", "C:\\logs\\app.log"));
		assertTrue(matches("C:\\logs\\app?.log", "C:\\logs\\app1.log"));
		assertFalse(matches("C:\\logs\\app?.log", "C:\\logs\\app12.log"));
		// Whole-name match: *.log does not match app.log.1
		assertFalse(matches("C:\\logs\\*.log", "C:\\logs\\app.log.1"));
		// Not deeper than the pattern
		assertFalse(matches("C:\\logs\\*.log", "C:\\logs\\archive\\app.log"));
		// Not outside the root
		assertFalse(matches("C:\\logs\\*.log", "C:\\logs2\\app.log"));
		// A trailing backslash lists all the files of the directory
		assertTrue(matches("C:\\logs\\", "C:\\logs\\anything"));
	}

	@Test
	void testMatchesDirectoryWildcards() {
		final String pattern = "D:\\Autosys_waae\\autouser*\\out\\event_demon*PE2";
		assertTrue(matches(pattern, "D:\\Autosys_waae\\autouser01\\out\\event_demon.PE2"));
		assertTrue(matches(pattern, "D:\\Autosys_waae\\autouser02\\out\\event_demon_XPE2"));
		assertFalse(matches(pattern, "D:\\Autosys_waae\\other\\out\\event_demon.PE2"));
		assertFalse(matches(pattern, "D:\\Autosys_waae\\autouser01\\err\\event_demon.PE2"));
		assertTrue(matches("C:\\*\\*.log", "C:\\logs\\app.log"));
		assertFalse(matches("C:\\*\\*.log", "C:\\app.log"));
	}

	@Test
	void testMatchesIgnoresCaseAndTreatsOtherCharactersLiterally() {
		assertTrue(matches("C:\\Logs\\*.LOG", "c:\\logs\\App.log"));
		assertTrue(matches("C:\\logs\\app[1].log", "C:\\logs\\app[1].log"));
		assertFalse(matches("C:\\logs\\app[1].log", "C:\\logs\\app1.log"));
		assertTrue(matches("C:\\data\\$logs\\a.b", "C:\\data\\$logs\\a.b"));
		assertFalse(matches("C:\\data\\$logs\\a.b", "C:\\data\\$logs\\aXb"));
		assertTrue(matches("C:\\journaux\\*.log", "C:\\journaux\\été.log"));
	}

	@Test
	void testCloseClosesTheClient() {
		final WinRMClient client = mock(WinRMClient.class);
		new WinRmFileOperations(client, "host").close();
		verify(client).close();
	}
}
