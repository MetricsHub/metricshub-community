package org.metricshub.engine.extension;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

class TcclClassLoaderDecoratorTest {

	/** A tiny SPI-like interface whose methods observe and manipulate the TCCL. */
	public interface Probe {
		ClassLoader tccl();

		void boom() throws IOException;

		ClassLoader reentrant(Probe inner);
	}

	@Test
	void testSetsAndRestoresTccl() {
		final ClassLoader extensionLoader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());
		final Thread thread = Thread.currentThread();
		final ClassLoader original = thread.getContextClassLoader();

		final Probe probe = TcclClassLoaderDecorator.wrap(
			Probe.class,
			new Probe() {
				@Override
				public ClassLoader tccl() {
					return Thread.currentThread().getContextClassLoader();
				}

				@Override
				public void boom() throws IOException {
					throw new IOException("expected");
				}

				@Override
				public ClassLoader reentrant(final Probe inner) {
					return inner.tccl();
				}
			},
			extensionLoader
		);

		assertSame(extensionLoader, probe.tccl(), "TCCL is the extension loader during the call");
		assertSame(original, thread.getContextClassLoader(), "TCCL is restored after the call");
	}

	@Test
	void testCheckedExceptionPropagatesUnwrapped() {
		final ClassLoader extensionLoader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());
		final Thread thread = Thread.currentThread();
		final ClassLoader original = thread.getContextClassLoader();

		final Probe probe = TcclClassLoaderDecorator.wrap(
			Probe.class,
			new Probe() {
				@Override
				public ClassLoader tccl() {
					return Thread.currentThread().getContextClassLoader();
				}

				@Override
				public void boom() throws IOException {
					throw new IOException("expected");
				}

				@Override
				public ClassLoader reentrant(final Probe inner) {
					return inner.tccl();
				}
			},
			extensionLoader
		);

		final IOException thrown = assertThrows(IOException.class, probe::boom);
		assertEquals("expected", thrown.getMessage(), "The extension's own checked exception propagates unchanged");
		assertSame(original, thread.getContextClassLoader(), "TCCL is restored even when the call throws");
	}

	@Test
	void testReentrantCallsNestCorrectly() {
		final ClassLoader loaderOuter = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());
		final ClassLoader loaderInner = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());
		final Thread thread = Thread.currentThread();
		final ClassLoader original = thread.getContextClassLoader();

		final Probe inner = TcclClassLoaderDecorator.wrap(Probe.class, new ProbeImpl(), loaderInner);
		final Probe outer = TcclClassLoaderDecorator.wrap(Probe.class, new ProbeImpl(), loaderOuter);

		// outer.reentrant() runs under loaderOuter and calls inner.tccl() which must swap to
		// loaderInner and, on return, the outer frame must still see loaderOuter restored.
		assertSame(loaderInner, outer.reentrant(inner), "Nested call observes the inner extension's loader");
		assertSame(original, thread.getContextClassLoader(), "TCCL fully restored after nested calls");
	}

	/** Simple probe returning the current TCCL. */
	private static final class ProbeImpl implements Probe {

		@Override
		public ClassLoader tccl() {
			return Thread.currentThread().getContextClassLoader();
		}

		@Override
		public void boom() throws IOException {
			throw new IOException("expected");
		}

		@Override
		public ClassLoader reentrant(final Probe innerProbe) {
			return innerProbe.tccl();
		}
	}
}
