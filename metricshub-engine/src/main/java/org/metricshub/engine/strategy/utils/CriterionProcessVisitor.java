package org.metricshub.engine.strategy.utils;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.detection.CriterionTestResult;

/**
 * Visitor class for handling local operating system details related to process criteria.
 */
@Slf4j
@RequiredArgsConstructor
public class CriterionProcessVisitor implements LocalOsHandler.ILocalOsVisitor {

	private static final String CRITERION_PROCESSOR_VISITOR_LOG_MESSAGE = "Hostname {} - Process Criterion, {}";

	private final ExtensionManager extensionManger;

	@NonNull
	private final ProcessCriterion processCriterion;

	@NonNull
	private final String hostname;

	@Getter
	private CriterionTestResult criterionTestResult;

	@Override
	public void visit(final LocalOsHandler.Windows os) {
		if (extensionManger == null) {
			throw new IllegalStateException("The ExtensionManager cannot be null for Local Windows Process criterion check.");
		}
		extensionManger
			.findExtensionByType("wmi")
			.map(extension -> extension.processCriterion(processCriterion, null, null))
			.ifPresent(result -> criterionTestResult = result);
	}

	@Override
	public void visit(final LocalOsHandler.Linux os) {
		final List<List<String>> result = listAllLinuxProcesses();
		processResult(result);
	}

	@Override
	public void visit(final LocalOsHandler.Hp os) {
		notImplemented(LocalOsHandler.HP.getOsTag());
	}

	@Override
	public void visit(final LocalOsHandler.Sun os) {
		notImplemented(LocalOsHandler.SUN.getOsTag());
	}

	@Override
	public void visit(final LocalOsHandler.Solaris os) {
		notImplemented(LocalOsHandler.SOLARIS.getOsTag());
	}

	@Override
	public void visit(final LocalOsHandler.FreeBsd os) {
		notImplemented(LocalOsHandler.FREE_BSD.getOsTag());
	}

	@Override
	public void visit(final LocalOsHandler.OpenBsd os) {
		notImplemented(LocalOsHandler.OPEN_BSD.getOsTag());
	}

	@Override
	public void visit(final LocalOsHandler.NetBsd os) {
		notImplemented(LocalOsHandler.NET_BSD.getOsTag());
	}

	@Override
	public void visit(final LocalOsHandler.Aix os) {
		notImplemented(LocalOsHandler.AIX.getOsTag());
	}

	@Override
	public void visit(final LocalOsHandler.MacOsx os) {
		notImplemented(LocalOsHandler.MAC_OS_X.getOsTag());
	}

	/**
	 * List all Linux processes.
	 *
	 * @return A list containing details of all Linux processes.
	 */
	public static List<List<String>> listAllLinuxProcesses() {
		return ProcessHandle.allProcesses().map(CriterionProcessVisitor::getProcessDetails).collect(Collectors.toList()); //NOSONAR
	}

	/**
	 * Get the "ps Command" output useful information: pid;comm;ruser;ppid;args
	 *
	 * @param processHandle
	 * @return
	 */
	static List<String> getProcessDetails(final ProcessHandle processHandle) {
		return List.of(
			String.valueOf(processHandle.pid()),
			processHandle.info().command().orElse(""),
			processHandle.info().user().orElse(""),
			processHandle.parent().map(ProcessHandle::pid).map(String::valueOf).orElse(""),
			processHandle.info().commandLine().orElse("")
		);
	}

	/**
	 * Process the command process list result.
	 *
	 * @param result
	 */
	private void processResult(final List<List<String>> result) {
		result
			.stream()
			.filter(line -> line.get(1).matches(processCriterion.getCommandLine()))
			.findFirst()
			.ifPresentOrElse(
				line ->
					success(
						String.format(
							"One or more currently running processes match the following regular expression:\n- " +
							"Regexp (should match with the command-line): %s",
							processCriterion.getCommandLine()
						)
					),
				() ->
					fail(
						String.format(
							"""
							No currently running processes match the following regular expression:
							- Regexp (should match with the command-line): %s
							- Currently running process list:
							%s""",
							processCriterion.getCommandLine(),
							result
								.stream()
								.map(line -> line.stream().collect(Collectors.joining(TABLE_SEP)))
								.collect(Collectors.joining(NEW_LINE))
						)
					)
			);
	}

	/**
	 * Not implemented OS case.
	 *
	 * @param os
	 */
	private void notImplemented(final String os) {
		success(String.format("Process presence check: No tests will be performed for OS: %s.", os));
	}

	/**
	 * Create a failed criterionTestResult.
	 *
	 * @param message error message.
	 */
	private void fail(final String message) {
		log.error(CRITERION_PROCESSOR_VISITOR_LOG_MESSAGE, hostname, message);
		criterionTestResult = CriterionTestResult.builder().message(message).build();
	}

	/**
	 * Create a success criterionTestResult.
	 *
	 * @param message success message.
	 */
	private void success(final String message) {
		log.debug(CRITERION_PROCESSOR_VISITOR_LOG_MESSAGE, hostname, message);
		criterionTestResult = CriterionTestResult.builder().success(true).message(message).build();
	}
}
