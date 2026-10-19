package org.metricshub.opamp.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
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

import java.util.ArrayList;
import java.util.List;

/**
 * Says what went wrong with a connection to Central, for a log line an operator can act on.
 */
public final class Failures {

	/** How far down the causes to look, against a chain that loops. */
	private static final int MOST_CAUSES = 8;

	private Failures() {}

	/**
	 * The JDK's HttpClient fails a refused or an unresolvable connection with a {@code ConnectException}
	 * that has no message, caused by a {@code ClosedChannelException} or an {@code UnresolvedAddressException}
	 * that has none either: logged by its message, such a failure reads "null". So the first message along
	 * the causes is used, and until one is found, the names of the exception types, which say what happened.
	 * A wrapper whose message only names its cause, such as a {@code CompletionException}, is read through.
	 *
	 * @param error what was thrown, or {@code null}
	 * @return its description, never {@code null}: for instance {@code ConnectException: ClosedChannelException}
	 */
	public static String describe(final Throwable error) {
		if (error == null) {
			return "unknown error";
		}
		final List<String> said = new ArrayList<>();
		Throwable current = error;
		for (int depth = 0; current != null && depth < MOST_CAUSES; depth++) {
			final String message = current.getMessage();
			final Throwable cause = current.getCause();
			if (message != null && !message.isBlank() && (cause == null || !message.equals(cause.toString()))) {
				said.add(message);
				break;
			}
			final String type = current.getClass().getSimpleName();
			// The JDK wraps a ConnectException in another: one name is enough
			if (said.isEmpty() || !said.get(said.size() - 1).equals(type)) {
				said.add(type);
			}
			current = cause;
		}
		return String.join(": ", said);
	}
}
