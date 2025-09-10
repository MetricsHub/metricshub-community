package org.metricshub.engine.extension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;

@Data
@AllArgsConstructor
@Builder
public class TestConfiguration implements IConfiguration {

	@Override
	public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

	@Override
	public String getHostname() {
		return null;
	}

	@Override
	public void setHostname(String hostname) {}

	@Override
	public IConfiguration copy() {
		return null;
	}

	@Override
	public void setTimeout(Long timeout) {}

	@Override
	public Object getProperty(final String property) {
		return "myProperty".equalsIgnoreCase(property) ? "myPropertyValue" : null;
	}

	@Override
	public boolean isCorrespondingProtocol(final String protocol) {
		return "test".equalsIgnoreCase(protocol);
	}
}
