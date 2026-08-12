import * as React from "react";
import {
	Accordion,
	AccordionDetails,
	AccordionSummary,
	Box,
	Stack,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableRow,
	Typography,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { explorerMetricNameSx } from "../../utils/metric-font";

/**
 * Accordion section wrapping a property/value table (attributes / metrics).
 *
 * @param {object} props
 * @param {string} props.title
 * @param {React.ReactNode} [props.icon]
 * @param {{ id: string; property: string; value: string }[]} props.rows
 * @param {boolean} [props.defaultExpanded]
 * @param {boolean} [props.monospaceProperty] use Explorer metric font for property names
 */
const GroupConfigSection = ({
	title,
	icon,
	rows,
	defaultExpanded = true,
	monospaceProperty = false,
}) => {
	const theme = useTheme();
	const isDarkMode = theme.palette.mode === "dark";
	const headerBg = isDarkMode ? theme.palette.neutral[800] : theme.palette.neutral[100];

	if (!rows || rows.length === 0) return null;

	return (
		<Accordion
			defaultExpanded={defaultExpanded}
			variant="outlined"
			disableGutters
			sx={{
				bgcolor: "transparent",
				"&:before": { display: "none" },
				"& .MuiAccordionDetails-root": { bgcolor: "transparent" },
			}}
		>
			<AccordionSummary
				expandIcon={<ExpandMoreIcon />}
				sx={{
					bgcolor: "action.hover",
					transition: "background-color 0.4s ease",
					"&:hover": { bgcolor: "action.selected" },
					"& .MuiAccordionSummary-content": { my: 1 },
				}}
			>
				<Stack direction="row" alignItems="center" spacing={1}>
					{icon && (
						<Box component="span" sx={{ display: "inline-flex", alignItems: "center" }}>
							{icon}
						</Box>
					)}
					<Typography fontWeight={600}>{title}</Typography>
				</Stack>
			</AccordionSummary>

			<AccordionDetails sx={{ p: 0, bgcolor: "transparent" }}>
				<Table size="small" sx={{ bgcolor: "transparent" }}>
					<TableHead>
						<TableRow sx={{ bgcolor: "transparent" }}>
							<TableCell
								sx={{
									bgcolor: headerBg,
									fontWeight: 700,
									color: "text.secondary",
									fontSize: "0.72rem",
									letterSpacing: "0.06em",
									textTransform: "uppercase",
									py: 1,
									borderBottom: 1,
									borderColor: "divider",
									width: "40%",
								}}
							>
								Property
							</TableCell>
							<TableCell
								sx={{
									bgcolor: headerBg,
									fontWeight: 700,
									color: "text.secondary",
									fontSize: "0.72rem",
									letterSpacing: "0.06em",
									textTransform: "uppercase",
									py: 1,
									borderBottom: 1,
									borderColor: "divider",
								}}
							>
								Value
							</TableCell>
						</TableRow>
					</TableHead>
					<TableBody>
						{rows.map((row) => (
							<TableRow
								key={row.id}
								sx={{
									bgcolor: "transparent",
									"&:hover": { bgcolor: "action.hover" },
									"&:last-child td": { borderBottom: 0 },
								}}
							>
								<TableCell
									sx={{
										bgcolor: "transparent",
										color: "text.secondary",
										verticalAlign: "top",
										py: 0.75,
										width: "40%",
										borderColor: "divider",
										...(monospaceProperty ? explorerMetricNameSx : {}),
									}}
								>
									{row.property}
								</TableCell>
								<TableCell
									sx={{
										bgcolor: "transparent",
										verticalAlign: "top",
										py: 0.75,
										wordBreak: "break-word",
										borderColor: "divider",
									}}
								>
									{row.value}
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			</AccordionDetails>
		</Accordion>
	);
};

export default GroupConfigSection;
