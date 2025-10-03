import { AuthGuard } from "../guards/auth-guard";

// Higher Order Component to wrap a component with AuthGuard
export const withAuthGuard = (Component) => {
	const Content = (props) => (
		<AuthGuard>
			<Component {...props} />
		</AuthGuard>
	);
	return Content;
};
