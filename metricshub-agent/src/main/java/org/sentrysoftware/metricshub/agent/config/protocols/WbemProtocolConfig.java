package org.sentrysoftware.metricshub.agent.config.protocols;

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.sentrysoftware.metricshub.agent.deserialization.TimeDeserializer;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.engine.configuration.WbemConfiguration;

/**
 * Configuration class for the Wbem (Web-Based Enterprise Management) protocol.
 * Extends {@link AbstractProtocolConfig}.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class WbemProtocolConfig extends AbstractProtocolConfig {

	@Default
	@JsonSetter(nulls = SKIP)
	TransportProtocols protocol = TransportProtocols.HTTPS;

	@Default
	private Integer port = 5989;

	private String namespace;

	@Default
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

	String username;

	char[] password;

	String vCenter;

	/**
	 * Create a new {@link WbemConfiguration} instance based on the current members
	 *
	 * @return The {@link WbemConfiguration} instance
	 */
	@Override
	public IConfiguration toConfiguration() {
		return WbemConfiguration
			.builder()
			.namespace(namespace)
			.username(username)
			.password(super.decrypt(password))
			.port(port)
			.protocol(protocol)
			.timeout(timeout)
			.vCenter(vCenter)
			.build();
	}

	@Override
	public String toString() {
		String desc = protocol + "/" + port;
		if (username != null) {
			desc = desc + " as " + username;
		}
		return desc;
	}
}
