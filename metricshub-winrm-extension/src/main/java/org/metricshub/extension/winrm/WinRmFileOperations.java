package org.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.FileHelper.PathPattern;
import org.metricshub.extension.win.WinFileOperations;
import org.metricshub.winrm.RemoteFileInfo;
import org.metricshub.winrm.WinRMClient;
import org.metricshub.winrm.exceptions.WinRMClientException;

/**
 * Remote file access through winrm-java's {@link WinRMClient#file(String)}: sizes, byte ranges and
 * whole files are read natively through the WinRM channel (raw bytes, host-side seek), and path
 * patterns are resolved with a host-side directory listing. Every operation runs on the client's
 * single authenticated connection; {@link #close()} releases it.
 */
@Slf4j
public class WinRmFileOperations implements WinFileOperations {

	private final WinRMClient client;
	private final String hostname;

	/**
	 * Wrap a client. The client is owned by this instance and closed with it.
	 *
	 * @param client   the client to read through
	 * @param hostname the hostname, for logging
	 */
	WinRmFileOperations(final WinRMClient client, final String hostname) {
		this.client = client;
		this.hostname = hostname;
	}

	@Override
	public Long getFileSize(final String path) {
		try {
			return client.file(path).info().map(RemoteFileInfo::size).orElse(null);
		} catch (WinRMClientException e) {
			log.info("Hostname {} - Unable to get \"{}\" file size: {}", hostname, path, e.getMessage());
			log.debug("Hostname {} - An error has occurred when reading the file size of {}: {}", hostname, path, e);
			return null;
		}
	}

	@Override
	public String readFromOffset(final String path, final Long offset, final Integer length) throws IOException {
		try {
			return new String(client.file(path).offset(offset).length(length).readBytes(), StandardCharsets.UTF_8);
		} catch (WinRMClientException e) {
			throw new IOException(String.format("Failed to read %s from offset %d on %s", path, offset, hostname), e);
		}
	}

	@Override
	public String readFileContent(final String path) {
		try {
			return client.file(path).readText(StandardCharsets.UTF_8);
		} catch (WinRMClientException e) {
			log.info("Hostname {} - Unable to get {} file content: {}", hostname, path, e.getMessage());
			log.debug("Hostname {} - An error has occurred when reading the content of {}: {}", hostname, path, e);
			return null;
		}
	}

	@Override
	public Set<String> resolve(final PathPattern pattern) throws ClientException {
		return list(pattern).stream().map(RemoteFileInfo::path).collect(Collectors.toSet());
	}

	/**
	 * List the files matching a pattern, with their properties. The host walks the pattern's root
	 * down to the pattern's depth and filters on the file name; each directory segment is then
	 * checked here, since winrm-java's glob applies to entry names only. Junctions and symbolic
	 * links are reported but never descended into.
	 *
	 * @param pattern the parsed path pattern
	 * @return the matching files
	 * @throws ClientException when the listing fails
	 */
	List<RemoteFileInfo> list(final PathPattern pattern) throws ClientException {
		final String rootPrefix = rootPrefix(pattern);
		final List<Pattern> globs = pattern.segments().stream().map(WinRmFileOperations::globToRegex).toList();
		try {
			return client
				.file(pattern.root())
				.list()
				.maxDepth(globs.size())
				.filesOnly()
				.glob(pattern.filename())
				.execute()
				.entries()
				.stream()
				.filter(entry -> matches(rootPrefix, globs, entry.path()))
				.collect(Collectors.toList());
		} catch (WinRMClientException e) {
			throw new ClientException(String.format("Failed to list %s on %s", pattern.fullPattern(), hostname), e);
		}
	}

	/**
	 * The pattern's root with a trailing backslash, the prefix every matching path starts with.
	 *
	 * @param pattern the parsed path pattern
	 * @return the root, ending with a backslash
	 */
	static String rootPrefix(final PathPattern pattern) {
		final String root = pattern.root();
		return root.endsWith(FileHelper.BACKSLASH) ? root : root + FileHelper.BACKSLASH;
	}

	/**
	 * Whether an absolute path matches a pattern: it starts with the root prefix, and the rest has
	 * exactly one segment per glob, each matching its glob. Comparisons ignore case, as Windows
	 * file names do.
	 *
	 * @param rootPrefix the pattern's root, ending with a backslash (see {@link #rootPrefix(PathPattern)})
	 * @param globs      one compiled glob per pattern segment (see {@link #globToRegex(String)})
	 * @param path       the absolute path reported by the host
	 * @return whether the path matches
	 */
	static boolean matches(final String rootPrefix, final List<Pattern> globs, final String path) {
		if (path.length() <= rootPrefix.length() || !path.regionMatches(true, 0, rootPrefix, 0, rootPrefix.length())) {
			return false;
		}
		final String[] segments = path.substring(rootPrefix.length()).split(Pattern.quote(FileHelper.BACKSLASH), -1);
		if (segments.length != globs.size()) {
			return false;
		}
		for (int i = 0; i < segments.length; i++) {
			if (!globs.get(i).matcher(segments[i]).matches()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Compile a file source glob, where only {@code *} (any sequence) and {@code ?} (one character)
	 * are special, into a case-insensitive whole-segment regular expression.
	 *
	 * @param glob the glob
	 * @return the compiled pattern
	 */
	static Pattern globToRegex(final String glob) {
		final StringBuilder regex = new StringBuilder();
		for (final char c : glob.toCharArray()) {
			switch (c) {
				case '*' -> regex.append(".*");
				case '?' -> regex.append('.');
				default -> regex.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	@Override
	public void close() {
		client.close();
	}
}
