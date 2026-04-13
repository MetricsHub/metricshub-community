package org.metricshub.engine.connector.model.monitor.task.source;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.metricshub.engine.connector.deserializer.custom.FileSourceModeDeserializer;
import org.metricshub.engine.connector.deserializer.custom.LinkedHashSetDeserializer;
import org.metricshub.engine.connector.deserializer.custom.SizeDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * A file source that reads content from local or remote files.
 * Supports incremental reading in LOG mode (using cursors to track position) or full-file reading in FLAT mode.
 * Paths may include wildcards (e.g. {@code C:\logs\*.log} or {@code /var/log/*.log}).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * Default max size per poll value in bytes (5 MB).
	 */
	private static final long DEFAULT_MAX_SIZE_PER_POLL = 5 * 1024 * 1024;

	/**
	 * Value for unlimited size per poll: no cap on bytes read per cycle.
	 */
	public static final long UNLIMITED_SIZE_PER_POLL = -1;

	/**
	 * Maximum number of bytes to read per polling cycle across all files (LOG mode).
	 * Use {@link #UNLIMITED_SIZE_PER_POLL} for no limit. Default is 5 MB.
	 * When set via YAML, uses {@link SizeDeserializer}: value is converted to bytes once (e.g. {@code 5Mb} → 5×1024×1024); no conversion elsewhere.
	 */
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = SizeDeserializer.class)
	private long maxSizePerPoll = DEFAULT_MAX_SIZE_PER_POLL;

	/**
	 * File path patterns to read (e.g. {@code C:\logs\*.log}, {@code /var/log/app/*.log}).
	 * Supports comma-separated strings or YAML arrays.
	 */
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = LinkedHashSetDeserializer.class)
	private Set<String> paths = new LinkedHashSet<>();

	/**
	 * Processing mode: {@link FileSourceProcessingMode#LOG} for incremental reading with cursors,
	 * or {@link FileSourceProcessingMode#FLAT} for full-file read on each poll. Default is LOG.
	 */
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = FileSourceModeDeserializer.class)
	private FileSourceProcessingMode mode;

	/**
	 * Creates a file source.
	 *
	 * @param type                   the source type (e.g. {@code "file"})
	 * @param computes               optional compute steps
	 * @param forceSerialization     whether to force serialization
	 * @param paths                  file path patterns; may be null (defaults to empty set)
	 * @param maxSizePerPoll         max size per poll in bytes; null or 0 use default, negative uses unlimited
	 * @param mode                   LOG (incremental) or FLAT (full-file); null defaults to LOG
	 * @param key                    the source key
	 * @param executeForEachEntryOf  optional iteration configuration
	 */
	@Builder
	public FileSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty("paths") Set<String> paths,
		@JsonProperty("maxSizePerPoll") Long maxSizePerPoll,
		@JsonProperty("mode") FileSourceProcessingMode mode,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.paths = (paths != null) ? paths : new LinkedHashSet<>();
		this.mode = mode != null ? mode : FileSourceProcessingMode.LOG;

		if (maxSizePerPoll == null || maxSizePerPoll == 0) {
			this.maxSizePerPoll = DEFAULT_MAX_SIZE_PER_POLL;
		} else {
			this.maxSizePerPoll = maxSizePerPoll > 0 ? maxSizePerPoll : UNLIMITED_SIZE_PER_POLL;
		}
	}

	@Override
	public Source copy() {
		return FileSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.paths(paths != null ? new LinkedHashSet<>(paths) : new LinkedHashSet<>())
			.maxSizePerPoll(maxSizePerPoll)
			.mode(mode)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		paths = paths.stream().map(updater).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- paths=", paths);
		addNonNull(stringJoiner, "- maxSizePerPoll=", maxSizePerPoll);
		addNonNull(stringJoiner, "- mode=", mode);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
