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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.emulation.AbstractEmulationExecutor;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.ipmi.IpmiConfiguration;
import org.metricshub.extension.ipmi.IpmiRecorder;
import org.metricshub.extension.ipmi.IpmiRequestExecutor;

/**
 * IPMI request executor that replays responses from recorded emulation files.
 */
@Slf4j
public class EmulationIpmiRequestExecutor extends IpmiRequestExecutor {

	record IpmiContext(String requestType) {}

	private final AbstractEmulationExecutor executorHelper;

	/**
	 * Constructs an EmulationIpmiRequestExecutor with the given round-robin manager and image cache manager.
	 *
	 * @param roundRobinManager Manager for round-robin selection of emulation images
	 * @param imageCacheManager Cache manager for emulation images to optimize performance
	 */
	public EmulationIpmiRequestExecutor(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		executorHelper = new AbstractEmulationExecutor(roundRobinManager, imageCacheManager);
	}

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

		return executorHelper.replayFromImage(
			hostname,
			emulationInputDirectory,
			IpmiEmulationImage.class,
			new IpmiContext(requestType),
			new AbstractEmulationExecutor.ReplayOperations<IpmiEmulationImage, IpmiEmulationEntry, IpmiContext, String>() {
				@Override
				public String protocolName() {
					return "IPMI";
				}

				@Override
				public String describeRequest(final IpmiContext context) {
					return "request '" + context.requestType() + "'";
				}

				@Override
				public List<IpmiEmulationEntry> extractEntries(final IpmiEmulationImage image) {
					return image != null ? image.getImage() : Collections.emptyList();
				}

				@Override
				public List<IpmiEmulationEntry> findMatchingEntries(
					final List<IpmiEmulationEntry> entries,
					final IpmiContext context
				) {
					return EmulationIpmiRequestExecutor.this.findMatchingEntries(entries, context.requestType());
				}

				@Override
				public String buildRequestKey(final IpmiContext context) {
					return context.requestType();
				}

				@Override
				public String extractResponseFileName(final IpmiEmulationEntry entry) {
					return entry.getResponse();
				}

				@Override
				public String mapResponse(final String content) {
					return content;
				}

				@Override
				public String emptyResult() {
					return null;
				}
			}
		);
	}

	/**
	 * Finds matching IPMI emulation entries for the given request type.
	 *
	 * @param entries     the list of emulation entries to search
	 * @param requestType the request type to match
	 * @return a list of matching entries, or an empty list if none found
	 */
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
