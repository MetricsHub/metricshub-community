package org.metricshub.web.config;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import jakarta.annotation.PostConstruct;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to install the SLF4J bridge for Java Util Logging (JUL).
 * This class removes the default JUL ConsoleHandlers to prevent double logging
 * and installs the SLF4JBridgeHandler to redirect JUL logs to SLF4J.
 */
@Configuration
public class LoggingBridgeConfiguration {

	@PostConstruct
	public static void installJavaUtilLoggingBridge() {
		// Remove JUL ConsoleHandlers to avoid double logging
		java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
		for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
			if (handler instanceof java.util.logging.ConsoleHandler) {
				rootLogger.removeHandler(handler);
			}
		}
		SLF4JBridgeHandler.install();
	}
}
