package org.metricshub.extension.jdbc;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.extension.recorder.AbstractRecorder;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Records JDBC SQL query exchanges to an emulation image ({@code image.yaml})
 * and response payload files under a {@code jdbc/} subdirectory.
 *
 * <p>The image format is:
 * <pre>
 * image:
 * - query: SELECT ...
 *   response: uuid-random.csv
 * </pre>
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class JdbcRecorder extends AbstractRecorder<JdbcRecorder.JdbcRecordRequest> {

	static final String JDBC_SUBDIR = "jdbc";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, JdbcRecorder> RECORDERS = new ConcurrentHashMap<>();

	record JdbcRecordRequest(String sqlQuery, List<List<String>> responseTable) {}

	JdbcRecorder(final String recordOutputDirectory) {
		super(recordOutputDirectory, JDBC_SUBDIR);
	}

	/**
	 * Returns a shared recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 * @return shared recorder instance
	 */
	public static JdbcRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, JdbcRecorder::new);
	}

	/**
	 * Clears all cached recorder instances.
	 * Intended for tests.
	 */
	static void clearInstances() {
		RECORDERS.clear();
	}

	/**
	 * Removes the cached recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 */
	public static void removeInstance(final String recordOutputDirectory) {
		RECORDERS.remove(recordOutputDirectory);
	}

	/**
	 * Flushes the recorder for the specified output directory and removes it from cache.
	 *
	 * @param recordOutputDirectory root recording output directory
	 */
	public static void flushAndRemoveInstance(final String recordOutputDirectory) {
		final JdbcRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records a JDBC SQL response.
	 *
	 * @param sqlQuery      SQL query string
	 * @param responseTable response table values
	 */
	public void record(final String sqlQuery, final List<List<String>> responseTable) {
		recordInternal(new JdbcRecordRequest(sqlQuery, responseTable));
	}

	/**
	 * Writes the response payload (CSV-encoded result table) to a uniquely-named file under
	 * {@code outputDir} and returns the file name to be referenced from {@code image.yaml}.
	 *
	 * @param request   the recording request containing the SQL query and response table.
	 * @param outputDir the directory where the response file must be written.
	 * @return the name (relative to {@code outputDir}) of the file holding the response payload.
	 * @throws IOException if the response file cannot be written.
	 */
	@Override
	protected String writeResponsePayload(final JdbcRecordRequest request, final Path outputDir) throws IOException {
		final String responseFileName = UUID.randomUUID() + ".csv";
		Files.writeString(
			outputDir.resolve(responseFileName),
			SourceTable.tableToCsv(request.responseTable(), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);
		return responseFileName;
	}

	/**
	 * Builds the {@code image.yaml} entry that maps the recorded SQL query to the file holding
	 * its response payload.
	 *
	 * @param request          the recording request containing the SQL query.
	 * @param responseFileName the name of the response payload file previously written by
	 *                         {@link #writeResponsePayload(JdbcRecordRequest, Path)}.
	 * @return an ordered map with {@code query} and {@code response} keys.
	 */
	@Override
	protected Map<String, Object> buildEntry(final JdbcRecordRequest request, final String responseFileName) {
		final Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("query", request.sqlQuery());
		entry.put("response", responseFileName);
		return entry;
	}

	@Override
	protected void logRecordFailure(final JdbcRecordRequest request, final IOException exception) {
		log.error("JDBC recording - Failed to record SQL query: {}", exception.getMessage());
		log.debug("JDBC recording - Error details:", exception);
	}

	@Override
	protected void logFlushFailure(final IOException exception) {
		log.error("JDBC recording - Failed to flush image file: {}", exception.getMessage());
		log.debug("JDBC recording - Flush error details:", exception);
	}
}
