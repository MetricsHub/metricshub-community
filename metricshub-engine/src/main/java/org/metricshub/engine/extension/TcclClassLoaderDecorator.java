package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;

/**
 * Wraps an extension SPI instance so that every call runs with the extension's own class loader as
 * the thread context class loader (TCCL), restoring the previous TCCL afterwards.
 *
 * <p>Extension libraries resolve service providers (JAXP {@code DocumentBuilderFactory}, StAX,
 * JAXB, JDBC {@code DriverManager}, …) through the TCCL. Once each extension lives in its own
 * {@link ExtensionClassLoader}, those lookups must resolve within the extension's loader rather than
 * the application loader. Rather than sprinkle try/finally around every engine→extension call site,
 * the loader wraps each SPI instance once, at load time, so all call sites are covered
 * transparently.
 *
 * <p>The save/restore is stack-disciplined and therefore re-entrant: when a composite source script
 * extension calls back into the engine, which then dispatches to another extension, each nested call
 * swaps and restores the TCCL correctly.
 */
public final class TcclClassLoaderDecorator {

	private TcclClassLoaderDecorator() {}

	/**
	 * Wraps {@code delegate} in a dynamic proxy that sets {@code loader} as the TCCL around every
	 * interface method invocation.
	 *
	 * @param <T>      the SPI interface type.
	 * @param spi      the SPI interface class; must be an interface.
	 * @param delegate the extension instance to wrap.
	 * @param loader   the extension's class loader to install as the TCCL.
	 * @return a proxy implementing {@code spi} that delegates to {@code delegate} under the correct
	 *         TCCL.
	 */
	public static <T> T wrap(final Class<T> spi, final T delegate, final ClassLoader loader) {
		final Object proxy = Proxy.newProxyInstance(
			spi.getClassLoader(),
			new Class<?>[] { spi },
			new TcclInvocationHandler(delegate, loader)
		);
		return spi.cast(proxy);
	}

	/**
	 * Runs {@code action} with {@code loader} installed as the TCCL, restoring the previous TCCL
	 * afterwards. Used to construct extension instances (whose constructors/static initializers may
	 * perform TCCL-sensitive lookups) under the correct loader.
	 *
	 * @param <R>    the result type.
	 * @param loader the class loader to install as the TCCL.
	 * @param action the action to run.
	 * @return the action's result.
	 * @throws Exception whatever {@code action} throws.
	 */
	public static <R> R call(final ClassLoader loader, final Callable<R> action) throws Exception {
		final Thread thread = Thread.currentThread();
		final ClassLoader previous = thread.getContextClassLoader();
		thread.setContextClassLoader(loader);
		try {
			return action.call();
		} finally {
			thread.setContextClassLoader(previous);
		}
	}

	/**
	 * Invocation handler that swaps the TCCL around each call and unwraps reflective exceptions so
	 * the extension's own checked exceptions propagate unchanged.
	 */
	private static final class TcclInvocationHandler implements InvocationHandler {

		private final Object delegate;
		private final ClassLoader loader;

		private TcclInvocationHandler(final Object delegate, final ClassLoader loader) {
			this.delegate = delegate;
			this.loader = loader;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			final Thread thread = Thread.currentThread();
			final ClassLoader previous = thread.getContextClassLoader();
			thread.setContextClassLoader(loader);
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			} finally {
				thread.setContextClassLoader(previous);
			}
		}
	}
}
