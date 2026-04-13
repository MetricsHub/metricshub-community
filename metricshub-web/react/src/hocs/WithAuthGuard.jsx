import { AuthGuard } from "../guards/AuthGuard";

// Higher Order Component to wrap a component with AuthGuard
export const withAuthGuard = (Component) => {
	const Content = (props) => (
		<AuthGuard>
			<Component {...props} />
		</AuthGuard>
	);
	return Content;
};
