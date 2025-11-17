import * as React from "react";
import { Typography, Box } from "@mui/material";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";
import ExplorerTree from "../components/explorer/tree/ExplorerTree";
import { useAppSelector } from "../hooks/store";
import { selectExplorerLastUpdatedAt } from "../store/slices/explorerSlice";

/**
 * Monitor page component
 * @returns JSX.Element
 */
const ExplorerPage = () => {
	const lastUpdatedAt = useAppSelector(selectExplorerLastUpdatedAt);
	const lastUpdatedLabel = React.useMemo(() => {
		if (!lastUpdatedAt) return null;
		const d = new Date(lastUpdatedAt);
		return d.toLocaleTimeString();
	}, [lastUpdatedAt]);

	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
					<Box sx={{ mb: 1, display: "flex", alignItems: "baseline", gap: 1 }}>
						<Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
							Hierarchy
						</Typography>
						{lastUpdatedLabel && (
							<Typography variant="caption" color="text.secondary">
								Updated {lastUpdatedLabel}
							</Typography>
						)}
					</Box>
					<Box sx={{ flex: 1, minHeight: 0, overflow: "auto" }}>
						<ExplorerTree />
					</Box>
				</Box>
			</Left>
			<Right>
				<Typography>Monitored Resources</Typography>
			</Right>
		</SplitScreen>
	);
};

export default ExplorerPage;
