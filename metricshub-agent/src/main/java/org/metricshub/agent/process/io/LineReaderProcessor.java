package org.metricshub.agent.process.io;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.io.BufferedReader;
import java.io.Reader;
import lombok.NonNull;

/**
 * This runnable reads the process output lines, it blocks until some input is
 * available, an I/O error occurs, or the end of the stream is reached. <br>
 * The {@link StreamProcessor} is called for each available line.
 */
public class LineReaderProcessor extends AbstractReaderProcessor {

	/**
	 * Constructs a LineReaderProcessor with the specified reader and stream processor.
	 *
	 * @param reader           The reader to read process output lines.
	 * @param streamProcessor  The {@link StreamProcessor} to be called for each available line.
	 */
	public LineReaderProcessor(@NonNull Reader reader, @NonNull StreamProcessor streamProcessor) {
		super(reader, streamProcessor);
	}

	@Override
	public void run() {
		try (BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				streamProcessor.process(line);
			}
		} catch (Exception e) {
			// Probably an IO error
		}
	}
}
