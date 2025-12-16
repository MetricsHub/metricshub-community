import * as React from "react";
import { Box, TableCell, TableHead, TableRow } from "@mui/material";
import HoverInfo from "./HoverInfo";
import { truncatedCellSx } from "../../common/table-styles";
import {
	getBaseMetricKey,
	getMetricLabel,
	getMetricMetadata,
} from "../../../../../utils/metrics-helper";
import { cleanUnit } from "../../../../../utils/formatters";

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
	// Memoize utilization group values (hooks must be called before any conditional returns)
	const utilizationMeta = React.useMemo(
		() => (isUtilizationGroup ? getMetricMetadata(group.baseName, metaMetrics) : null),
		[isUtilizationGroup, group.baseName, metaMetrics],
	);
	const utilizationUnit = React.useMemo(() => utilizationMeta?.unit, [utilizationMeta?.unit]);
	const displayUnit = React.useMemo(
		() => (utilizationUnit === "1" ? "%" : cleanUnit(utilizationUnit)),
		[utilizationUnit],
	);
	const hoverTitle = React.useMemo(
		() => (isUtilizationGroup ? getMetricLabel(group.baseName) : null),
		[isUtilizationGroup, group.baseName],
	);

	if (!isUtilizationGroup) {
		const colCount = 1 + group.metricKeys.length;
		const colWidth = `${100 / colCount}%`;

		return (
			<TableHead>
				<TableRow>
					<TableCell sx={{ ...truncatedCellSx, width: colWidth }}>Instance</TableCell>
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
						const cleanedUnit = cleanUnit(unit);

						return (
							<TableCell key={key} align="left" sx={{ ...truncatedCellSx, width: colWidth }}>
								<HoverInfo
									title={hoverTitle}
									description={description}
									unit={cleanedUnit}
									sx={{ display: "inline-block", maxWidth: "100%", ...truncatedCellSx }}
								>
									{colLabel}
									{cleanedUnit && (
										<Box
											component="span"
											sx={{ color: "text.secondary", fontSize: "0.75em", ml: 0.5 }}
										>
											({cleanedUnit})
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

	const { description } = utilizationMeta || {};

	return (
		<TableHead>
			<TableRow>
				<TableCell sx={{ ...truncatedCellSx, width: "50%" }}>Instance</TableCell>
				<TableCell sx={{ ...truncatedCellSx, width: "50%" }}>
					<HoverInfo
						title={hoverTitle || ""}
						description={description}
						unit={displayUnit}
						sx={{ display: "inline-block", maxWidth: "100%", ...truncatedCellSx }}
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
