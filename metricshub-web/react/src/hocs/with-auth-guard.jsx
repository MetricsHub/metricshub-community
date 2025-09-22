import { AuthGuard } from "../guards/auth-guard";

// Higher Order Component to wrap a component with AuthGuard
// eslint-disable-next-line no-unused-vars
export const withAuthGuard = (Component) => (props) => (
	<AuthGuard>
		<Component {...props} />
	</AuthGuard>
);
