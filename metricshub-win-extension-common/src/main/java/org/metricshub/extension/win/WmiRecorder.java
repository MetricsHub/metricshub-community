package org.metricshub.extension.win;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2026 MetricsHub
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
 * Records WMI query/namespace exchanges to an emulation image ({@code image.yaml})
 * and response payload files under a {@code wmi/} subdirectory.
 *
 * <p>The image format is:
 * <pre>
 * image:
 * - request:
 *     wql: SELECT ...
 *     namespace: root\cimv2
 *   response: uuid-random.csv
 * </pre>
 */
@Slf4j
public class WmiRecorder extends AbstractRecorder<WmiRecorder.WmiRecordRequest> {

	static final String WMI_SUBDIR = "wmi";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, WmiRecorder> RECORDERS = new ConcurrentHashMap<>();

	/**
	 * Protocol-specific payload used to record one WMI query response.
	 *
	 * @param wql WQL query
	 * @param namespace WMI namespace
	 * @param responseTable response table values
	 */
	record WmiRecordRequest(String wql, String namespace, List<List<String>> responseTable) {}

	/**
	 * Creates a recorder instance for the provided output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 */
	WmiRecorder(final String recordOutputDirectory) {
		super(recordOutputDirectory, WMI_SUBDIR);
	}

	/**
	 * Returns a shared recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 * @return shared recorder instance
	 */
	public static WmiRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, WmiRecorder::new);
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
		final WmiRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records a WMI response.
	 *
	 * @param wql WQL query
	 * @param namespace WMI namespace
	 * @param responseTable response table values
	 */
	public void record(final String wql, final String namespace, final List<List<String>> responseTable) {
		recordInternal(new WmiRecordRequest(wql, namespace, responseTable));
	}

	/**
	 * Writes the response table as CSV and returns the generated response file name.
	 *
	 * @param request WMI record payload
	 * @param outputDir protocol output directory
	 * @return generated response file name
	 * @throws IOException if writing response payload fails
	 */
	@Override
	protected String writeResponsePayload(final WmiRecordRequest request, final Path outputDir) throws IOException {
		final String responseFileName = UUID.randomUUID() + ".csv";
		Files.writeString(
			outputDir.resolve(responseFileName),
			SourceTable.tableToCsv(request.responseTable(), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);
		return responseFileName;
	}

	/**
	 * Builds one YAML image entry for the recorded WMI request and response file.
	 *
	 * @param request WMI record payload
	 * @param responseFileName generated response file name
	 * @return YAML image entry
	 */
	@Override
	protected Map<String, Object> buildEntry(final WmiRecordRequest request, final String responseFileName) {
		final Map<String, Object> requestMap = new LinkedHashMap<>();
		requestMap.put("wql", request.wql());
		requestMap.put("namespace", request.namespace());

		final Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("request", requestMap);
		entry.put("response", responseFileName);
		return entry;
	}

	/**
	 * Logs failures raised while recording WMI responses.
	 *
	 * @param request WMI record payload
	 * @param exception thrown exception
	 */
	@Override
	protected void logRecordFailure(final WmiRecordRequest request, final IOException exception) {
		log.error(
			"WMI recording - Failed to record query for namespace {}: {}",
			request.namespace(),
			exception.getMessage()
		);
		log.debug("WMI recording - Error details:", exception);
	}

	/**
	 * Logs failures raised while flushing the buffered image.
	 *
	 * @param exception thrown exception
	 */
	@Override
	protected void logFlushFailure(final IOException exception) {
		log.error("WMI recording - Failed to flush image file: {}", exception.getMessage());
		log.debug("WMI recording - Flush error details:", exception);
	}
}
