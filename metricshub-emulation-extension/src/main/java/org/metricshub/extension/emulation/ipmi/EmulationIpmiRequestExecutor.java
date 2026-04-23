package org.metricshub.extension.emulation.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationPathHelper;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.IpmiEmulationConfig;
import org.metricshub.extension.ipmi.IpmiConfiguration;
import org.metricshub.extension.ipmi.IpmiRecorder;
import org.metricshub.extension.ipmi.IpmiRequestExecutor;

/**
 * IPMI request executor that replays responses from recorded emulation files.
 */
@Slf4j
@RequiredArgsConstructor
public class EmulationIpmiRequestExecutor extends IpmiRequestExecutor {

	private static final String IPMI_EMULATION_YAML = "image.yaml";

	private final EmulationRoundRobinManager roundRobinManager;
	private final EmulationImageCacheManager imageCacheManager;

	/**
	 * Replays an IPMI detection result from emulation files.
	 *
	 * @param hostname          the hostname
	 * @param ipmiConfiguration the IPMI configuration (expected to be {@link IpmiEmulationConfig})
	 * @param emulationConfiguration the emulation configuration containing the IPMI directory
	 * @return the recorded detection result, or {@code null} if not found
	 */
	public String executeIpmiDetection(
		final String hostname,
		final IpmiConfiguration ipmiConfiguration,
		final EmulationConfiguration emulationConfiguration
	) {
		return replayFromEmulationFiles(hostname, emulationConfiguration, IpmiRecorder.IPMI_DETECTION_REQUEST);
	}

	/**
	 * Replays an IPMI GetSensors result from emulation files.
	 *
	 * @param hostname          the hostname
	 * @param ipmiConfiguration the IPMI configuration (expected to be {@link IpmiEmulationConfig})
	 * @param emulationConfiguration the emulation configuration containing the IPMI directory
	 * @return the recorded sensor data, or {@code null} if not found
	 */
	public String executeIpmiGetSensors(
		final String hostname,
		final IpmiConfiguration ipmiConfiguration,
		final EmulationConfiguration emulationConfiguration
	) {
		return replayFromEmulationFiles(hostname, emulationConfiguration, IpmiRecorder.GET_SENSORS_REQUEST);
	}

	/**
	 * Replays a response from IPMI emulation files.
	 *
	 * @param hostname               the hostname
	 * @param emulationConfiguration the emulation configuration
	 * @param requestType            the request type identifier
	 * @return the response content, or {@code null} if not found
	 */
	private String replayFromEmulationFiles(
		final String hostname,
		final EmulationConfiguration emulationConfiguration,
		final String requestType
	) {
		if (emulationConfiguration == null || emulationConfiguration.getIpmi() == null) {
			log.warn("Hostname {} - IPMI emulation configuration is not set.", hostname);
			return null;
		}

		final String emulationInputDirectory = emulationConfiguration.getIpmi().getDirectory();
		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - IPMI emulation input directory is not configured.", hostname);
			return null;
		}

		final Path ipmiDir = Path.of(emulationInputDirectory);
		final Path indexFile = ipmiDir.resolve(IPMI_EMULATION_YAML);

		if (!Files.isRegularFile(indexFile)) {
			log.warn("Hostname {} - IPMI emulation index file not found: {}", hostname, indexFile);
			return null;
		}

		final IpmiEmulationImage emulationImage;
		try {
			emulationImage = imageCacheManager.getImage(indexFile, IpmiEmulationImage.class);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to parse IPMI emulation index file {}. Error: {}",
				hostname,
				indexFile,
				e.getMessage()
			);
			log.debug("Hostname {} - IPMI emulation index parse error:", hostname, e);
			return null;
		}

		final List<IpmiEmulationEntry> entries = emulationImage != null
			? emulationImage.getImage()
			: Collections.emptyList();

		final List<IpmiEmulationEntry> matchingEntries = findMatchingEntries(entries, requestType);
		if (matchingEntries.isEmpty()) {
			log.warn("Hostname {} - No matching IPMI emulation entry found for request '{}'.", hostname, requestType);
			return null;
		}

		final int index = roundRobinManager.nextIndex(
			indexFile.toAbsolutePath().toString(),
			requestType,
			matchingEntries.size()
		);

		final String responseFileName = matchingEntries.get(index).getResponse();
		if (responseFileName == null || responseFileName.isBlank()) {
			log.warn("Hostname {} - Matched IPMI emulation entry has no response file.", hostname);
			return null;
		}

		final Path responseFile = EmulationPathHelper.resolveSecurely(ipmiDir, responseFileName);
		if (responseFile == null) {
			return null;
		}
		try {
			return Files.readString(responseFile, StandardCharsets.UTF_8);
		} catch (Exception e) {
			log.error(
				"Hostname {} - Failed to read IPMI emulation response file {}. Error: {}",
				hostname,
				responseFile,
				e.getMessage()
			);
			log.debug("Hostname {} - IPMI emulation response read error:", hostname, e);
			return null;
		}
	}

	List<IpmiEmulationEntry> findMatchingEntries(final List<IpmiEmulationEntry> entries, final String requestType) {
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}

		return entries
			.stream()
			.filter(entry -> entry != null && requestType.equals(entry.getRequest()))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
