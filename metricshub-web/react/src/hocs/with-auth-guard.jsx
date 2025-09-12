import { AuthGuard } from "../guards/auth-guard";

// eslint-disable-next-line no-unused-vars
export const withAuthGuard = (Component) => (props) => (
	<AuthGuard>
		<Component {...props} />
	</AuthGuard>
);
