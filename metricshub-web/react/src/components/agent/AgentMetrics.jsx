import React, { memo } from "react";
import PropTypes from "prop-types";
import { Box, Typography, Stack } from "@mui/material";
import MetricCard from "../common/MetricCard";
import { MonitorIcon, MemoryIcon, CpuIcon, ResourcesIcon } from "../common/MetricIcons";
import { gradients, getUsageColorScheme } from "../../theme/colors";
import { formatBytes } from "../../utils/formatters";

const AgentMetrics = memo(
	({
		numberOfMonitors,
		numberOfConfiguredResources,
		memoryUsageBytes,
		memoryUsagePercent,
		cpuUsage,
	}) => {
		return (
			<>
				<Typography variant="h6" sx={{ mb: 2 }}>
					Metrics
				</Typography>
				<Stack
					direction={{ xs: "column", sm: "row" }}
					sx={{
						flexWrap: "wrap",
						gap: 2,
						"& > *": {
							flex: { sm: "1 1 calc(50% - 8px)", md: "1 1 calc(25% - 12px)" },
							minWidth: { sm: 200 },
						},
					}}
				>
					{typeof numberOfMonitors === "number" && (
						<Box sx={{ display: "flex" }}>
							<MetricCard
								label="Monitors"
								value={numberOfMonitors.toLocaleString()}
								gradient={gradients.primary}
								icon={<MonitorIcon />}
								tooltip="Number of active monitors"
							/>
						</Box>
					)}
					{typeof numberOfConfiguredResources === "number" && (
						<Box sx={{ display: "flex" }}>
							<MetricCard
								label="Resources"
								value={numberOfConfiguredResources.toLocaleString()}
								gradient={gradients.purple}
								icon={<ResourcesIcon />}
								tooltip="Number of configured resources"
							/>
						</Box>
					)}
					{typeof memoryUsageBytes === "number" && (
						<Box sx={{ display: "flex" }}>
							<MetricCard
								label="Memory Usage"
								value={
									<Box>
										<Box component="span">{formatBytes(memoryUsageBytes)}</Box>
										{typeof memoryUsagePercent === "number" && (
											<Box
												component="span"
												sx={{ fontSize: "0.8em", ml: 1, opacity: 0.9, fontWeight: "normal" }}
											>
												({memoryUsagePercent.toFixed(1)}%)
											</Box>
										)}
									</Box>
								}
								gradient={getUsageColorScheme(memoryUsagePercent).gradient}
								icon={<MemoryIcon />}
								tooltip="Current memory consumption"
							/>
						</Box>
					)}
					{typeof cpuUsage === "number" && (
						<Box sx={{ display: "flex" }}>
							<MetricCard
								label="CPU Usage"
								value={`${cpuUsage.toFixed(1)}%`}
								gradient={getUsageColorScheme(cpuUsage).gradient}
								icon={<CpuIcon />}
								tooltip="Current CPU utilization"
							/>
						</Box>
					)}
				</Stack>
			</>
		);
	},
);

AgentMetrics.displayName = "AgentMetrics";

AgentMetrics.propTypes = {
	numberOfMonitors: PropTypes.number,
	numberOfConfiguredResources: PropTypes.number,
	memoryUsageBytes: PropTypes.number,
	memoryUsagePercent: PropTypes.number,
	cpuUsage: PropTypes.number,
};

export default AgentMetrics;
