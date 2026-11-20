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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.telemetry.TelemetryManager;

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

		final Probe probe = TcclClassLoaderDecorator.wrap(Probe.class, new Probe() {
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
		}, extensionLoader);

		assertSame(extensionLoader, probe.tccl(), "TCCL is the extension loader during the call");
		assertSame(original, thread.getContextClassLoader(), "TCCL is restored after the call");
	}

	@Test
	void testCheckedExceptionPropagatesUnwrapped() {
		final ClassLoader extensionLoader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());
		final Thread thread = Thread.currentThread();
		final ClassLoader original = thread.getContextClassLoader();

		final Probe probe = TcclClassLoaderDecorator.wrap(Probe.class, new Probe() {
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
		}, extensionLoader);

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

	@Test
	void testKeepsOtherInterfacesOfTheExtension() {
		final ClassLoader extensionLoader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());
		final Thread thread = Thread.currentThread();
		final ClassLoader original = thread.getContextClassLoader();
		final HttpProbe delegate = new HttpProbe();

		final Probe probe = TcclClassLoaderDecorator.wrap(Probe.class, delegate, extensionLoader);

		final IHttpRequestExtension httpExtension = assertInstanceOf(
			IHttpRequestExtension.class,
			probe,
			"The proxy keeps IHttpRequestExtension, which SourceProcessor.executeHttpRequest() looks for"
		);
		assertNull(httpExtension.executeHttpRequest(null, "connector", null));
		assertSame(extensionLoader, delegate.tcclDuringRequest, "The extension's request runs under its own loader");
		assertSame(original, thread.getContextClassLoader(), "TCCL is restored after the call");
	}

	@Test
	void testKeepsTheParentsOfAnInterfaceItCannotImplement() {
		final Probe probe = TcclClassLoaderDecorator.wrap(
			Probe.class,
			new LocalHttpProbe(),
			new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())
		);

		assertInstanceOf(
			IHttpRequestExtension.class,
			probe,
			"IHttpRequestExtension, inherited through an interface the proxy cannot implement, is kept"
		);
	}

	@Test
	void testSkipsSealedInterfaces() {
		final ClassLoader extensionLoader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());

		final Probe probe = TcclClassLoaderDecorator.wrap(Probe.class, new SealedProbe(), extensionLoader);

		assertSame(extensionLoader, probe.tccl(), "The extension still loads and runs under its own loader");
		assertFalse(probe instanceof Sealed, "A proxy cannot implement a sealed interface");
	}

	/** A non-public interface: the proxy cannot implement it, but can implement its parent. */
	interface LocalHttp extends IHttpRequestExtension {}

	/** A probe whose HTTP capability comes through {@link LocalHttp}. */
	private static final class LocalHttpProbe extends ProbeImpl implements LocalHttp {

		@Override
		public HttpResult executeHttpRequest(
			final HttpSource httpSource,
			final String connectorId,
			final TelemetryManager telemetryManager
		) {
			return null;
		}
	}

	/** A public sealed interface, which {@link java.lang.reflect.Proxy} rejects. */
	public sealed interface Sealed permits SealedProbe {}

	/** A probe that also implements a sealed interface. */
	private static final class SealedProbe extends ProbeImpl implements Sealed {}

	/** A probe that also executes HTTP requests, recording the TCCL it ran under. */
	private static final class HttpProbe extends ProbeImpl implements IHttpRequestExtension {

		private ClassLoader tcclDuringRequest;

		@Override
		public HttpResult executeHttpRequest(
			final HttpSource httpSource,
			final String connectorId,
			final TelemetryManager telemetryManager
		) {
			tcclDuringRequest = Thread.currentThread().getContextClassLoader();
			return null;
		}
	}

	/** Simple probe returning the current TCCL. */
	private static class ProbeImpl implements Probe {

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
