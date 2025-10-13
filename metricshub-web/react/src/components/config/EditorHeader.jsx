// src/components/config/EditorHeader.jsx
import { Stack, Typography, Chip, Button } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import VerifiedIcon from "@mui/icons-material/Verified";

/**
 * Editor header component.
 * @param {{selected:string,saving:boolean,validation:{valid:boolean,error?:string},onValidate:()=>void,onSave:()=>void,canSave:boolean}} param0 The component props.
 * @returns {JSX.Element} The editor header component.
 */
export default function EditorHeader({
	selected,
	saving,
	validation,
	onValidate,
	onSave,
	canSave,
}) {
	return (
		<Stack direction="row" alignItems="center" justifyContent="space-between">
			<Typography variant="subtitle1">{selected ?? "Select a file to edit"}</Typography>
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
				<Button size="small" startIcon={<SaveIcon />} onClick={onSave} disabled={!canSave}>
					{saving ? "Saving..." : "Save"}
				</Button>
			</Stack>
		</Stack>
	);
}
