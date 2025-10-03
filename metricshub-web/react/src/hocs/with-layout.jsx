// Higher Order Component to wrap a component with a specified layout
export const withLayout = (Layout) => {
	return (Component) => {
		const Content = (props) => (
			<Layout>
				<Component {...props} />
			</Layout>
		);

		return Content;
	};
};
