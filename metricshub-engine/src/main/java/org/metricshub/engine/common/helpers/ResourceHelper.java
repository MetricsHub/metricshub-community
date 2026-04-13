package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;

/**
 * Helper class for working with resources.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceHelper {

	/**
	 * Get resource file content as String for the given class.
	 *
	 * @param path  path of the file in the resources directory
	 * @param clazz class used to load the resource as stream
	 * @return Resource file as {@link String}
	 */
	public static String getResourceAsString(final String path, @NonNull final Class<?> clazz) {
		Assert.isTrue(path != null && !path.isEmpty(), "path cannot be null or empty");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(clazz.getResourceAsStream(path)))) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			Assert.state(
				false,
				() ->
					String.format(
						"Cannot load resource file '%s' for class '%s'. Message: %s",
						path,
						clazz.getName(),
						e.getMessage()
					)
			);
		}

		return "";
	}

	/**
	 * Retrieves the directory containing the given source class, whether it's
	 * located within a JAR file or a regular directory.
	 *
	 * @param sourceClass The sourceClass we wish to get its directory,
	 *                    this class could be located under a JAR or a regular folder such as
	 *                    <em>\target\classes</em>
	 * @return {@link File} instance representing the source directory.
	 * @throws IOException        If the {@link URLConnection} cannot be opened or an
	 *                            error occurs while trying to connect to the JAR file using the
	 *                            {@link JarURLConnection}
	 * @throws URISyntaxException If the {@link URLConnection} is not formatted strictly according
	 *                            to RFC2396 and cannot be converted to a URI.
	 */
	public static File findSourceDirectory(@NonNull final Class<?> sourceClass) throws IOException, URISyntaxException {
		ProtectionDomain domain = sourceClass.getProtectionDomain();
		CodeSource codeSource = domain != null ? domain.getCodeSource() : null;
		URL location = codeSource != null ? codeSource.getLocation() : null;
		File source = location != null ? findSourceDirectory(location) : null;
		if (source != null && source.exists()) {
			return source.getAbsoluteFile();
		}

		return null;
	}

	/**
	 * Find source directory for the given URL location
	 *
	 * @param location {@link URL} instance
	 * @return {@link File} instance representing the source directory.
	 * @throws IOException        If the {@link URLConnection} cannot be opened or an
	 *                            error occurs while trying to connect to the JAR file using the
	 *                            {@link JarURLConnection}
	 * @throws URISyntaxException If the {@link URLConnection} is not formatted strictly according
	 *                            to RFC2396 and cannot be converted to a URI.
	 */
	static File findSourceDirectory(@NonNull final URL location) throws IOException, URISyntaxException {
		URLConnection connection = location.openConnection();
		if (connection instanceof JarURLConnection jarUrlConnection) {
			return getRootJarFile(jarUrlConnection.getJarFile());
		}
		return new File(location.toURI());
	}

	/**
	 * Since jars can be embedded in other jars, this method will get the root jar
	 * file instance
	 *
	 * @param jarFile
	 * @return File instance
	 */
	static File getRootJarFile(@NonNull final JarFile jarFile) {
		String name = jarFile.getName();
		int separator = name.indexOf("!/");
		if (separator > 0) {
			name = name.substring(0, separator);
		}
		return new File(name);
	}

	/**
	 * Get the resource content from the classpath as a String.
	 *
	 * @param resourcePath the path to the resource in the classpath
	 * @return the content of the resource as a String
	 */
	public static final String getClassPathResourceContent(final String resourcePath) {
		final var resource = new ClassPathResource(resourcePath);
		try (
			var inputStream = resource.getInputStream();
			var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
		) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			throw new IllegalStateException(
				"Cannot read classpath resource file: " + resourcePath + ". Message: " + e.getMessage(),
				e
			);
		}
	}
}
