// src/components/config/ConfirmDeleteDialog.jsx
import {
	Dialog,
	DialogTitle,
	DialogContent,
	DialogContentText,
	DialogActions,
	Button,
} from "@mui/material";

export default function ConfirmDeleteDialog({ open, fileName, onCancel, onConfirm }) {
	return (
		<Dialog open={open} onClose={onCancel}>
			<DialogTitle>Delete file</DialogTitle>
			<DialogContent>
				<DialogContentText>
					{`Are you sure you want to delete “${fileName ?? ""}”? This action cannot be undone.`}
				</DialogContentText>
			</DialogContent>
			<DialogActions>
				<Button onClick={onCancel}>Cancel</Button>
				<Button color="error" variant="contained" onClick={onConfirm}>
					Delete
				</Button>
			</DialogActions>
		</Dialog>
	);
}
