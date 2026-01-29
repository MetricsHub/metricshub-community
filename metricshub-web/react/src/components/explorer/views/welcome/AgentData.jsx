import * as React from "react";
import { Typography, Box, Paper, Stack, Alert, Collapse, IconButton } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import EntityHeader from "../common/EntityHeader";
import MetricsAccordion from "../common/MetricsAccordion";
import { formatBytes } from "../../../../utils/formatters";

const getUsageColor = (percentage) => {
	if (typeof percentage !== "number") return "#1976d2";
	if (percentage < 50) return "#2e7d32";
	if (percentage < 80) return "#ed6c02";
	return "#d32f2f";
};

const StatBox = ({ label, value, bgcolor }) => (
	<Paper
		elevation={0}
		sx={{
			bgcolor: bgcolor,
			p: 2,
			borderRadius: 2,
			color: "white",
			display: "flex",
			flexDirection: "column",
			alignItems: "center",
			flex: 1,
			textAlign: "center",
			minWidth: 150,
		}}
	>
		<Typography
			variant="subtitle2"
			sx={{ opacity: 0.9, mb: 0.5, textTransform: "uppercase", letterSpacing: 0.5 }}
		>
			{label}
		</Typography>
		<Typography variant="h5" sx={{ fontWeight: "bold" }}>
			{value}
		</Typography>
	</Paper>
);

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

	const displayedResources = numberOfConfiguredResources ?? totalResources;

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
					direction="row"
					spacing={2}
					sx={{
						mt: 1,
						flexWrap: "wrap",
						gap: 2,
						"& > *": {
							minWidth: { xs: "100%", sm: 150 },
							flex: { xs: "1 1 100%", sm: "1 1 auto" },
						},
					}}
				>
					{typeof numberOfMonitors === "number" && (
						<StatBox label="Monitors" value={numberOfMonitors} bgcolor="#1976d2" />
					)}
					{typeof memoryUsageBytes === "number" && (
						<StatBox
							label="Memory Usage"
							value={
								<Box>
									<Box component="span">
										{formatBytes(memoryUsageBytes)}
										{typeof memoryUsagePercent === "number" && memoryUsagePercent > 0 && (
											<> / {formatBytes((memoryUsageBytes * 100) / memoryUsagePercent)}</>
										)}
									</Box>
									{typeof memoryUsagePercent === "number" && (
										<Box
											component="span"
											sx={{ fontSize: "0.8em", ml: 1, opacity: 0.9, fontWeight: "normal" }}
										>
											({memoryUsagePercent.toFixed(0)}%)
										</Box>
									)}
								</Box>
							}
							bgcolor={getUsageColor(memoryUsagePercent)}
						/>
					)}
					{typeof cpuUsage === "number" && (
						<StatBox
							label="CPU Usage"
							value={`${cpuUsage.toFixed(1)}%`}
							bgcolor={getUsageColor(cpuUsage)}
						/>
					)}
				</Stack>
			)}

			{hasMetrics && <MetricsAccordion metrics={agent.metrics} />}
		</Box>
	);
};

export default React.memo(AgentData);
