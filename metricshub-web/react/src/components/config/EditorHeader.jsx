import { Stack, Typography, Chip, Button, Box } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import VerifiedIcon from "@mui/icons-material/Verified";
import { useAppSelector } from "../../hooks/store";

export default function EditorHeader({ selected, saving, validation, onValidate, onSave }) {
	const dirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const isDirty = !!dirtyByName?.[selected];

	return (
		<Stack direction="row" alignItems="center" justifyContent="space-between">
			{/* File name + unsaved indicator */}
			<Stack direction="row" alignItems="center" spacing={1}>
				<Typography variant="subtitle1" sx={{ fontWeight: isDirty ? 510 : 500 }}>
					{selected ?? "Select a file to edit"}
				</Typography>

				{isDirty && selected && (
					<Box
						component="span"
						aria-hidden
						sx={{
							width: 8,
							height: 8,
							borderRadius: "50%",
							bgcolor: (t) => t.palette.warning.main,
							ml: 0.25,
						}}
					/>
				)}
			</Stack>

			{/* Right side buttons */}
			<Stack direction="row" spacing={1} alignItems="center">
				{validation && (
					<Chip
						size="small"
						icon={validation.valid ? <VerifiedIcon /> : undefined}
						color={validation.valid ? "success" : "error"}
						label={validation.valid ? "Valid" : validation.error || "Invalid"}
					/>
				)}
				<Button size="small" onClick={onValidate} disabled={!selected}>
					Validate
				</Button>
				<Button
					size="small"
					startIcon={<SaveIcon />}
					onClick={onSave}
					disabled={!selected || !isDirty || saving}
					variant="contained"
				>
					{saving ? "Saving..." : "Save"}
				</Button>
			</Stack>
		</Stack>
	);
}
