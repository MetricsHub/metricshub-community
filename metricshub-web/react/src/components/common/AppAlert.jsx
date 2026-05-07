import { Alert, IconButton } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

const AppAlert = ({ severity = "info", children, sx = {}, closable = false, onClose, action }) => (
	<Alert
		severity={severity}
		sx={{ mb: 1, ...sx }}
		action={
			action ||
			(closable && onClose ? (
				<IconButton aria-label="close" color="inherit" size="small" onClick={onClose}>
					<CloseIcon fontSize="inherit" />
				</IconButton>
			) : undefined)
		}
	>
		{children}
	</Alert>
);

export default AppAlert;
