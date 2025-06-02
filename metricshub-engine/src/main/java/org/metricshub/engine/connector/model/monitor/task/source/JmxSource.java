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

import static com.fasterxml.jackson.annotation.Nulls.FAIL;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a JMX source task that fetches metrics via JMX.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JmxSource extends Source {

	private static final long serialVersionUID = 1L;

	/** JMX host */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String host;

	/** JMX port */
	private int port;

	/** Multiple MBean configurations */
	@JsonSetter(nulls = FAIL)
	private List<MBeanConfig> mbeans;

	/** Single MBean configuration */
	private MBeanConfig mbean;

	@Builder
	@JsonCreator
	public JmxSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "host", required = true) @NonNull String host,
		@JsonProperty("port") int port,
		@JsonProperty("mbeans") List<MBeanConfig> mbeans,
		@JsonProperty("mbean") MBeanConfig mbean,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.host = host;
		this.port = port;
		this.mbeans = mbeans;
		this.mbean = mbean;
	}

	@Override
	public JmxSource copy() {
		return JmxSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.host(host)
			.port(port)
			.mbeans(mbeans != null ? new ArrayList<>(mbeans) : null)
			.mbean(mbean)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		host = updater.apply(host);
		if (mbeans != null) {
			mbeans.forEach(cfg -> cfg.update(updater));
		}
		if (mbean != null) {
			mbean.update(updater);
		}
	}

	@Override
	public String toString() {
		StringJoiner sj = new StringJoiner(NEW_LINE);
		sj.add(super.toString());
		addNonNull(sj, "- host=", host);
		addNonNull(sj, "- port=", String.valueOf(port));
		if (mbeans != null) {
			addNonNull(sj, "- mbeans=", mbeans.toString());
		}
		if (mbean != null) {
			addNonNull(sj, "- mbean=", mbean.toString());
		}
		return sj.toString();
	}

	@Override
	public SourceTable accept(ISourceProcessor sourceProcessor) {
		try {
			return sourceProcessor.process(this);
		} catch (Exception e) {
			throw new RuntimeException("Error processing JmxSource: " + e.getMessage(), e);
		}
	}

	/**
	 * Configuration for a single MBean query.
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@EqualsAndHashCode
	public static class MBeanConfig {

		@NonNull
		@JsonSetter(nulls = FAIL)
		@JsonDeserialize(using = NonBlankDeserializer.class)
		private String objectName;

		@JsonSetter(nulls = FAIL)
		private List<String> attributes;

		private List<String> keysAsAttributes;

		public MBeanConfig(@NonNull String objectName, List<String> attributes, List<String> keysAsAttributes) {
			this.objectName = objectName;
			this.attributes = attributes;
			this.keysAsAttributes = keysAsAttributes;
		}

		public void update(UnaryOperator<String> updater) {
			objectName = updater.apply(objectName);
		}

		@Override
		public String toString() {
			return new StringJoiner(", ")
				.add("objectName=" + objectName)
				.add("attributes=" + attributes)
				.add("keysAsAttributes=" + keysAsAttributes)
				.toString();
		}
	}
}
