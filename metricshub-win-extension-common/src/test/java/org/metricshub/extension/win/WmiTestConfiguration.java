package org.metricshub.extension.win;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;

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
}
