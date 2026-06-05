package org.metricshub.extension.jdbc.driver;

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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Resolves app-relative sub-paths the same way {@code ConfigHelper.getSubPath} does for the
 * agent: locate the source directory of an engine class (always shaded into the agent fat-jar in
 * production), take its parent, then walk up one level and append the requested sub-path.
 *
 * <p>The anchor (called {@code $APP_DIR} in user-facing path expressions) differs per OS but the
 * canonical sub-paths ({@code extensions/...}, {@code connectors/...}, {@code config/...}) always
 * land at the right place:
 *
 * <p>Production layout (Linux):
 * <pre>
 *   /opt/metricshub/lib/app/metricshub-agent.jar  &larr; findSourceDirectory(TelemetryManager.class)
 *   /opt/metricshub/lib/app/                      &larr; parent
 *   /opt/metricshub/lib/                          &larr; $APP_DIR
 *   /opt/metricshub/lib/extensions/jdbc/          &larr; resolveSubPath("extensions/jdbc")
 * </pre>
 *
 * <p>Production layout (Windows):
 * <pre>
 *   C:\Program Files\MetricsHub\app\metricshub-agent.jar
 *   C:\Program Files\MetricsHub\app\             &larr; parent
 *   C:\Program Files\MetricsHub\                 &larr; $APP_DIR
 *   C:\Program Files\MetricsHub\extensions\jdbc\ &larr; resolveSubPath("extensions/jdbc")
 * </pre>
 *
 * <p>Development layout (Maven module run):
 * <pre>
 *   .../metricshub-engine/target/classes/         &larr; findSourceDirectory(TelemetryManager.class)
 *   .../metricshub-engine/target/                 &larr; parent
 *   .../metricshub-engine/                        &larr; $APP_DIR
 *   .../metricshub-engine/extensions/jdbc/        &larr; resolveSubPath("extensions/jdbc")
 * </pre>
 *
 * <p>The pure helper {@link #computeSubPath(Path, String)} contains the path math so it can be
 * tested without depending on where JUnit happens to load classes from.
 */
@Slf4j
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class JdbcAppDir {

	/**
	 * Resolves {@code subPath} relative to the MetricsHub application directory ({@code $APP_DIR}).
	 * See the class-level Javadoc for the layout assumptions.
	 *
	 * @param subPath path fragment to append (e.g. {@code "extensions/jdbc"}); must not be
	 *                {@code null} or blank.
	 * @return the resolved absolute {@link Path}; never {@code null}. The returned path is
	 *         normalized but its existence on disk is not checked.
	 */
	public static Path resolveSubPath(final String subPath) {
		if (subPath == null || subPath.isBlank()) {
			throw new IllegalArgumentException("subPath must not be blank");
		}
		final Path codeSource = locateEngineCodeSource();
		if (codeSource == null) {
			// Last-resort fallback: anchor at the working directory.
			final Path fallback = Paths.get(subPath).toAbsolutePath().normalize();
			log.debug(
				"Could not locate the engine code source; falling back to working directory for sub-path {} -> {}.",
				subPath,
				fallback
			);
			return fallback;
		}
		return computeSubPath(codeSource, subPath);
	}

	/**
	 * Pure path math extracted for unit testing. Mirrors {@code ConfigHelper.getSubPath}: take the
	 * code source path, use its parent (or the path itself if it has no parent), then resolve
	 * {@code "../" + subPath} and normalize.
	 *
	 * @param codeSource the file or directory the engine class was loaded from.
	 * @param subPath    path fragment to append.
	 * @return the resolved absolute {@link Path}; never {@code null}.
	 */
	static Path computeSubPath(final Path codeSource, final String subPath) {
		Path parentLibPath = codeSource.toAbsolutePath().getParent();
		if (parentLibPath == null) {
			parentLibPath = codeSource.toAbsolutePath();
		}
		return parentLibPath.resolve("../" + subPath).normalize();
	}

	/**
	 * Returns the on-disk path the engine's {@link TelemetryManager} class was loaded from, or
	 * {@code null} when it cannot be determined (e.g. unusual classloader).
	 *
	 * @return the engine code source as a {@link Path}, or {@code null}.
	 */
	private static Path locateEngineCodeSource() {
		try {
			final File source = ResourceHelper.findSourceDirectory(TelemetryManager.class);
			if (source == null) {
				return null;
			}
			return source.toPath();
		} catch (Exception e) {
			log.debug("Failed to locate engine code source: {}", e.getMessage());
			return null;
		}
	}
}
