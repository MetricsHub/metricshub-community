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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.metricshub.engine.common.helpers.JsonHelper;

/**
 * Caches parsed emulation images and reloads them only when the backing file changes.
 */
public class EmulationImageCacheManager {

	private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

	/**
	 * Returns the parsed image for the provided file path and image type.
	 *
	 * <p>The image is parsed once and reused while the file modification time and
	 * file size remain unchanged.
	 *
	 * @param indexFile The image.yaml file path.
	 * @param imageClass The expected image class.
	 * @param <T> Parsed image type.
	 * @return The parsed image instance.
	 * @throws IOException If the file cannot be read or parsed.
	 */
	public <T> T getImage(final Path indexFile, final Class<T> imageClass) throws IOException {
		final FileSignature signature = readFileSignature(indexFile);
		final String cacheKey = buildCacheKey(indexFile, imageClass);
		final CacheEntry cacheEntry = cache.get(cacheKey);
		if (cacheEntry != null && cacheEntry.matches(signature)) {
			return imageClass.cast(cacheEntry.value());
		}

		final T parsedImage = JsonHelper.buildYamlMapper().readValue(indexFile.toFile(), imageClass);
		cache.put(cacheKey, new CacheEntry(parsedImage, signature.lastModifiedMillis(), signature.size()));
		return parsedImage;
	}

	/**
	 * Builds the cache key used to isolate entries by image file and image type.
	 *
	 * @param indexFile The image.yaml file path.
	 * @param imageClass The image class.
	 * @return A unique cache key.
	 */
	private String buildCacheKey(final Path indexFile, final Class<?> imageClass) {
		return indexFile.toAbsolutePath().normalize() + "|" + imageClass.getName();
	}

	/**
	 * Reads the freshness signature of a file.
	 *
	 * @param indexFile The image.yaml file path.
	 * @return The file signature.
	 * @throws IOException If file metadata cannot be read.
	 */
	private FileSignature readFileSignature(final Path indexFile) throws IOException {
		return new FileSignature(Files.getLastModifiedTime(indexFile).toMillis(), Files.size(indexFile));
	}

	/**
	 * Immutable file signature used to detect file changes.
	 */
	private record FileSignature(long lastModifiedMillis, long size) {}

	/**
	 * Cached image value and the file signature used to produce it.
	 */
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	private static class CacheEntry {

		private final Object value;
		private final long lastModifiedMillis;
		private final long size;

		/**
		 * Returns the cached image object.
		 *
		 * @return cached image
		 */
		private Object value() {
			return value;
		}

		/**
		 * Indicates whether this cache entry still matches the provided file signature.
		 *
		 * @param signature current file signature
		 * @return {@code true} when this entry is still fresh
		 */
		private boolean matches(final FileSignature signature) {
			return lastModifiedMillis == signature.lastModifiedMillis() && size == signature.size();
		}
	}
}
