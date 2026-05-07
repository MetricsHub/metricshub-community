package org.metricshub.extension.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JMX Extension
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
import org.metricshub.engine.extension.recorder.AbstractRecorder;

/**
 * Records JMX query exchanges to an emulation image ({@code image.yaml})
 * and response payload files under a {@code jmx/} subdirectory.
 *
 * <p>The image format is:
 * <pre>
 * image:
 * - request:
 *     objectName: com.example:type=Example
 *     attributes:
 *       - Attribute1
 *     keyProperties:
 *       - scope
 *   response: uuid-random.txt
 * </pre>
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class JmxRecorder extends AbstractRecorder<JmxRecorder.JmxRecordRequest> {

	static final String JMX_SUBDIR = "jmx";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, JmxRecorder> RECORDERS = new ConcurrentHashMap<>();

	record JmxRecordRequest(String objectName, List<String> attributes, List<String> keyProperties, String csvResponse) {}

	JmxRecorder(final String recordOutputDirectory) {
		super(recordOutputDirectory, JMX_SUBDIR);
	}

	/**
	 * Returns a shared recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 * @return shared recorder instance
	 */
	public static JmxRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, JmxRecorder::new);
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
		final JmxRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records a JMX response.
	 *
	 * @param objectName    the MBean ObjectName pattern
	 * @param attributes    the list of attributes fetched
	 * @param keyProperties the list of key properties included
	 * @param csvResponse   the response already serialized as CSV
	 */
	public void record(
		final String objectName,
		final List<String> attributes,
		final List<String> keyProperties,
		final String csvResponse
	) {
		recordInternal(new JmxRecordRequest(objectName, attributes, keyProperties, csvResponse));
	}

	/**
	 * Flushes buffered entries to {@code image.yaml}.
	 */
	@Override
	protected String writeResponsePayload(final JmxRecordRequest request, final Path outputDir) throws IOException {
		final String responseFileName = UUID.randomUUID() + ".txt";
		Files.writeString(
			outputDir.resolve(responseFileName),
			request.csvResponse() != null ? request.csvResponse() : "",
			StandardCharsets.UTF_8
		);
		return responseFileName;
	}

	/**
	 * Returns the in-memory recording entries, loading them from disk on first access.
	 *
	 * @return mutable list of recording entries
	 * @throws IOException if the index file cannot be read
	 */
	@Override
	protected Map<String, Object> buildEntry(final JmxRecordRequest request, final String responseFileName) {
		final Map<String, Object> requestMap = new LinkedHashMap<>();
		requestMap.put("objectName", request.objectName());
		requestMap.put("attributes", request.attributes() != null ? request.attributes() : List.of());
		requestMap.put("keyProperties", request.keyProperties() != null ? request.keyProperties() : List.of());

		final Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("request", requestMap);
		entry.put("response", responseFileName);
		return entry;
	}

	@Override
	protected void logRecordFailure(final JmxRecordRequest request, final IOException exception) {
		log.error("JMX recording - Failed to record JMX query: {}", exception.getMessage());
		log.debug("JMX recording - Error details:", exception);
	}

	@Override
	protected void logFlushFailure(final IOException exception) {
		log.error("JMX recording - Failed to flush image file: {}", exception.getMessage());
		log.debug("JMX recording - Flush error details:", exception);
	}
}
