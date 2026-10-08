package org.metricshub.extension.win;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.WMI_DEFAULT_NAMESPACE;

import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;

/**
 * Interface defining the contract for executing Windows Management Instrumentation (WMI) requests
 * on a specified host.
 */
public interface IWinRequestExecutor {
	/**
	 * WMI invalid class error message.
	 */
	String WBEM_E_INVALID_CLASS = "WBEM_E_INVALID_CLASS";
	/**
	 * WMI invalid namespace error message.
	 */
	String WBEM_E_INVALID_NAMESPACE = "WBEM_E_INVALID_NAMESPACE";
	/**
	 * WMI error message when the WMI repository is corrupted.
	 */
	String WBEM_E_NOT_FOUND = "WBEM_E_NOT_FOUND";

	/**
	 * Execute a WMI query
	 *
	 * @param hostname              The hostname of the device where the WMI service is running (<code>null</code> for localhost)
	 * @param winConfiguration      Windows Protocol configuration (credentials, timeout). E.g. WMI or WinRm.
	 * @param query                 The WQL to execute.
	 * @param namespace             The WMI namespace where all the classes reside.
	 * @param recordOutputDirectory The directory for recording the query result, or {@code null} to skip recording.
	 * @return A list of rows, where each row is represented as a list of strings.
	 * @throws ClientException when anything goes wrong (details in cause).
	 */
	List<List<String>> executeWmi(
		String hostname,
		@NonNull IWinConfiguration winConfiguration,
		@NonNull String query,
		String namespace,
		String recordOutputDirectory
	) throws ClientException;

	/**
	 * Assess whether an exception (or any of its causes) is simply an error saying that the
	 * requested namespace of class doesn't exist, which is considered okay.
	 * <br>
	 *
	 * @param t Throwable to verify.
	 * @return whether specified exception is acceptable while performing namespace detection.
	 */
	boolean isAcceptableException(Throwable t);

	/**
	 * Perform a Windows remote command query, either WinRM or WMI.
	 * <br>
	 *
	 * @param hostname         The hostname of the device where the WMI or WinRm service is running.
	 * @param winConfiguration Windows Protocol configuration (credentials, timeout). E.g. WMI or WinRm.
	 * @param command          Windows remote command to execute.
	 * @param embeddedFiles    The list of embedded files used in the wql remote command query.
	 * @return A {@link String} value resulting from the execution of the query.
	 * @throws ClientException when anything wrong happens.
	 */
	String executeWinRemoteCommand(
		String hostname,
		IWinConfiguration winConfiguration,
		String command,
		List<String> embeddedFiles
	) throws ClientException;

	/**
	 * Execute a WQL query and render its result as a text table, for the CLI and the MCP tools:
	 * the columns are the properties the query selects, or no header when it selects {@code *}.
	 *
	 * @param hostname         The hostname of the device where the WMI service is running.
	 * @param winConfiguration Windows Protocol configuration (credentials, timeout). E.g. WMI or WinRm.
	 * @param query            The WQL to execute.
	 * @param namespace        The WMI namespace where all the classes reside; {@code null} for {@code root\cimv2}.
	 * @return The result as a text table.
	 * @throws ClientException when anything goes wrong (details in cause).
	 */
	default String executeWqlQuery(
		final String hostname,
		@NonNull final IWinConfiguration winConfiguration,
		@NonNull final String query,
		final String namespace
	) throws ClientException {
		final List<List<String>> result = executeWmi(
			hostname,
			winConfiguration,
			query,
			namespace == null ? WMI_DEFAULT_NAMESPACE : namespace,
			null
		);
		final String[] columns = StringHelper.extractColumns(query);
		return columns.length == 1 && "*".equals(columns[0])
			? TextTableHelper.generateTextTable(result)
			: TextTableHelper.generateTextTable(columns, result);
	}

	/**
	 * Open protocol-native access to the files of a remote Windows host, for a file source poll.
	 * The default has none: the file source then reads files by running PowerShell scripts as
	 * remote commands through {@link #executeWinRemoteCommand(String, IWinConfiguration, String, List)}.
	 *
	 * @param hostname         The hostname of the device.
	 * @param winConfiguration Windows Protocol configuration (credentials, timeout). E.g. WMI or WinRm.
	 * @return The file operations, to be closed by the caller once the poll is over, or empty when
	 *         this protocol has no native file access.
	 */
	default Optional<WinFileOperations> openFileOperations(
		final String hostname,
		final IWinConfiguration winConfiguration
	) {
		return Optional.empty();
	}

	/**
	 * Whether this error message is an acceptable WMI COM error.
	 *
	 * @param errorMessage string value representing the message of the WMI COM exception
	 * @return boolean value
	 */
	static boolean isAcceptableWmiComError(final String errorMessage) {
		// CHECKSTYLE:OFF
		return (
			errorMessage != null &&
			// @formatter:off
			(
				errorMessage.contains(WBEM_E_NOT_FOUND) ||
				errorMessage.contains(WBEM_E_INVALID_NAMESPACE) ||
				errorMessage.contains(WBEM_E_INVALID_CLASS)
			)
			// @formatter:on
		);
		// CHECKSTYLE:ON
	}
}
