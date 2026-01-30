import React, { memo } from "react";
import PropTypes from "prop-types";
import { Box, Typography, Stack } from "@mui/material";
import MemoryIcon from "@mui/icons-material/Memory";
import SpeedIcon from "@mui/icons-material/Speed";
import MonitorHeartIcon from "@mui/icons-material/MonitorHeart";
import DevicesIcon from "@mui/icons-material/Devices";
import StatCard from "./StatCard";
import { getUsageColor } from "./utils";
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
			<Box>
				<Typography variant="h6" sx={{ mb: 2 }}>
					Metrics
				</Typography>
				<Stack
					direction="row"
					spacing={2}
					sx={{
						flexWrap: "wrap",
						gap: 2,
						"& > *": {
							flex: { xs: "1 1 100%", sm: "1 1 calc(50% - 8px)", md: "1 1 calc(25% - 12px)" },
							minWidth: { xs: "100%", sm: 200 },
						},
					}}
				>
					{typeof numberOfMonitors === "number" && (
						<StatCard
							icon={<MonitorHeartIcon />}
							label="Monitors"
							value={numberOfMonitors.toLocaleString()}
							bgcolor="#1976d2"
						/>
					)}
					{typeof numberOfConfiguredResources === "number" && (
						<StatCard
							icon={<DevicesIcon />}
							label="Resources"
							value={numberOfConfiguredResources.toLocaleString()}
							bgcolor="#7b1fa2"
						/>
					)}
					{typeof memoryUsageBytes === "number" && (
						<StatCard
							icon={<MemoryIcon />}
							label="Memory Usage"
							value={formatBytes(memoryUsageBytes)}
							subValue={
								typeof memoryUsagePercent === "number"
									? `${memoryUsagePercent.toFixed(1)}% of available`
									: undefined
							}
							bgcolor={getUsageColor(memoryUsagePercent)}
						/>
					)}
					{typeof cpuUsage === "number" && (
						<StatCard
							icon={<SpeedIcon />}
							label="CPU Usage"
							value={`${cpuUsage.toFixed(1)}%`}
							bgcolor={getUsageColor(cpuUsage)}
						/>
					)}
				</Stack>
			</Box>
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
