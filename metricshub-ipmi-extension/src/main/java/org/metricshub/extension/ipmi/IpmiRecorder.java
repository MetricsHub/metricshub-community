package org.metricshub.extension.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Ipmi Extension
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.extension.recorder.AbstractRecorder;

/**
 * Records IPMI over-LAN exchanges to an emulation image ({@code image.yaml})
 * and response payload files under an {@code ipmi/} subdirectory.
 *
 * <p>The image format is:
 * <pre>
 * image:
 * - request: IpmiDetection
 *   response: uuid-random.txt
 * - request: GetSensors
 *   response: uuid-random.txt
 * </pre>
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class IpmiRecorder extends AbstractRecorder<IpmiRecorder.IpmiRecordRequest> {

	/**
	 * Request type identifier for IPMI chassis status detection.
	 */
	public static final String IPMI_DETECTION_REQUEST = "IpmiDetection";

	/**
	 * Request type identifier for IPMI FRUs and Sensors retrieval.
	 */
	public static final String GET_SENSORS_REQUEST = "GetSensors";

	static final String IPMI_SUBDIR = "ipmi";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, IpmiRecorder> RECORDERS = new ConcurrentHashMap<>();

	record IpmiRecordRequest(String requestType, String response) {}

	IpmiRecorder(final String recordOutputDirectory) {
		super(recordOutputDirectory, IPMI_SUBDIR);
	}

	/**
	 * Returns a shared recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 * @return shared recorder instance
	 */
	public static IpmiRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, IpmiRecorder::new);
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
		final IpmiRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records an IPMI response.
	 *
	 * @param requestType the request type identifier (e.g., {@code "IpmiDetection"} or {@code "GetSensors"})
	 * @param response    the raw response string
	 */
	public void record(final String requestType, final String response) {
		recordInternal(new IpmiRecordRequest(requestType, response));
	}

	/**
	 * Flushes buffered entries to {@code image.yaml}.
	 */
	@Override
	protected String writeResponsePayload(final IpmiRecordRequest request, final Path outputDir) throws IOException {
		final String responseFileName = UUID.randomUUID() + ".txt";
		Files.writeString(
			outputDir.resolve(responseFileName),
			request.response() != null ? request.response() : "",
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
	protected Map<String, Object> buildEntry(final IpmiRecordRequest request, final String responseFileName) {
		final Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("request", request.requestType());
		entry.put("response", responseFileName);
		return entry;
	}

	@Override
	protected void logRecordFailure(final IpmiRecordRequest request, final IOException exception) {
		log.error("IPMI recording - Failed to record {} response: {}", request.requestType(), exception.getMessage());
		log.debug("IPMI recording - Error details:", exception);
	}

	@Override
	protected void logFlushFailure(final IOException exception) {
		log.error("IPMI recording - Failed to flush image file: {}", exception.getMessage());
		log.debug("IPMI recording - Flush error details:", exception);
	}
}
