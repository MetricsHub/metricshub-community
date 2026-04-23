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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
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
public class WmiRecorder {

	static final String WMI_SUBDIR = "wmi";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, WmiRecorder> RECORDERS = new ConcurrentHashMap<>();

	private final Path wmiDir;
	private final ObjectMapper yamlMapper;

	WmiRecorder(final String recordOutputDirectory) {
		this.wmiDir = Path.of(recordOutputDirectory, WMI_SUBDIR);
		this.yamlMapper = JsonHelper.buildYamlMapper();
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
	 * Records a WMI response.
	 *
	 * @param wql WQL query
	 * @param namespace WMI namespace
	 * @param responseTable response table values
	 */
	public synchronized void record(final String wql, final String namespace, final List<List<String>> responseTable) {
		try {
			Files.createDirectories(wmiDir);

			final Path indexFile = wmiDir.resolve(IMAGE_YAML);
			final List<Map<String, Object>> entries = loadExistingEntries(indexFile);

			final String responseFileName = UUID.randomUUID() + ".csv";
			Files.writeString(
				wmiDir.resolve(responseFileName),
				SourceTable.tableToCsv(responseTable, MetricsHubConstants.TABLE_SEP, true),
				StandardCharsets.UTF_8
			);

			final Map<String, Object> request = new LinkedHashMap<>();
			request.put("wql", wql);
			request.put("namespace", namespace);

			final Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("request", request);
			entry.put("response", responseFileName);
			entries.add(entry);

			final Map<String, Object> image = new LinkedHashMap<>();
			image.put("image", entries);
			yamlMapper.writeValue(indexFile.toFile(), image);
		} catch (IOException e) {
			log.error("WMI recording - Failed to record query for namespace {}: {}", namespace, e.getMessage());
			log.debug("WMI recording - Error details:", e);
		}
	}

	List<Map<String, Object>> loadExistingEntries(final Path indexFile) throws IOException {
		if (Files.isRegularFile(indexFile)) {
			final TypeReference<Map<String, List<Map<String, Object>>>> typeRef = new TypeReference<>() {};
			final Map<String, List<Map<String, Object>>> existing = yamlMapper.readValue(indexFile.toFile(), typeRef);
			final List<Map<String, Object>> imageList = existing.get("image");
			if (imageList != null) {
				return new ArrayList<>(imageList);
			}
		}
		return new ArrayList<>();
	}
}
