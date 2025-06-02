package org.metricshub.extension.jmx;

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.deserialization.MultiValueDeserializer;

/**
 * The JmxConfiguration class represents the host/protocol configuration for JMX connections.
 * Corresponds to “host” and “port” under a MetricsHub YAML source of type “jmx”.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JmxConfiguration implements IConfiguration {

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String host;

	@Builder.Default
	@JsonSetter(nulls = SKIP)
	private Integer port = 1099;

	@Override
	public String getHostname() {
		return "";
	}

	@Override
	public void setHostname(String hostname) {}

	@Override
	public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {
		StringHelper.validateConfigurationAttribute(
			host,
			h -> (h == null || h.isBlank()),
			() ->
				String.format(
					"Resource %s - Invalid JMX host configured. Host: %s. Please verify host value.",
					resourceKey,
					host
				)
		);

		StringHelper.validateConfigurationAttribute(
			port,
			p -> (p == null || p < 1 || p > 65535),
			() ->
				String.format(
					"Resource %s - Invalid JMX port configured. Port: %s. Please verify port value.",
					resourceKey,
					port
				)
		);
	}

	@Override
	public IConfiguration copy() {
		return JmxConfiguration.builder().host(host).port(port).build();
	}

	@Override
	public String toString() {
		return String.format("JMX/%s:%d", host, port);
	}
}
