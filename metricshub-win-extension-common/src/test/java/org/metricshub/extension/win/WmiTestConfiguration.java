package org.metricshub.extension.win;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;

@Data
@Builder
public class WmiTestConfiguration implements IWinConfiguration {

	private String username;
	private char[] password;
	private String namespace;

	private String hostname;

	@Default
	private Long timeout = 120L;

	@Override
	public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

	@Override
	public IConfiguration copy() {
		return null;
	}

	@Override
	public Object getProperty(final String property) {
		if (property == null || property.isEmpty()) {
			return null;
		}
		switch (property.toLowerCase()) {
			case "username":
				return getUsername();
			case "password":
				return getPassword();
			case "namespace":
				return getNamespace();
			case "hostname":
				return getHostname();
			case "timeout":
				return getTimeout();
			default:
				return null;
		}
	}

	@Override
	public boolean isCorrespondingProtocol(final String protocol) {
		return false;
	}
}
