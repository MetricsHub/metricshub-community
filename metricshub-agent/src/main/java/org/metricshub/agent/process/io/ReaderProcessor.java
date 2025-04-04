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

import java.io.Reader;
import lombok.NonNull;

/**
 * This thread reads the process output using a 512 characters buffer, it blocks until some input is
 * available, an I/O error occurs, or the end of the stream is reached. <br>
 * The {@link StreamProcessor} is called for each available block.
 */
public class ReaderProcessor extends AbstractReaderProcessor {

	/**
	 * Creates a new {@code ReaderProcessor} with the specified {@link Reader} and {@link StreamProcessor}.
	 *
	 * @param reader          The {@code Reader} from which to read the process output.
	 * @param streamProcessor The {@code StreamProcessor} to process each block of data.
	 */
	public ReaderProcessor(@NonNull Reader reader, @NonNull StreamProcessor streamProcessor) {
		super(reader, streamProcessor);
	}

	private static final int CHAR_BUFFER_LENGTH = 512;

	@Override
	public void run() {
		try {
			int read;
			char[] buffer = new char[CHAR_BUFFER_LENGTH];
			// Try to read a block
			while ((read = reader.read(buffer)) != -1) {
				// Process the block
				streamProcessor.process(new String(buffer, 0, read));
			}
		} catch (Exception e) {
			// Error for any unknown error
		}
	}
}
