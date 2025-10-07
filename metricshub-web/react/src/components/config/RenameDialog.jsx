// src/components/config/RenameDialog.jsx
import {
	Dialog,
	DialogTitle,
	DialogContent,
	DialogActions,
	Button,
	TextField,
} from "@mui/material";

export default function RenameDialog({ open, value, setValue, onCancel, onSubmit }) {
	return (
		<Dialog open={open} onClose={onCancel}>
			<DialogTitle>Rename file</DialogTitle>
			<DialogContent>
				<TextField
					autoFocus
					margin="dense"
					label="New name"
					fullWidth
					value={value}
					onChange={(e) => setValue(e.target.value)}
					placeholder="new-file.yaml"
				/>
			</DialogContent>
			<DialogActions>
				<Button onClick={onCancel}>Cancel</Button>
				<Button variant="contained" onClick={onSubmit}>
					Rename
				</Button>
			</DialogActions>
		</Dialog>
	);
}
