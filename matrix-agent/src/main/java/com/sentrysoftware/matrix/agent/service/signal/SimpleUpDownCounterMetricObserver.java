package com.sentrysoftware.matrix.agent.service.signal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SimpleUpDownCounterMetricObserver extends AbstractMetricObserver {

	private final Double metricValue;

	@Builder(setterPrefix = "with")
	public SimpleUpDownCounterMetricObserver(
		final Meter meter,
		final String metricName,
		final String unit,
		final String description,
		final Attributes attributes,
		final Double metricValue
	) {
		super(meter, attributes, metricName, unit, description);
		this.metricValue = metricValue;
	}

	@Override
	public void init() {
		newDoubleUpDownCounterBuilder().buildWithCallback(recorder -> recorder.record(metricValue, attributes));
	}
}
