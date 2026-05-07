package org.metricshub.extension.emulation;

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
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Template base class for emulation executors that replay responses from {@code image.yaml}.
 *
 */
@Slf4j
public class AbstractEmulationExecutor {

	private static final String EMULATION_IMAGE_YAML = "image.yaml";

	private final EmulationRoundRobinManager roundRobinManager;
	private final EmulationImageCacheManager imageCacheManager;

	/**
	 * Constructs an AbstractEmulationExecutor with the given round-robin manager and image cache manager.
	 *
	 * @param roundRobinManager Manager for round-robin selection of emulation images
	 * @param imageCacheManager Cache manager for emulation images to optimize performance
	 */
	public AbstractEmulationExecutor(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		this.roundRobinManager = roundRobinManager;
		this.imageCacheManager = imageCacheManager;
	}

	/**
	 * Replays a response from an emulation directory using a common execution flow.
	 *
	 * @param <I> emulation image type
	 * @param <E> emulation entry type
	 * @param <C> request context type
	 * @param <R> replay result type
	 *
	 * @param hostname host name used for logs
	 * @param emulationInputDirectory protocol emulation input directory
	 * @param imageClass image class to parse
	 * @param context protocol request context
	 * @return replayed result, or protocol-specific empty result on failure
	 */
	public <I, E, C, R> R replayFromImage(
		final String hostname,
		final String emulationInputDirectory,
		final Class<I> imageClass,
		final C context,
		final ReplayOperations<I, E, C, R> operations
	) {
		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - {} emulation input directory is not configured.", hostname, operations.protocolName());
			return operations.emptyResult();
		}

		final Path protocolDir = Path.of(emulationInputDirectory);
		final Path indexFile = protocolDir.resolve(EMULATION_IMAGE_YAML);
		if (!Files.isRegularFile(indexFile)) {
			log.warn("Hostname {} - {} emulation index file not found: {}", hostname, operations.protocolName(), indexFile);
			return operations.emptyResult();
		}

		final I emulationImage;
		try {
			emulationImage = imageCacheManager.getImage(indexFile, imageClass);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to parse {} emulation index file {}. Error: {}",
				hostname,
				operations.protocolName(),
				indexFile,
				e.getMessage()
			);
			log.debug("Hostname {} - {} emulation index parse error:", hostname, operations.protocolName(), e);
			return operations.emptyResult();
		}

		final List<E> entries = operations.extractEntries(emulationImage);
		final List<E> matchingEntries = operations.findMatchingEntries(entries, context);
		if (matchingEntries.isEmpty()) {
			log.warn(
				"Hostname {} - No matching {} emulation entry found for {}.",
				hostname,
				operations.protocolName(),
				operations.describeRequest(context)
			);
			return operations.emptyResult();
		}

		final int index = roundRobinManager.nextIndex(
			indexFile.toAbsolutePath().toString(),
			operations.buildRequestKey(context),
			matchingEntries.size()
		);
		final String responseFileName = operations.extractResponseFileName(matchingEntries.get(index));
		if (responseFileName == null || responseFileName.isBlank()) {
			log.warn("Hostname {} - Matched {} emulation entry has no response file.", hostname, operations.protocolName());
			return operations.emptyResult();
		}

		final Path responseFile = EmulationPathHelper.resolveSecurely(protocolDir, responseFileName);
		if (responseFile == null) {
			return operations.emptyResult();
		}
		try {
			final String content = Files.readString(responseFile, StandardCharsets.UTF_8);
			return operations.mapResponse(content);
		} catch (Exception e) {
			log.error(
				"Hostname {} - Failed to read {} emulation response file {}. Error: {}",
				hostname,
				operations.protocolName(),
				responseFile,
				e.getMessage()
			);
			log.debug("Hostname {} - {} emulation response read error:", hostname, operations.protocolName(), e);
			return operations.emptyResult();
		}
	}

	/**
	 * Strategy contract used to adapt replay flow to a protocol-specific image model.
	 *
	 * @param <I> emulation image type
	 * @param <E> emulation entry type
	 * @param <C> request context type
	 * @param <R> replay result type
	 */
	public interface ReplayOperations<I, E, C, R> {
		/**
		 * Protocol name for logging purposes.
		 *
		 * @return protocol name
		 */
		String protocolName();

		/**
		 * Describes the request for logging purposes.
		 * @param context request context
		 * @return description of the request
		 */
		String describeRequest(C context);

		/**
		 * Extracts emulation entries from the parsed image.
		 *
		 * @param image parsed emulation image
		 * @return list of emulation entries, or empty list if image is null or has no entries
		 */
		List<E> extractEntries(I image);

		/**
		 * Finds matching emulation entries for the given request context.
		 *
		 * @param entries list of emulation entries to search
		 * @param context request context to match
		 * @return list of matching emulation entries, or empty list if no matches found
		 */
		List<E> findMatchingEntries(List<E> entries, C context);

		/**
		 * Builds a key for round-robin selection based on the request context.
		 *
		 * @param context request context
		 * @return key for round-robin selection among matching entries
		 */
		String buildRequestKey(C context);

		/**
		 * Extracts the response file name from a matched emulation entry.
		 *
		 * @param entry matched emulation entry
		 * @return response file name specified in the entry, or null/blank if not specified
		 */
		String extractResponseFileName(E entry);

		/**
		 * Maps the content of the response file to the expected replay result type.
		 *
		 * @param content raw content read from the response file
		 * @return mapped replay result
		 * @throws Exception if mapping fails due to invalid content format or other issues
		 */
		R mapResponse(String content) throws Exception;

		/**
		 * Provides an empty result for the protocol, used when emulation fails or no matching entry is found.
		 *
		 * @return protocol-specific empty result
		 */
		R emptyResult();
	}
}
