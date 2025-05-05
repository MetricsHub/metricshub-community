package org.metricshub.agent.service.task;

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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A task for watching a directory and triggering actions on specified events (creation, modification, deletion).
 */
@Slf4j
@Builder
public class DirectoryWatcherTask extends Thread {

	@NonNull
	private Path directory;

	@NonNull
	private Predicate<WatchEvent<?>> filter;

	@NonNull
	private Runnable onChange;

	private long await;

	@Override
	public void run() {
		try {
			watchDirectory();
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}

			log.error("Directory watcher could not be started: {}. Error: {}.", directory.toAbsolutePath(), e.getMessage());
			log.debug("Error: ", e);
		}
	}

	private void watchDirectory() throws IOException, InterruptedException {
		try (var watchService = FileSystems.getDefault().newWatchService()) {
			directory.register(
				watchService,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY
			);

			WatchKey key;
			while (!Thread.currentThread().isInterrupted()) {
				key = watchService.take();

				for (WatchEvent<?> event : key.pollEvents()) {
					performActionOnEvent(event);
				}

				if (!key.reset()) {
					log.warn("WatchKey no longer valid; directory watcher stopping.");
					break;
				}
			}
		}
	}

	/**
	 * Perform the action on the event if it matches the filter.
	 *
	 * @param event the event to process
	 */
	private void performActionOnEvent(final WatchEvent<?> event) {
		if (filter.test(event)) {
			performAction();
		}
	}

	/**
	 * Perform the action after waiting for the specified duration.
	 */
	private void performAction() {
		try {
			if (await > 0) {
				sleep(await);
			}
			onChange.run();
		} catch (InterruptedException e) {
			log.info("DirectoryWatcherTask interrupted: {}", e.getMessage());
			interrupt();
		} catch (Exception e) {
			log.error("DirectoryWatcherTask encountered an error: {}", e.getMessage(), e);
		}
	}
}
