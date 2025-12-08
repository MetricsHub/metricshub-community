import * as React from "react";
import { Box, TableCell, TableHead, TableRow } from "@mui/material";
import HoverInfo from "./HoverInfo";
import {
	getBaseMetricKey,
	getMetricLabel,
	getMetricMetadata,
} from "../../../../../utils/metrics-helper";

/**
 * Renders the header for the pivot group table.
 *
 * @param {{
 *   group: { baseName: string, metricKeys: string[] },
 *   isUtilizationGroup: boolean,
 *   metaMetrics?: Record<string, { unit?: string, description?: string, type?: string }>
 * }} props
 */
const PivotGroupHeader = ({ group, isUtilizationGroup, metaMetrics }) => {
	if (!isUtilizationGroup) {
		return (
			<TableHead>
				<TableRow>
					<TableCell sx={{ width: "25%" }}>Instance</TableCell>
					{group.metricKeys.map((key) => {
						let colLabel;
						let hoverTitle;
						if (group.metricKeys.length === 1) {
							colLabel = "Value";
							hoverTitle = getBaseMetricKey(key);
						} else {
							colLabel = getMetricLabel(key);
							hoverTitle = colLabel;
						}

						const meta = getMetricMetadata(key, metaMetrics);
						const { description, unit } = meta;
						const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";

						return (
							<TableCell key={key} align="left">
								<HoverInfo
									title={hoverTitle}
									description={description}
									unit={cleanUnit}
									sx={{ display: "inline-block" }}
								>
									{colLabel}
									{cleanUnit && (
										<Box
											component="span"
											sx={{ color: "text.secondary", fontSize: "0.75em", ml: 0.5 }}
										>
											({cleanUnit})
										</Box>
									)}
								</HoverInfo>
							</TableCell>
						);
					})}
				</TableRow>
			</TableHead>
		);
	}

	const meta = getMetricMetadata(group.baseName, metaMetrics);
	const { description, unit } = meta;
	const displayUnit = unit === "1" ? "%" : unit ? unit.replace(/[{}]/g, "") : "";

	// For utilization groups, we usually have multiple keys (e.g. user, system, idle)
	// but they are aggregated into one column. The baseName is the metric prefix.
	// If we want the hover to show the base name, we can use it.
	const hoverTitle = getMetricLabel(group.baseName);

	return (
		<TableHead>
			<TableRow>
				<TableCell sx={{ width: "25%" }}>Instance</TableCell>
				<TableCell>
					<HoverInfo
						title={hoverTitle}
						description={description}
						unit={displayUnit}
						sx={{ display: "inline-block" }}
					>
						Value
						{displayUnit && (
							<Box component="span" sx={{ color: "text.secondary", fontSize: "0.75em", ml: 0.5 }}>
								({displayUnit})
							</Box>
						)}
					</HoverInfo>
				</TableCell>
			</TableRow>
		</TableHead>
	);
};

export default React.memo(PivotGroupHeader);
