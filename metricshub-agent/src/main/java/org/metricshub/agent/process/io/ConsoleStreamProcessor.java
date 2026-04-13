package org.metricshub.agent.process.io;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.io.PrintStream;

/**
 * Implementation of {@link StreamProcessor} that prints the received block to either
 * standard output or standard error.
 */
public class ConsoleStreamProcessor implements StreamProcessor {

	private PrintStream printStream;

	/**
	 * Constructs a {@code ConsoleStreamProcessor} instance.
	 *
	 * @param isError {@code true} if the output is an error, {@code false} for standard output.
	 */
	public ConsoleStreamProcessor(final boolean isError) {
		// Check if we should print to STDOUT or STDERR
		if (isError) {
			printStream = System.err; // NOSONAR
		} else {
			printStream = System.out; // NOSONAR
		}
	}

	@Override
	public void process(final String block) {
		// Print the block
		printStream.println(block);
		printStream.flush();
	}
}
