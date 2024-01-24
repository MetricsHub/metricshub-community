package org.sentrysoftware.metricshub.agent.config.exporter;

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration class for metric exporters.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExporterConfig {

	@Default
	@JsonSetter(nulls = SKIP)
	private OtlpExporterConfig otlp = OtlpExporterConfig.builder().build();
}
