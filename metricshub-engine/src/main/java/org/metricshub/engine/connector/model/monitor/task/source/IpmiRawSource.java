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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * A source that sends one raw IPMI command to the BMC and returns its completion code and response bytes.
 * The network function, the command and the request bytes are decimal or {@code 0x} hexadecimal numbers.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IpmiRawSource extends Source {

	private static final long serialVersionUID = 1L;

	@JsonSetter(nulls = SKIP)
	private String netFn;

	@JsonSetter(nulls = SKIP)
	private String command;

	@JsonSetter(nulls = SKIP)
	private String data;

	/**
	 * Constructs a new {@code IpmiRawSource} instance with the provided attributes.
	 *
	 * @param type                  the type of the source
	 * @param computes              the list of compute operations to be applied
	 * @param forceSerialization    flag indicating whether serialization should be forced
	 * @param netFn                 the network function of the request, an even number from 0x00 to 0x3E
	 * @param command               the command byte
	 * @param data                  the request bytes, separated with spaces; may be {@code null}
	 * @param key                   the key associated with the source
	 * @param executeForEachEntryOf the execute-for-each-entry-of information
	 */
	@Builder
	@JsonCreator
	public IpmiRawSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "netFn", required = true) String netFn,
		@JsonProperty(value = "command", required = true) String command,
		@JsonProperty("data") String data,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.netFn = netFn;
		this.command = command;
		this.data = data;
	}

	public IpmiRawSource copy() {
		return IpmiRawSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.netFn(netFn)
			.command(command)
			.data(data)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		netFn = updater.apply(netFn);
		command = updater.apply(command);
		data = updater.apply(data);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		stringJoiner.add(super.toString());
		addNonNull(stringJoiner, "- netFn=", netFn);
		addNonNull(stringJoiner, "- command=", command);
		addNonNull(stringJoiner, "- data=", data);
		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
