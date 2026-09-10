package org.metricshub.programmable.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Programmable Configuration Extension
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

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Velocity tool exposed to templates as {@code $schedule}.
 * <p>
 * It lets a template declare how often it must be re-evaluated as a whole:
 * </p>
 *
 * <pre>{@code
 * $schedule.cron('0/30 * * * * ?')
 * }</pre>
 *
 * <p>
 * The declaration applies to the <b>entire template</b>: when the cron fires, the template is
 * rendered again from scratch, so every {@code $http}, {@code $sql} and {@code $file} call in it is
 * re-run and the configuration is updated from the fresh result.
 * </p>
 * <p>
 * A template declares at most one schedule. When {@code cron} is called more than once, the
 * <b>last</b> call wins and a warning is logged, since the earlier declarations have no effect.
 * </p>
 * <p>
 * The directive itself renders to the empty string &mdash; it declares behavior, not text.
 * </p>
 */
@Slf4j
public class ScheduleTool {

	/** The cron declared by the template during the current render pass, or {@code null} if none. */
	private String cron;

	/**
	 * Template entry point: {@code $schedule.cron('<cron>')}.
	 * <p>
	 * Declares that the template must be re-rendered on the given cron. Blank expressions are
	 * ignored, and a second call replaces the first.
	 * </p>
	 *
	 * @param cronExpression the cron expression governing the re-evaluation of this template
	 * @return an empty string (the directive prints nothing)
	 */
	public String cron(final String cronExpression) {
		if (cronExpression == null || cronExpression.isBlank()) {
			log.warn("Ignoring a $schedule.cron(...) declaration with a blank cron expression.");
			return "";
		}

		final String newCron = cronExpression.trim();
		if (cron != null) {
			log.warn(
				"A template declares several $schedule.cron(...) expressions; only the last one is applied. " +
					"Replacing '{}' with '{}'.",
				cron,
				newCron
			);
		}
		cron = newCron;
		return "";
	}

	/**
	 * Returns the cron declared by the template during the last render pass.
	 *
	 * @return the declared cron expression, or empty when the template declares no schedule
	 */
	public Optional<String> getCron() {
		return Optional.ofNullable(cron);
	}

	/**
	 * Clears the declared cron. Called before every render pass so the schedule always reflects the
	 * template as it is now: re-running the declarations is what re-establishes it, and a directive
	 * removed from the template correctly leaves no schedule behind.
	 * <p>
	 * What this tool collects during a render is only published once that render completes (see
	 * {@link VelocityConfigurationLoader#getCron()}), so clearing it here does not expose a template
	 * whose render failed as one declaring no schedule.
	 * </p>
	 */
	void reset() {
		cron = null;
	}
}
