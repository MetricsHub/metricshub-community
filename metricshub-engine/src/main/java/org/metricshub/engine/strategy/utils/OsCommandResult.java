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

import lombok.Data;
import org.metricshub.engine.common.helpers.StringHelper;

/**
 * The {@code OsCommandResult} class represents the result of an operating system command execution.
 * <p>
 * The result has LF line endings whatever the host: the line filters, the column selection and the criteria read
 * the same lines from a host that answers with CRLF, or with the CR CR LF that some Windows commands print through a
 * pipe.
 */
@Data
public class OsCommandResult {

	private final String result;
	private final String noPasswordCommand;

	/**
	 * The result of a command.
	 *
	 * @param result            The output of the command, stored with each CRLF converted to LF
	 * @param noPasswordCommand The command with its passwords masked
	 */
	public OsCommandResult(final String result, final String noPasswordCommand) {
		this.result = result == null ? null : StringHelper.toLf(result);
		this.noPasswordCommand = noPasswordCommand;
	}
}
