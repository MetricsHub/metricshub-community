import { Alert } from "@mui/material";

const AppAlert = ({ severity = "info", children, sx = {} }) => (
	<Alert severity={severity} sx={{ mb: 1, ...sx }}>
		{children}
	</Alert>
);

export default AppAlert;
