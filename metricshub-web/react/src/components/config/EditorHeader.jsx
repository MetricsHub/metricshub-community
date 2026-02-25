import { Stack, Typography, Button, Box, Chip } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import PushPinIcon from "@mui/icons-material/PushPin";
import DoneAllIcon from "@mui/icons-material/DoneAll";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import { useAppSelector } from "../../hooks/store";
import { isBackupFileName } from "../../utils/backup-names";
import { isVmFile, getFileType } from "../../utils/file-type-utils";
import FileTypeIcon from "./tree/icons/FileTypeIcons";

/**
 * Editor header component showing file name, save button, and status.
 * @param {{selected:string|null,saving:boolean,onSave:()=>void,onApply?:()=>void,onTest?:()=>void,testLoading?:boolean,isReadOnly?:boolean}} props The component props.
 * @returns {JSX.Element} The editor header component.
 */
export default function EditorHeader({
	selected,
	saving,
	onSave,
	onApply,
	onTest,
	testLoading = false,
	isReadOnly = false,
}) {
	const dirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const isDirty = !!dirtyByName?.[selected];
	const filesByName = useAppSelector((s) => s.config.filesByName) ?? {};
	const fileValidation = selected ? filesByName[selected]?.validation : null;
	const hasErrors = !!(fileValidation && fileValidation.valid === false);
	const isBackup = !!(selected && isBackupFileName(selected));
	const isVm = !!(selected && isVmFile(selected));
	const fileType = selected ? getFileType(selected) : "file";

	const isDraft = selected && selected.endsWith(".draft");
	const displayName = isDraft
		? selected.replace(/\.draft$/, "")
		: (selected ?? "Select a file to edit");

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
					{selected && <FileTypeIcon type={fileType} />}
					<Typography
						variant="subtitle1"
						sx={{ fontWeight: isDirty ? 510 : 500, transition: "color 0.4s ease" }}
					>
						{displayName}
					</Typography>

					{isVm && (
						<Chip
							label="Velocity"
							size="small"
							sx={{
								height: 24,
								ml: 1,
								"& .MuiChip-label": { px: 0.75, fontSize: "0.8rem" },
							}}
						/>
					)}

					{isDraft && (
						<Chip
							label="Draft"
							size="small"
							color="secondary"
							icon={<PushPinIcon style={{ fontSize: 14 }} />}
							sx={{
								height: 20,
								ml: 1,
								"& .MuiChip-label": { px: 0.5, fontSize: "0.7rem" },
								"& .MuiChip-icon": { ml: 0.5 },
							}}
						/>
					)}

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
					{isDraft && onApply && (
						<Button
							size="small"
							startIcon={<DoneAllIcon />}
							onClick={onApply}
							disabled={!selected || saving || isReadOnly}
							variant="contained"
							sx={{
								background: "linear-gradient(135deg, #167c4cff 0%, #45ce52 100%)",
								color: "#fff",
								"&:hover": {
									background: "linear-gradient(135deg, #115e3a 0%, #36a843 100%)",
								},
							}}
						>
							{saving ? "Applying..." : "Apply"}
						</Button>
					)}
					{isVm && onTest && (
						<Button
							size="small"
							variant="outlined"
							color="inherit"
							startIcon={<PlayArrowIcon />}
							onClick={onTest}
							disabled={!selected || saving || testLoading}
						>
							{testLoading ? "Testing..." : "Test"}
						</Button>
					)}
					<Button
						size="small"
						startIcon={<SaveIcon />}
						onClick={onSave}
						disabled={!selected || isBackup || !isDirty || saving || isReadOnly}
						variant="contained"
						sx={{
							"&:not(.Mui-disabled)": {
								background: "linear-gradient(135deg, #0A58CA 0%, #267DF4 100%)",
								color: "#fff",
							},
							"&:hover:not(.Mui-disabled)": {
								background: "linear-gradient(135deg, #084298 0%, #0A58CA 100%)",
							},
						}}
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
					{nonPosErrors.map((e) => e?.message || String(e)).join("\n")}
				</Box>
			)}
		</>
	);
}
