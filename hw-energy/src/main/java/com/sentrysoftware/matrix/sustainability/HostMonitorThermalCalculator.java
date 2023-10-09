package com.sentrysoftware.matrix.sustainability;

import com.sentrysoftware.matrix.telemetry.TelemetryManager;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HostMonitorThermalCalculator {

	private TelemetryManager telemetryManager;

	/**
	 * Computes the heating margin of the host monitor
	 * @return Double
	 */
	public static Double computeHeatingMargin() {
		return null;
	}

	/**
	 * Computes the ambient temperature
	 * @return Double
	 */
	public static Double computeAmbientTemperature() {
		return null;
	}

	/**
	 * Estimates the overall average temperature
	 * @return Double
	 */
	public static Double estimateAverageTemperature() {
		return null;
	}

	/**
	 * Estimates temperature warning threshold average
	 * @return Double
	 */
	public static Double estimateTemperatureWarningThresholdAverage() {
		return null;
	}

	/**
	 * Estimates thermal dissipation rate
	 * @return Double
	 */
	public static Double estimateThermalDissipationRate() {
		return null;
	}
}
