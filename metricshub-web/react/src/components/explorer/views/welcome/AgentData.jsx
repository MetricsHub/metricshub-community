import * as React from "react";
import { Typography, Box, Stack, Alert, Collapse, IconButton } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import EntityHeader from "../common/EntityHeader";
import MetricsAccordion from "../common/MetricsAccordion";
import MetricCard from "../../../common/MetricCard";
import { MonitorIcon, MemoryIcon, CpuIcon } from "../../../common/MetricIcons";
import { formatBytes } from "../../../../utils/formatters";
import { gradients, getUsageColorScheme } from "../../../../theme/colors";

/**
 * Agent header, attributes and metrics section for the welcome page.
 *
 * @param {object} props - Component props
 * @param {{ attributes?: Record<string, unknown>, metrics?: Record<string, any> } | null} props.agent - The agent object
 * @param {number} [props.totalResources] - Total number of resources
 * @param {object} [props.status] - Application status data
 * @returns {JSX.Element | null}
 */
const AgentData = ({ agent, totalResources, status }) => {
	const [showLicenseWarning, setShowLicenseWarning] = React.useState(true);

	const attributes = React.useMemo(() => agent?.attributes ?? {}, [agent?.attributes]);
	const version = React.useMemo(
		() => attributes.version || attributes.cc_version || "",
		[attributes.version, attributes.cc_version],
	);
	const hasMetrics = React.useMemo(
		() => !!(agent?.metrics && Object.keys(agent.metrics).length > 0),
		[agent?.metrics],
	);

	const {
		numberOfConfiguredResources,
		numberOfMonitors,
		memoryUsageBytes,
		memoryUsagePercent,
		cpuUsage,
		licenseDaysRemaining,
	} = status || {};

	const licenseWarning = React.useMemo(() => {
		if (licenseDaysRemaining === null || licenseDaysRemaining === undefined) return null;
		if (licenseDaysRemaining < 7) {
			return { severity: "error", message: `License expires in ${licenseDaysRemaining} days!` };
		}
		if (licenseDaysRemaining < 30) {
			return { severity: "warning", message: `License expires in ${licenseDaysRemaining} days.` };
		}
		return null;
	}, [licenseDaysRemaining]);

	const displayedResources = numberOfConfiguredResources ?? totalResources;

	// Format memory usage value with proper display
	const memoryUsageValue = React.useMemo(() => {
		if (typeof memoryUsageBytes !== "number") return null;

		const used = formatBytes(memoryUsageBytes);
		const hasPercent = typeof memoryUsagePercent === "number" && memoryUsagePercent > 0;

		if (!hasPercent) return used;

		const total = formatBytes((memoryUsageBytes * 100) / memoryUsagePercent);
		const percent = memoryUsagePercent.toFixed(0);

		return (
			<Box>
				<Box component="span">
					{used} / {total}
				</Box>
				<Box component="span" sx={{ fontSize: "0.8em", ml: 1, opacity: 0.9, fontWeight: "normal" }}>
					({percent}%)
				</Box>
			</Box>
		);
	}, [memoryUsageBytes, memoryUsagePercent]);

	if (!agent) {
		return null;
	}

	const title =
		attributes["service.name"] === "MetricsHub Agent"
			? "MetricsHub Community"
			: "MetricsHub Enterprise";

	const osType = attributes["os.type"];
	let action = null;
	if (typeof osType === "string") {
		const lower = osType.toLowerCase();
		if (lower.includes("windows")) {
			action = (
				<Box component="img" src="/windows.svg" alt="Windows" sx={{ width: 40, height: 40 }} />
			);
		} else if (lower.includes("linux")) {
			action = <Box component="img" src="/linux.svg" alt="Linux" sx={{ width: 60, height: 60 }} />;
		}
	}

	return (
		<Box display="flex" flexDirection="column" gap={2}>
			{licenseWarning && (
				<Collapse in={showLicenseWarning}>
					<Alert
						severity={licenseWarning.severity}
						action={
							<IconButton
								aria-label="close"
								color="inherit"
								size="small"
								onClick={() => setShowLicenseWarning(false)}
							>
								<CloseIcon fontSize="inherit" />
							</IconButton>
						}
					>
						{licenseWarning.message}
					</Alert>
				</Collapse>
			)}

			<EntityHeader title={title} iconType="agent" attributes={attributes} action={action}>
				{version && <Typography variant="subtitle1">Version: {version}</Typography>}
				{typeof displayedResources === "number" && (
					<Typography variant="subtitle1">Total resources: {displayedResources}</Typography>
				)}
			</EntityHeader>

			{status && (
				<Stack
					direction={{ xs: "column", sm: "row" }}
					sx={{
						mt: 1,
						flexWrap: "wrap",
						gap: 2,
						"& > *": {
							flex: { sm: "1 1 auto" },
							minWidth: { sm: 150 },
						},
					}}
				>
					{typeof numberOfMonitors === "number" && (
						<MetricCard
							label="Monitors"
							value={numberOfMonitors}
							gradient={gradients.primary}
							icon={<MonitorIcon />}
							tooltip="Number of active monitors"
						/>
					)}
					{memoryUsageValue && (
						<MetricCard
							label="Memory Usage"
							value={memoryUsageValue}
							gradient={getUsageColorScheme(memoryUsagePercent).gradient}
							icon={<MemoryIcon />}
							tooltip="Current memory consumption"
						/>
					)}
					{typeof cpuUsage === "number" && (
						<MetricCard
							label="CPU Usage"
							value={`${cpuUsage.toFixed(1)}%`}
							gradient={getUsageColorScheme(cpuUsage).gradient}
							icon={<CpuIcon />}
							tooltip="Current CPU utilization"
						/>
					)}
				</Stack>
			)}

			{hasMetrics && <MetricsAccordion metrics={agent.metrics} />}
		</Box>
	);
};

export default React.memo(AgentData);
