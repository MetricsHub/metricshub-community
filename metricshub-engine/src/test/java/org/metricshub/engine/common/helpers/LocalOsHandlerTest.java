package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.mockito.Mockito.mockStatic;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.metricshub.engine.common.helpers.LocalOsHandler.FreeBsd;
import org.metricshub.engine.common.helpers.LocalOsHandler.ILocalOs;
import org.metricshub.engine.common.helpers.LocalOsHandler.NetBsd;
import org.metricshub.engine.common.helpers.LocalOsHandler.OpenBsd;
import org.mockito.MockedStatic;

class LocalOsHandlerTest {

	@Test
	void testDetectOsNotFoundlSystemOsName() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.empty());
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			assertEquals(Optional.empty(), LocalOsHandler.detectOs());
		}
	}

	@Test
	void testDetectOsUnknown() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("OS/400"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			assertEquals(Optional.empty(), LocalOsHandler.detectOs());
		}
	}

	@Test
	void testDetectOsWindows() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("Windows 8.1"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.WINDOWS, os);
			assertFalse(os.isUnix());
		}
	}

	@Test
	void testDetectOsLinux() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("Linux"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.LINUX, os);
			assertTrue(os.isUnix());
		}
	}

	@Test
	void testDetectOsAix() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("AIX"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.AIX, os);
			assertTrue(os.isUnix());
		}
	}

	@Test
	void testDetectOsSolaris() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("Solaris"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.SOLARIS, os);
			assertTrue(os.isUnix());
		}
	}

	@Test
	void testDetectOsSunOs() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("sunos"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.SUN, os);
			assertTrue(os.isUnix());
		}
	}

	@Test
	void testDetectOsHp() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("HP-UX"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.HP, os);
			assertTrue(os.isUnix());
		}
	}

	@Test
	void testDetectOsFreeBsd() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("FreeBsd"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.FREE_BSD, os);
			assertTrue(os.isUnix());

			final FreeBsd bsdOS = (FreeBsd) os;
			assertTrue(bsdOS.isBsd());
		}
	}

	@Test
	void testDetectOsOpenBsd() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("OpenBsd"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.OPEN_BSD, os);
			assertTrue(os.isUnix());

			final OpenBsd bsdOS = (OpenBsd) os;
			assertTrue(bsdOS.isBsd());
		}
	}

	@Test
	void testDetectOsNetBsd() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("NetBsd"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.NET_BSD, os);
			assertTrue(os.isUnix());

			final NetBsd bsdOS = (NetBsd) os;
			assertTrue(bsdOS.isBsd());
		}
	}

	@Test
	void testDetectOsMacOSX() {
		try (final MockedStatic<LocalOsHandler> mockedLocalOsHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOsHandler.when(LocalOsHandler::getSystemOsName).thenReturn(Optional.of("Mac OS X"));
			mockedLocalOsHandler.when(LocalOsHandler::detectOs).thenCallRealMethod();

			final Optional<ILocalOs> maybeOS = LocalOsHandler.detectOs();
			assertNotNull(maybeOS);
			assertTrue(maybeOS.isPresent());

			final ILocalOs os = maybeOS.get();
			assertEquals(LocalOsHandler.MAC_OS_X, os);
			assertTrue(os.isUnix());
		}
	}

	@Test
	@EnabledOnOs(WINDOWS)
	void testIsWindows() {
		assertTrue(LocalOsHandler.isWindows());
	}

	@Test
	@EnabledOnOs(LINUX)
	void testIsNotWindows() {
		assertFalse(LocalOsHandler.isWindows());
	}
}
