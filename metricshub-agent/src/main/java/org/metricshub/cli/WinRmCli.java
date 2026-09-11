package org.metricshub.cli;

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

/**
 * Command-line interface for WinRM, backed directly by the winrm-java library's own CLI
 * ({@link org.metricshub.winrm.cli.WinRmCli}): WQL queries, remote command execution and an
 * interactive shell.
 */
public class WinRmCli {

	/**
	 * Entry point for the WinRM CLI application. Delegates straight to the winrm-java library's
	 * CLI, which parses the arguments, runs the requested operation and exits the JVM with the
	 * resulting exit code.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(final String[] args) {
		org.metricshub.winrm.cli.WinRmCli.main(args);
	}
}
