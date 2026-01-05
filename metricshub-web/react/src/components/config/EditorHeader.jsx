import { Stack, Typography, Button, Box } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import { useAppSelector } from "../../hooks/store";
import { isBackupFileName } from "../../utils/backup-names";
import FileTypeIcon from "./tree/icons/FileTypeIcons";

/**
 * Editor header component showing file name, save button, and status.
 * @param {{selected:string|null,saving:boolean,onSave:()=>void}} props The component props.
 * @returns {JSX.Element} The editor header component.
 */
export default function EditorHeader({ selected, saving, onSave }) {
	const dirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const isDirty = !!dirtyByName?.[selected];
	const filesByName = useAppSelector((s) => s.config.filesByName) ?? {};
	const fileValidation = selected ? filesByName[selected]?.validation : null;
	const hasErrors = !!(fileValidation && fileValidation.valid === false);
	const isBackup = !!(selected && isBackupFileName(selected));

	// Non-positional errors (line/column <= 0 or missing) are shown under the header
	const nonPosErrors = Array.isArray(fileValidation?.errors)
		? fileValidation.errors.filter((e) => {
			const ln = Number(e?.line);
			const col = Number(e?.column);
			return !Number.isFinite(ln) || !Number.isFinite(col) || ln <= 0 || col <= 0;
		})
		: [];

	return (
		<>
			<Stack direction="row" alignItems="center" justifyContent="space-between">
				{/* File name + unsaved indicator */}
				<Stack direction="row" alignItems="center" spacing={0}>
					{selected && <FileTypeIcon type={isBackup ? "backup" : "file"} />}
					<Typography
						variant="subtitle1"
						sx={{ fontWeight: isDirty ? 510 : 500, transition: "color 0.4s ease" }}
					>
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
								bgcolor: (t) => (hasErrors ? t.palette.error.main : t.palette.warning.main),
								ml: 0.25,
								transition: "background-color 0.4s ease",
							}}
						/>
					)}
				</Stack>
				{/* Right side buttons */}
				<Stack direction="row" spacing={1} alignItems="center">
					<Button
						size="small"
						startIcon={<SaveIcon />}
						onClick={onSave}
						disabled={!selected || isBackup || !isDirty || saving}
						variant="contained"
					>
						{saving ? "Saving..." : "Save"}
					</Button>
				</Stack>
			</Stack>

			{nonPosErrors.length > 0 && (
				<Box
					sx={{
						color: (t) => t.palette.error.main,
						typography: "body2",
						whiteSpace: "pre-wrap",
						maxHeight: 96,
						overflow: "auto",
						mt: 0.5,
						transition: "color 0.4s ease",
					}}
				>
					{nonPosErrors.join("\n")}
				</Box>
			)}
		</>
	);
}
