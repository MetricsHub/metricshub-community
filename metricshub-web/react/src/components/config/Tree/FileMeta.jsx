import * as React from "react";
import { Stack, Box, Chip } from "@mui/material";
import { formatBytes, formatRelativeTime } from "../../../utils/formatters";

function FileMeta({ file, sx }) {
	return (
		<Stack
			direction="row"
			alignItems="center"
			spacing={1}
			sx={{ opacity: 0.65, fontSize: "0.8rem", fontWeight: 400, ...sx }}
		>
			<Box component="span">
				{`${formatBytes(file.size ?? 0)} • ${formatRelativeTime(
					file.lastModificationTime ?? Date.now(),
				)}`}
			</Box>

			{file.localOnly && (
				<Chip
					label="Unsaved"
					size="small"
					sx={{
						border: 0,
						bgcolor: "transparent",
						p: 0,
						"& .MuiChip-label": { p: 0, fontWeight: 500 },
					}}
				/>
			)}
		</Stack>
	);
}

export default React.memo(FileMeta);
